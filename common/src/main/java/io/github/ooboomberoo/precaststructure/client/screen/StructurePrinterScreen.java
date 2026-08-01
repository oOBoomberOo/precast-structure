package io.github.ooboomberoo.precaststructure.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.block.entity.StructurePrinterBlockEntity;
import io.github.ooboomberoo.precaststructure.menu.StructurePrinterMenu;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.structure.MaterialRequirement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class StructurePrinterScreen extends AbstractContainerScreen<StructurePrinterMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PrecastStructureMod.MOD_ID, "textures/gui/structure_printer.png");
    private static final int LABEL_COLOR = 0x404040;
    private static final int SLOT_UV_X = 176;
    private static final int SLOT_UV_Y = 32;
    private static final int SCROLLBAR_UV_X = 176;
    private static final int SCROLLBAR_UV_Y = 50;
    private static final int SCROLLBAR_HANDLE_HEIGHT = 15;
    private static final int GHOST_COUNT_COLOR = 0xA0C8C8C8;
    private static final float GHOST_ALPHA = 0.4F;

    private boolean scrolling;

    public StructurePrinterScreen(StructurePrinterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = StructurePrinterMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = StructurePrinterMenu.INVENTORY_LABEL_Y;
        this.titleLabelY = 6;
    }

    @Override
    protected void containerTick() {
        this.menu.refreshLayout();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.menu.refreshLayout();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

        for (int i = 0; i < StructurePrinterBlockEntity.MATERIAL_SLOT_COUNT; i++) {
            Slot slot = this.menu.slots.get(StructurePrinterBlockEntity.FIRST_MATERIAL_SLOT + i);
            if (!slot.isActive() || slot.x < -1000) {
                continue;
            }
            guiGraphics.blit(TEXTURE, this.leftPos + slot.x - 1, this.topPos + slot.y - 1, SLOT_UV_X, SLOT_UV_Y, 18, 18);
        }

        if (this.menu.canScroll()) {
            int trackX = this.leftPos + StructurePrinterMenu.SCROLLBAR_X;
            int trackY = this.topPos + StructurePrinterMenu.SCROLLBAR_Y;
            guiGraphics.fill(trackX, trackY, trackX + StructurePrinterMenu.SCROLLBAR_WIDTH, trackY + StructurePrinterMenu.SCROLLBAR_HEIGHT, 0xFF000000);
            int handleTravel = StructurePrinterMenu.SCROLLBAR_HEIGHT - SCROLLBAR_HANDLE_HEIGHT;
            int handleY = trackY + Mth.floor(this.menu.getScrollProgress() * handleTravel);
            guiGraphics.blit(TEXTURE, trackX, handleY, SCROLLBAR_UV_X, SCROLLBAR_UV_Y, StructurePrinterMenu.SCROLLBAR_WIDTH, SCROLLBAR_HANDLE_HEIGHT);
        }

        int progress = this.menu.getScaledProgress(StructurePrinterMenu.PROGRESS_WIDTH);
        guiGraphics.blit(
            TEXTURE,
            this.leftPos + StructurePrinterMenu.PROGRESS_X,
            this.topPos + StructurePrinterMenu.PROGRESS_Y,
            176,
            0,
            StructurePrinterMenu.PROGRESS_WIDTH,
            StructurePrinterMenu.PROGRESS_HEIGHT
        );
        if (progress > 0) {
            guiGraphics.blit(
                TEXTURE,
                this.leftPos + StructurePrinterMenu.PROGRESS_X,
                this.topPos + StructurePrinterMenu.PROGRESS_Y,
                176,
                16,
                progress,
                StructurePrinterMenu.PROGRESS_HEIGHT
            );
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, LABEL_COLOR, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        super.renderSlot(guiGraphics, slot);
        if (slot.hasItem() || !slot.isActive() || slot.x < -1000) {
            return;
        }

        if (slot.index == StructurePrinterBlockEntity.BLUEPRINT_SLOT) {
            renderGhostItem(guiGraphics, new ItemStack(ModItems.BLUEPRINT.get()), slot.x, slot.y, null);
            return;
        }

        if (slot.index == StructurePrinterBlockEntity.OUTPUT_SLOT) {
            renderGhostItem(guiGraphics, new ItemStack(ModItems.PRECAST_STRUCTURE.get()), slot.x, slot.y, null);
            return;
        }

        if (slot.index < StructurePrinterBlockEntity.FIRST_MATERIAL_SLOT || slot.index >= StructurePrinterBlockEntity.OUTPUT_SLOT) {
            return;
        }

        int materialIndex = slot.index - StructurePrinterBlockEntity.FIRST_MATERIAL_SLOT;
        MaterialRequirement requirement = this.menu.getMaterialRequirement(materialIndex);
        if (requirement == null) {
            return;
        }

        ItemStack ghost = new ItemStack(requirement.item(), Mth.clamp(requirement.amount(), 1, requirement.item().getDefaultMaxStackSize()));
        renderGhostItem(guiGraphics, ghost, slot.x, slot.y, formatGhostCount(requirement.amount()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.menu.canScroll() && isOverMaterialsArea(mouseX, mouseY)) {
            int delta = scrollY > 0 ? -1 : 1;
            this.menu.scroll(delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.menu.canScroll() && isOverScrollbar(mouseX, mouseY)) {
            this.scrolling = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.scrolling = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrolling && this.menu.canScroll()) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void updateScrollFromMouse(double mouseY) {
        int trackY = this.topPos + StructurePrinterMenu.SCROLLBAR_Y;
        int travel = StructurePrinterMenu.SCROLLBAR_HEIGHT - SCROLLBAR_HANDLE_HEIGHT;
        float progress = (float) ((mouseY - trackY - SCROLLBAR_HANDLE_HEIGHT / 2.0) / travel);
        int max = this.menu.getMaxScrollRow();
        this.menu.setScrollRow(Mth.clamp(Math.round(progress * max), 0, max));
    }

    private boolean isOverMaterialsArea(double mouseX, double mouseY) {
        int x = this.leftPos + StructurePrinterMenu.MATERIAL_SLOT_X - 1;
        int y = this.topPos + StructurePrinterMenu.MATERIAL_SLOT_Y - 1;
        int w = StructurePrinterMenu.MATERIAL_COLUMNS * StructurePrinterMenu.SLOT_SPACING + StructurePrinterMenu.SCROLLBAR_WIDTH + 4;
        int h = StructurePrinterMenu.VISIBLE_MATERIAL_ROWS * StructurePrinterMenu.SLOT_SPACING + 2;
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        int x = this.leftPos + StructurePrinterMenu.SCROLLBAR_X;
        int y = this.topPos + StructurePrinterMenu.SCROLLBAR_Y;
        return mouseX >= x && mouseX < x + StructurePrinterMenu.SCROLLBAR_WIDTH
            && mouseY >= y && mouseY < y + StructurePrinterMenu.SCROLLBAR_HEIGHT;
    }

    private void renderGhostItem(GuiGraphics guiGraphics, ItemStack ghost, int x, int y, String count) {
        guiGraphics.pose().pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, GHOST_ALPHA);
        guiGraphics.renderItem(ghost, x, y);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        guiGraphics.pose().popPose();

        if (count != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
            guiGraphics.drawString(this.font, count, x + 19 - this.font.width(count), y + 9, GHOST_COUNT_COLOR, true);
            guiGraphics.pose().popPose();
        }
    }

    private static String formatGhostCount(int amount) {
        return amount > 1 ? String.valueOf(amount) : null;
    }
}
