package io.github.ooboomberoo.precaststructure.client.screen;

import io.github.ooboomberoo.precaststructure.menu.StructurePrinterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class StructurePrinterScreen extends AbstractContainerScreen<StructurePrinterMenu> {
    public StructurePrinterScreen(StructurePrinterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFF241F2C);
        guiGraphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.imageWidth - 1, this.topPos + this.imageHeight - 1, 0xFF433650);
        guiGraphics.fill(this.leftPos + 7, this.topPos + 17, this.leftPos + this.imageWidth - 7, this.topPos + 75, 0xFF1E1824);
        guiGraphics.fill(this.leftPos + 44, this.topPos + 30, this.leftPos + 52, this.topPos + 32, 0xFFE0903C);
        guiGraphics.fill(this.leftPos + 120, this.topPos + 38, this.leftPos + 128, this.topPos + 40, 0xFFE0903C);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, 0xE0E0E0, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.precaststructure.printer_blueprint"), 18, 18, 0xC8D0D8, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.precaststructure.printer_materials"), 62, 18, 0xC8D0D8, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.precaststructure.printer_output"), 124, 18, 0xC8D0D8, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.precaststructure.printer_auto"), 8, 72, 0xAEB8C2, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0xE0E0E0, false);
    }
}
