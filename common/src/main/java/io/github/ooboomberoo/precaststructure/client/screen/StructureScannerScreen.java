package io.github.ooboomberoo.precaststructure.client.screen;

import io.github.ooboomberoo.precaststructure.block.entity.StructureScannerBlockEntity;
import io.github.ooboomberoo.precaststructure.menu.StructureScannerMenu;
import io.github.ooboomberoo.precaststructure.network.ModNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class StructureScannerScreen extends AbstractContainerScreen<StructureScannerMenu> {
    private EditBox nameField;

    public StructureScannerScreen(StructureScannerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 96;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.nameField = new EditBox(this.font, this.leftPos + 12, this.topPos + 28, 152, 20, Component.translatable("gui.precaststructure.structure_name"));
        this.nameField.setMaxLength(StructureScannerBlockEntity.MAX_NAME_LENGTH);
        this.nameField.setValue(this.menu.getInitialStructureName());
        this.addRenderableWidget(this.nameField);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.precaststructure.scan_structure"), button -> ModNetworking.sendScannerAction(this.menu.getBlockPos(), this.nameField.getValue())).bounds(this.leftPos + 12, this.topPos + 58, 152, 20).build());
        this.setInitialFocus(this.nameField);
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        String value = this.nameField != null ? this.nameField.getValue() : "";
        super.resize(minecraft, width, height);
        this.nameField.setValue(value);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameField.keyPressed(keyCode, scanCode, modifiers) || this.nameField.canConsumeInput()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.nameField.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFF222A31);
        guiGraphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.imageWidth - 1, this.topPos + this.imageHeight - 1, 0xFF3B4652);
        guiGraphics.fill(this.leftPos + 8, this.topPos + 18, this.leftPos + this.imageWidth - 8, this.topPos + 50, 0xFF1F252C);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, 0xE0E0E0, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.precaststructure.structure_name"), 12, 18, 0xC8D0D8, false);
    }
}
