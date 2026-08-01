package io.github.ooboomberoo.precaststructure.block.entity;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import io.github.ooboomberoo.precaststructure.block.StructureScannerBlock;
import io.github.ooboomberoo.precaststructure.menu.StructureScannerMenu;
import io.github.ooboomberoo.precaststructure.registry.ModBlockEntityTypes;
import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.structure.BlueprintCapture;
import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import io.github.ooboomberoo.precaststructure.structure.StructureFrameDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StructureScannerBlockEntity extends BlockEntity implements ExtendedMenuProvider {
    public static final int MAX_NAME_LENGTH = 48;
    private static final int RECHECK_INTERVAL = 10;
    private String structureName = "";

    public StructureScannerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.STRUCTURE_SCANNER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StructureScannerBlockEntity scanner) {
        if (level.getGameTime() % RECHECK_INTERVAL == 0) {
            scanner.recheckReady();
        }
    }

    public void recheckReady() {
        if (level == null || level.isClientSide()) {
            return;
        }

        boolean ready = StructureFrameDetector.detect(level, worldPosition).successful();
        BlockState state = level.getBlockState(worldPosition);
        if (!state.is(ModBlocks.STRUCTURE_SCANNER.get())) {
            return;
        }
        if (state.getValue(StructureScannerBlock.READY) == ready) {
            return;
        }

        // UPDATE_ALL so clients reliably swap red/blue models when the frame breaks far away.
        level.setBlock(worldPosition, state.setValue(StructureScannerBlock.READY, ready), Block.UPDATE_ALL);
    }

    public String getStructureName() {
        return structureName;
    }

    public void setStructureName(String structureName) {
        String normalized = normalizeStructureName(structureName);
        if (!this.structureName.equals(normalized)) {
            this.structureName = normalized;
            setChanged();
        }
    }

    public void scanStructure(ServerPlayer player) {
        if (level == null) {
            return;
        }

        StructureFrameDetector.ScanResult result = StructureFrameDetector.detect(level, worldPosition);
        if (!result.successful()) {
            player.displayClientMessage(result.error(), true);
            recheckReady();
            return;
        }

        StructureBlueprint blueprint = BlueprintCapture.capture(level, result.frameOptional().orElseThrow());
        if (blueprint.blocks().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.precast_structure.empty_scan"), true);
            return;
        }

        if (!consumeEmptyBlueprint(player)) {
            player.displayClientMessage(Component.translatable("message.precast_structure.needs_empty_blueprint"), true);
            return;
        }

        ItemStack blueprintStack = new ItemStack(ModItems.BLUEPRINT.get());
        BlueprintItemData.write(blueprintStack, blueprint, structureName.isBlank() ? null : Component.literal(structureName));
        if (!player.addItem(blueprintStack)) {
            player.drop(blueprintStack, false);
        }
        player.displayClientMessage(Component.translatable("message.precast_structure.scan_complete", blueprint.blocks().size()), true);
    }

    private static boolean consumeEmptyBlueprint(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ModItems.EMPTY_BLUEPRINT.get())) {
                continue;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    inventory.setItem(i, ItemStack.EMPTY);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.precast_structure.structure_scanner");
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeUtf(structureName, MAX_NAME_LENGTH);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, net.minecraft.world.entity.player.Player player) {
        return new StructureScannerMenu(containerId, inventory, worldPosition, structureName);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!structureName.isBlank()) {
            tag.putString("StructureName", structureName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        structureName = normalizeStructureName(tag.getString("StructureName"));
    }

    private static String normalizeStructureName(String structureName) {
        String trimmed = structureName == null ? "" : structureName.trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }
}
