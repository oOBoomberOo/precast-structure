package io.github.ooboomberoo.precaststructure.compat;

import io.github.ooboomberoo.precaststructure.structure.StructurePlacement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Soft Create compatibility: brackets in BE NBT, virtual kinetic ModelData,
 * kinetic BER extras (see CreateCompatClient), and Schematicannon-style material
 * costs (encased shaft/cog → root shaft/cog).
 */
public final class CreateCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateCompat.class);
    private static final String BRACKET_KEY = "Bracket";

    private static volatile boolean initialized;
    private static boolean createPresent;
    private static boolean requirementApiReady;

    @Nullable
    private static Method nbtProcess;
    @Nullable
    private static Method itemRequirementOf;
    @Nullable
    private static Method getRequiredItems;
    @Nullable
    private static Method isEmpty;
    @Nullable
    private static Method isInvalid;
    @Nullable
    private static Field stackField;
    @Nullable
    private static Field usageField;
    @Nullable
    private static Object consumeUsage;

    private CreateCompat() {
    }

    public static boolean isLoaded() {
        return ensureInitialized();
    }

    @Nullable
    public static CompoundTag captureBlockEntityNbt(Level level, BlockEntity blockEntity) {
        CompoundTag nbt = blockEntity.saveWithFullMetadata(level.registryAccess());
        nbt.remove("x");
        nbt.remove("y");
        nbt.remove("z");
        if (ensureInitialized() && nbtProcess != null) {
            try {
                Object processed = nbtProcess.invoke(null, blockEntity.getBlockState(), blockEntity, nbt, false);
                if (processed instanceof CompoundTag compound) {
                    nbt = compound;
                }
            } catch (ReflectiveOperationException ignored) {
                // Keep unsanitized NBT rather than dropping the block entity.
            }
        }
        return nbt.isEmpty() ? null : nbt;
    }

    /**
     * Rotates Create-specific nested block states (brackets) stored in block-entity NBT.
     */
    @Nullable
    public static CompoundTag transformNbt(@Nullable CompoundTag nbt, Rotation rotation, HolderLookup.Provider registries) {
        if (nbt == null || nbt.isEmpty() || rotation == Rotation.NONE) {
            return nbt;
        }
        if (!nbt.contains(BRACKET_KEY, Tag.TAG_COMPOUND)) {
            return nbt;
        }
        CompoundTag copy = nbt.copy();
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        BlockState bracket = NbtUtils.readBlockState(blocks, copy.getCompound(BRACKET_KEY));
        copy.put(BRACKET_KEY, NbtUtils.writeBlockState(StructurePlacement.rotateState(bracket, rotation)));
        return copy;
    }

    public static void applyBlockEntityNbt(
        Level level,
        BlockEntity blockEntity,
        BlockState placedState,
        @Nullable CompoundTag nbt
    ) {
        if (nbt == null || nbt.isEmpty()) {
            return;
        }
        CompoundTag tag = nbt.copy();
        if (ensureInitialized() && nbtProcess != null) {
            try {
                Object processed = nbtProcess.invoke(null, placedState, blockEntity, tag, false);
                if (processed instanceof CompoundTag compound) {
                    tag = compound;
                } else if (processed == null) {
                    return;
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall through to raw load.
            }
        }
        tag.putInt("x", blockEntity.getBlockPos().getX());
        tag.putInt("y", blockEntity.getBlockPos().getY());
        tag.putInt("z", blockEntity.getBlockPos().getZ());
        blockEntity.loadWithComponents(tag, level.registryAccess());
        blockEntity.setChanged();
        if (!level.isClientSide()) {
            placedState.updateNeighbourShapes(level, blockEntity.getBlockPos(), 3);
            level.sendBlockUpdated(blockEntity.getBlockPos(), placedState, placedState, 3);
        }
    }

    @Nullable
    public static BlockState readBracket(@Nullable CompoundTag nbt, HolderLookup.Provider registries) {
        if (nbt == null || !nbt.contains(BRACKET_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        BlockState bracket = NbtUtils.readBlockState(blocks, nbt.getCompound(BRACKET_KEY));
        return bracket.isAir() ? null : bracket;
    }

    @Nullable
    public static Item bracketItem(@Nullable CompoundTag nbt, HolderLookup.Provider registries) {
        BlockState bracket = readBracket(nbt, registries);
        if (bracket == null) {
            return null;
        }
        Item item = bracket.getBlock().asItem();
        return item == Items.AIR ? null : item;
    }

    /**
     * Uses Create's schematic material API when present (encased shaft → shaft, etc.).
     *
     * @return true if Create supplied requirements (including "none"); false for the default asItem path
     */
    public static boolean tryMergeRequirements(BlockState state, Map<Item, Integer> requirements) {
        if (!ensureInitialized() || !requirementApiReady) {
            return false;
        }
        try {
            Object requirement = itemRequirementOf.invoke(null, state, (BlockEntity) null);
            if (Boolean.TRUE.equals(isInvalid.invoke(requirement))) {
                return false;
            }
            if (Boolean.TRUE.equals(isEmpty.invoke(requirement))) {
                return true;
            }
            @SuppressWarnings("unchecked")
            List<Object> stacks = (List<Object>) getRequiredItems.invoke(requirement);
            for (Object stackRequirement : stacks) {
                if (consumeUsage != null && usageField.get(stackRequirement) != consumeUsage) {
                    continue;
                }
                ItemStack stack = (ItemStack) stackField.get(stackRequirement);
                Item item = stack.getItem();
                if (item == Items.AIR || stack.isEmpty()) {
                    continue;
                }
                requirements.merge(item, Math.max(1, stack.getCount()), Integer::sum);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("Create material lookup failed for {}", state.getBlock(), e);
            return false;
        }
    }

    private static boolean ensureInitialized() {
        if (!initialized) {
            synchronized (CreateCompat.class) {
                if (!initialized) {
                    bindCreateApi();
                    initialized = true;
                }
            }
        }
        return createPresent;
    }

    private static void bindCreateApi() {
        try {
            createPresent = dev.architectury.platform.Platform.isModLoaded("create");
        } catch (Throwable t) {
            // Unit tests / early bootstrap may not have Architectury platform services.
            createPresent = false;
            return;
        }
        if (!createPresent) {
            return;
        }

        try {
            Class<?> processors = Class.forName("net.createmod.catnip.nbt.NBTProcessors");
            nbtProcess = processors.getMethod(
                "process",
                BlockState.class,
                BlockEntity.class,
                CompoundTag.class,
                boolean.class
            );
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Create NBTProcessors unavailable", e);
        }

        try {
            Class<?> itemRequirement = Class.forName("com.simibubi.create.content.schematics.requirement.ItemRequirement");
            Class<?> stackRequirement = Class.forName("com.simibubi.create.content.schematics.requirement.ItemRequirement$StackRequirement");
            Class<?> itemUseType = Class.forName("com.simibubi.create.content.schematics.requirement.ItemRequirement$ItemUseType");

            itemRequirementOf = itemRequirement.getMethod("of", BlockState.class, BlockEntity.class);
            getRequiredItems = itemRequirement.getMethod("getRequiredItems");
            isEmpty = itemRequirement.getMethod("isEmpty");
            isInvalid = itemRequirement.getMethod("isInvalid");
            stackField = stackRequirement.getField("stack");
            usageField = stackRequirement.getField("usage");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object consume = Enum.valueOf((Class) itemUseType, "CONSUME");
            consumeUsage = consume;
            requirementApiReady = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Create schematic material API could not be bound; encased blocks may cost their encased items", e);
        }
    }
}
