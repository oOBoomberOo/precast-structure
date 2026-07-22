package io.github.ooboomberoo.precaststructure.block.entity;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import io.github.ooboomberoo.precaststructure.menu.StructureScannerMenu;
import io.github.ooboomberoo.precaststructure.registry.ModBlockEntityTypes;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StructureScannerBlockEntity extends BlockEntity implements ExtendedMenuProvider {
    private static final int MAX_NAME_LENGTH = 48;
    private String structureName = "";

    public StructureScannerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.STRUCTURE_SCANNER.get(), pos, blockState);
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
            return;
        }

        StructureBlueprint blueprint = BlueprintCapture.capture(level, result.frameOptional().orElseThrow());
        if (blueprint.blocks().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.precaststructure.empty_scan"), true);
            return;
        }

        ItemStack blueprintStack = new ItemStack(ModItems.BLUEPRINT.get());
        BlueprintItemData.write(blueprintStack, blueprint, structureName.isBlank() ? null : Component.literal(structureName));
        if (!player.addItem(blueprintStack)) {
            player.drop(blueprintStack, false);
        }
        player.displayClientMessage(Component.translatable("message.precaststructure.scan_complete", blueprint.blocks().size()), true);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.precaststructure.structure_scanner");
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
