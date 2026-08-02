package io.github.ooboomberoo.precaststructure.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ooboomberoo.precaststructure.client.ClientRegistryAccess;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only Create soft-compat. Kinetic relays (shafts/cogs) bake empty quads unless
 * ModelData is marked virtual; brackets are a separate static mesh from BE NBT.
 * Machines like millstones/mixers keep their cogs in the block-entity renderer, so those
 * are drawn via a temporary BE on Create's SchematicLevel (avoids Flywheel skipping).
 */
public final class CreateCompatClient {
    private static final Object VIRTUAL_DATA;
    private static final Method RENDER_WITH_MODEL_DATA;
    @Nullable
    private static final Constructor<?> SCHEMATIC_LEVEL_CTOR;
    @Nullable
    private static final Method MARK_VIRTUAL;

    @Nullable
    private static Level cachedVirtualLevel;
    @Nullable
    private static Level cachedVirtualLevelSource;

    static {
        Object virtualData = null;
        Method renderWithModelData = null;
        Constructor<?> schematicLevelCtor = null;
        Method markVirtual = null;
        if (CreateCompat.isLoaded()) {
            try {
                Class<?> helper = Class.forName("net.createmod.ponder.render.VirtualRenderHelper");
                virtualData = helper.getField("VIRTUAL_DATA").get(null);
                for (Method method : BlockRenderDispatcher.class.getMethods()) {
                    if (!"renderSingleBlock".equals(method.getName()) || method.getParameterCount() != 7) {
                        continue;
                    }
                    Class<?>[] params = method.getParameterTypes();
                    if (params[0] == BlockState.class
                        && params[1] == PoseStack.class
                        && MultiBufferSource.class.isAssignableFrom(params[2])
                        && params[3] == int.class
                        && params[4] == int.class) {
                        renderWithModelData = method;
                        break;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                virtualData = null;
                renderWithModelData = null;
            }
            try {
                Class<?> schematicLevel = Class.forName("net.createmod.catnip.levelWrappers.SchematicLevel");
                schematicLevelCtor = schematicLevel.getConstructor(Level.class);
            } catch (ReflectiveOperationException ignored) {
                schematicLevelCtor = null;
            }
            try {
                Class<?> smartBe = Class.forName("com.simibubi.create.foundation.blockEntity.SmartBlockEntity");
                markVirtual = smartBe.getMethod("markVirtual");
            } catch (ReflectiveOperationException ignored) {
                markVirtual = null;
            }
        }
        VIRTUAL_DATA = virtualData;
        RENDER_WITH_MODEL_DATA = renderWithModelData;
        SCHEMATIC_LEVEL_CTOR = schematicLevelCtor;
        MARK_VIRTUAL = markVirtual;
    }

    private CreateCompatClient() {
    }

    public static void renderSingleBlock(
        BlockRenderDispatcher dispatcher,
        BlockState state,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay,
        @Nullable CompoundTag nbt
    ) {
        BakedModel model = dispatcher.getBlockModel(state);
        boolean bracketedKinetic = isBracketedKineticModel(model);
        // Virtual ModelData is only required for shaft/cog wrappers (empty quads otherwise).
        // Passing it to other Create machines can expose unsorted internal kinetic meshes.
        boolean renderedVirtual = false;
        if (bracketedKinetic && VIRTUAL_DATA != null && RENDER_WITH_MODEL_DATA != null) {
            try {
                RENDER_WITH_MODEL_DATA.invoke(
                    dispatcher,
                    state,
                    poseStack,
                    bufferSource,
                    packedLight,
                    packedOverlay,
                    VIRTUAL_DATA,
                    null
                );
                renderedVirtual = true;
            } catch (ReflectiveOperationException ignored) {
                renderedVirtual = false;
            }
        }
        if (!renderedVirtual) {
            dispatcher.renderSingleBlock(state, poseStack, bufferSource, packedLight, packedOverlay);
        }

        BlockState bracket = CreateCompat.readBracket(nbt, ClientRegistryAccess.getLookup());
        if (bracket != null) {
            dispatcher.renderSingleBlock(bracket, poseStack, bufferSource, packedLight, packedOverlay);
        }

        if (!bracketedKinetic) {
            renderKineticBlockEntity(dispatcher, state, poseStack, bufferSource, packedLight, packedOverlay, nbt);
        }
    }

    /**
     * Draws Create BER kinetic extras (millstone cog, mixer head/cog, fan propeller, …).
     */
    private static void renderKineticBlockEntity(
        BlockRenderDispatcher dispatcher,
        BlockState state,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay,
        @Nullable CompoundTag nbt
    ) {
        if (!CreateCompat.isLoaded() || !(state.getBlock() instanceof EntityBlock entityBlock)) {
            return;
        }

        BlockEntity blockEntity;
        try {
            blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, state);
        } catch (RuntimeException ignored) {
            return;
        }
        if (blockEntity == null) {
            return;
        }

        BlockEntityRenderer<BlockEntity> renderer = Minecraft.getInstance()
            .getBlockEntityRenderDispatcher()
            .getRenderer(blockEntity);
        if (renderer == null || !isCreateRenderer(renderer)) {
            return;
        }

        Level virtualLevel = virtualLevel();
        if (virtualLevel == null) {
            return;
        }

        if (nbt != null && !nbt.isEmpty()) {
            try {
                blockEntity.loadWithComponents(nbt.copy(), virtualLevel.registryAccess());
            } catch (RuntimeException ignored) {
                // Still try a default empty BE so static kinetic meshes appear.
            }
        }

        markVirtual(blockEntity);
        blockEntity.setLevel(virtualLevel);
        try {
            renderer.render(blockEntity, 0.0F, poseStack, bufferSource, packedLight, packedOverlay);
        } catch (RuntimeException ignored) {
            // Some BEs expect a fuller world; skip rather than abort the hologram pass.
        } finally {
            blockEntity.setLevel(null);
        }
    }

    private static boolean isBracketedKineticModel(BakedModel model) {
        for (Class<?> type = model.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().contains("BracketedKineticBlockModel")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCreateRenderer(BlockEntityRenderer<?> renderer) {
        return renderer.getClass().getName().startsWith("com.simibubi.create.");
    }

    private static void markVirtual(BlockEntity blockEntity) {
        if (MARK_VIRTUAL == null) {
            return;
        }
        try {
            MARK_VIRTUAL.invoke(blockEntity);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional Create hook.
        }
    }

    @Nullable
    private static Level virtualLevel() {
        Level clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null) {
            return null;
        }
        if (cachedVirtualLevel != null && cachedVirtualLevelSource == clientLevel) {
            return cachedVirtualLevel;
        }
        cachedVirtualLevelSource = clientLevel;
        if (SCHEMATIC_LEVEL_CTOR != null) {
            try {
                cachedVirtualLevel = (Level) SCHEMATIC_LEVEL_CTOR.newInstance(clientLevel);
                return cachedVirtualLevel;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                cachedVirtualLevel = null;
            }
        }
        // Flywheel may skip kinetic BERs on the real client level; still better than nothing.
        cachedVirtualLevel = clientLevel;
        return cachedVirtualLevel;
    }
}
