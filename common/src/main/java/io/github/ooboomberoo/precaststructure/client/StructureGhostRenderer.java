package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ooboomberoo.precaststructure.client.HologramRenderSystem.Part;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import io.github.ooboomberoo.precaststructure.structure.StructureBlockInfo;
import io.github.ooboomberoo.precaststructure.structure.StructureDeploymentManager;
import io.github.ooboomberoo.precaststructure.structure.StructurePlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Placement preview for held precast structures via {@link HologramRenderSystem}.
 * Placeable previews stay cyan-hologram; blocked previews tint red (no bounding box).
 */
public final class StructureGhostRenderer {
    private static final float PLACEABLE_R = 1.0F;
    private static final float PLACEABLE_G = 1.0F;
    private static final float PLACEABLE_B = 1.0F;
    private static final float BLOCKED_R = 1.0F;
    private static final float BLOCKED_G = 0.18F;
    private static final float BLOCKED_B = 0.15F;

    private StructureGhostRenderer() {
    }

    public static void render(PoseStack poseStack, Vec3 cameraPosition, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null || !(minecraft.hitResult instanceof BlockHitResult hitResult) || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        ItemStack stack = getPreviewStack(player);
        if (stack.isEmpty()) {
            return;
        }

        Optional<StructureBlueprint> optional = BlueprintItemData.read(stack, level.registryAccess());
        if (optional.isEmpty()) {
            return;
        }

        StructureBlueprint blueprint = optional.get();
        // UseOnContext.getHorizontalDirection is remapped into Sable plot-local yaw on Simulated
        // ships (see UseOnContextMixin). player.getDirection() stays world-yaw and desyncs the ghost.
        UseOnContext context = new UseOnContext(player, player.getUsedItemHand(), hitResult);
        Direction facing = context.getHorizontalDirection();
        BlockPos origin = StructurePlacement.resolveOrigin(context);

        List<Part> parts = new ArrayList<>(blueprint.blocks().size());
        for (StructureBlockInfo block : blueprint.blocks()) {
            BlockPos blockPos = origin.offset(StructurePlacement.transformOffset(block.offset(), blueprint, facing));
            BlockState state = StructurePlacement.transformState(block.state(), facing);
            parts.add(Part.of(
                blockPos,
                state,
                io.github.ooboomberoo.precaststructure.compat.CreateCompat.transformNbt(
                    block.nbt(),
                    StructurePlacement.rotationFor(facing),
                    level.registryAccess()
                )
            ));
        }

        boolean overlapsDeploy = StructureDeploymentManager.overlapsActiveDeploy(
            level,
            StructurePlacement.placementBounds(origin, blueprint, facing)
        );
        boolean placeable = !overlapsDeploy
            && StructurePlacement.firstBlockedPosition(level, origin, blueprint, facing).isEmpty();

        // Anchor at placement origin so Sable sub-level poses apply on Simulated ships.
        if (placeable) {
            HologramRenderSystem.render(
                poseStack, cameraPosition, partialTick, origin, parts, PLACEABLE_R, PLACEABLE_G, PLACEABLE_B
            );
        } else {
            HologramRenderSystem.render(
                poseStack, cameraPosition, partialTick, origin, parts, BLOCKED_R, BLOCKED_G, BLOCKED_B
            );
        }
    }

    private static ItemStack getPreviewStack(Player player) {
        if (player.getMainHandItem().is(ModItems.PRECAST_STRUCTURE.get())) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(ModItems.PRECAST_STRUCTURE.get())) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }
}
