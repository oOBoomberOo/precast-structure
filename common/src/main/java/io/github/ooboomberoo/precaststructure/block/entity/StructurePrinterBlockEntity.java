package io.github.ooboomberoo.precaststructure.block.entity;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import io.github.ooboomberoo.precaststructure.block.StructurePrinterBlock;
import io.github.ooboomberoo.precaststructure.config.ModConfig;
import io.github.ooboomberoo.precaststructure.menu.StructurePrinterMenu;
import io.github.ooboomberoo.precaststructure.registry.ModBlockEntityTypes;
import io.github.ooboomberoo.precaststructure.registry.ModGameRules;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.registry.ModSounds;
import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import io.github.ooboomberoo.precaststructure.structure.MaterialRequirement;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class StructurePrinterBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, ExtendedMenuProvider {
    public static final int BLUEPRINT_SLOT = 0;
    public static final int FIRST_MATERIAL_SLOT = 1;
    public static final int MATERIAL_SLOT_COUNT = 28;
    public static final int OUTPUT_SLOT = FIRST_MATERIAL_SLOT + MATERIAL_SLOT_COUNT;
    public static final int SLOT_COUNT = OUTPUT_SLOT + 1;
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX_PROGRESS = 1;
    public static final int DATA_COUNT = 2;
    private static final int[] INPUT_SLOTS = IntStream.range(BLUEPRINT_SLOT, OUTPUT_SLOT).toArray();
    private static final int[] OUTPUT_SLOTS = {OUTPUT_SLOT};

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int printProgress;
    private int maxPrintProgress = ModConfig.get().printer.defaultDelayTicks;
    private final ContainerData progressData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> printProgress;
                case DATA_MAX_PROGRESS -> maxPrintProgress;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_PROGRESS -> printProgress = value;
                case DATA_MAX_PROGRESS -> maxPrintProgress = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public StructurePrinterBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.STRUCTURE_PRINTER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StructurePrinterBlockEntity blockEntity) {
        blockEntity.tryPrint(state);
    }

    public List<MaterialRequirement> getMaterialRequirements() {
        ItemStack blueprintStack = items.get(BLUEPRINT_SLOT);
        if (!blueprintStack.is(ModItems.BLUEPRINT.get()) || level == null) {
            return List.of();
        }
        return BlueprintItemData.read(blueprintStack, level.registryAccess())
            .map(blueprint -> blueprint.materialSlotRequirements(level.registryAccess()))
            .orElse(List.of());
    }

    @Nullable
    public MaterialRequirement getMaterialRequirement(int materialIndex) {
        List<MaterialRequirement> requirements = getMaterialRequirements();
        if (materialIndex < 0 || materialIndex >= requirements.size() || materialIndex >= MATERIAL_SLOT_COUNT) {
            return null;
        }
        return requirements.get(materialIndex);
    }

    private void tryPrint(BlockState state) {
        if (!state.getValue(StructurePrinterBlock.ENABLED)) {
            resetProgress();
            return;
        }

        ItemStack blueprintStack = items.get(BLUEPRINT_SLOT);
        if (!blueprintStack.is(ModItems.BLUEPRINT.get())) {
            resetProgress();
            return;
        }

        Optional<StructureBlueprint> optional = BlueprintItemData.read(blueprintStack, level.registryAccess());
        if (optional.isEmpty()) {
            resetProgress();
            return;
        }

        StructureBlueprint blueprint = optional.get();
        List<MaterialRequirement> requirements = blueprint.materialSlotRequirements(level.registryAccess());
        if (requirements.size() > MATERIAL_SLOT_COUNT || !hasMaterials(requirements)) {
            resetProgress();
            return;
        }

        ItemStack structureStack = new ItemStack(ModItems.PRECAST_STRUCTURE.get());
        BlueprintItemData.write(structureStack, blueprint, blueprintStack.get(DataComponents.CUSTOM_NAME));
        ItemStack outputStack = items.get(OUTPUT_SLOT);
        if (!canAcceptPrintedStructure(outputStack, structureStack)) {
            resetProgress();
            return;
        }

        if (printProgress == 0) {
            maxPrintProgress = getConfiguredPrintDelay();
        }

        if (printProgress < maxPrintProgress) {
            printProgress++;
            playWorkingSound();
            setChanged();
            return;
        }

        consumeMaterials(requirements);
        if (outputStack.isEmpty()) {
            items.set(OUTPUT_SLOT, structureStack);
        } else {
            outputStack.grow(1);
        }
        printProgress = 0;
        setChanged();
    }

    private void playWorkingSound() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (level.getGameTime() % ModConfig.get().printer.soundIntervalTicks == 0) {
            level.playSound(null, worldPosition, ModSounds.PRINTING.get(), SoundSource.BLOCKS, 0.65F, 1.2F);
        }
    }

    private static boolean canAcceptPrintedStructure(ItemStack outputStack, ItemStack structureStack) {
        if (outputStack.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(outputStack, structureStack) && outputStack.getCount() < outputStack.getMaxStackSize();
    }

    private int getConfiguredPrintDelay() {
        return Math.max(1, level != null ? level.getGameRules().getInt(ModGameRules.STRUCTURE_PRINTER_DELAY) : ModConfig.get().printer.defaultDelayTicks);
    }

    private void resetProgress() {
        if (printProgress != 0) {
            printProgress = 0;
            setChanged();
        }
    }

    public ContainerData getProgressData() {
        return progressData;
    }

    private boolean hasMaterials(List<MaterialRequirement> requirements) {
        for (int i = 0; i < requirements.size(); i++) {
            MaterialRequirement requirement = requirements.get(i);
            ItemStack stack = items.get(FIRST_MATERIAL_SLOT + i);
            if (!stack.is(requirement.item()) || stack.getCount() < requirement.amount()) {
                return false;
            }
        }
        return true;
    }

    private void consumeMaterials(List<MaterialRequirement> requirements) {
        for (int i = 0; i < requirements.size(); i++) {
            MaterialRequirement requirement = requirements.get(i);
            int slot = FIRST_MATERIAL_SLOT + i;
            ItemStack stack = items.get(slot);
            stack.shrink(requirement.amount());
            if (stack.isEmpty()) {
                items.set(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return direction == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) {
        if (slot == BLUEPRINT_SLOT) {
            return stack.is(ModItems.BLUEPRINT.get()) && BlueprintItemData.hasStructure(stack);
        }
        if (slot < FIRST_MATERIAL_SLOT || slot >= OUTPUT_SLOT) {
            return false;
        }
        MaterialRequirement requirement = getMaterialRequirement(slot - FIRST_MATERIAL_SLOT);
        return requirement != null && stack.is(requirement.item());
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return slot == OUTPUT_SLOT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return canPlaceItemThroughFace(slot, stack, Direction.UP);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.precast_structure.structure_printer");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new StructurePrinterMenu(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("PrintProgress", printProgress);
        tag.putInt("MaxPrintProgress", maxPrintProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        printProgress = tag.getInt("PrintProgress");
        int savedMaxPrintProgress = tag.getInt("MaxPrintProgress");
        maxPrintProgress = savedMaxPrintProgress > 0 ? savedMaxPrintProgress : getConfiguredPrintDelay();
    }
}
