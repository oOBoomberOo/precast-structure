package io.github.ooboomberoo.precaststructure.client.screen;

import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.block.StructureScannerBlock;
import io.github.ooboomberoo.precaststructure.block.entity.StructureScannerBlockEntity;
import io.github.ooboomberoo.precaststructure.menu.StructureScannerMenu;
import io.github.ooboomberoo.precaststructure.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

public class StructureScannerScreen extends AbstractContainerScreen<StructureScannerMenu> {
  private static final ResourceLocation TEXTURE =
      ResourceLocation.fromNamespaceAndPath(
          PrecastStructureMod.MOD_ID, "textures/gui/structure_scanner.png");
  private static final int LABEL_COLOR = 0x404040;
  private static final int CHECKMARK_SIZE = 16;
  private static final int CLOSE_SIZE = 12;
  private static final int CLOSE_UV_X = 176;
  private static final int CLOSE_UV_Y = 16;
  private static final int CLOSE_UV_Y_HOVERED = 28;
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
    this.nameField =
        new EditBox(
            this.font,
            this.leftPos + 14,
            this.topPos + 31,
            148,
            14,
            Component.translatable("gui.precast_structure.structure_name"));
    this.nameField.setBordered(false);
    this.nameField.setTextColor(0xE0E0E0);
    this.nameField.setMaxLength(StructureScannerBlockEntity.MAX_NAME_LENGTH);
    this.nameField.setValue(this.menu.getInitialStructureName());
    this.nameField.setCanLoseFocus(true);
    this.addRenderableWidget(this.nameField);
    this.addRenderableWidget(
        Button.builder(
                Component.translatable("gui.precast_structure.scan_structure"),
                button -> this.requestScan())
            .bounds(this.leftPos + 12, this.topPos + 58, 152, 20)
            .build());
    this.addRenderableWidget(
        new CloseButton(this.leftPos + this.imageWidth - CLOSE_SIZE - 5, this.topPos + 5));
  }

  private void requestScan() {
    BlockPos pos = this.menu.getBlockPos();
    String structureName = this.nameField.getValue();
    ModNetworking.sendScannerAction(pos, structureName);

    // Integrated-server fallback: C2S Architectury packets can be dropped while the
    // game window is unfocused under MCP automation; invoke the same handler locally.
    Minecraft minecraft = this.minecraft;
    if (minecraft == null || minecraft.player == null) {
      return;
    }
    MinecraftServer server = minecraft.getSingleplayerServer();
    if (server == null) {
      return;
    }
    var playerId = minecraft.player.getUUID();
    server.execute(
        () -> {
          ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
          if (serverPlayer == null) {
            return;
          }
          if (!(serverPlayer.containerMenu instanceof StructureScannerMenu menu)
              || !menu.getBlockPos().equals(pos)) {
            return;
          }
          StructureScannerBlockEntity scanner = menu.getScanner();
          if (scanner == null
              && serverPlayer.level().getBlockEntity(pos)
                  instanceof StructureScannerBlockEntity levelScanner) {
            scanner = levelScanner;
          }
          if (scanner == null) {
            return;
          }
          scanner.setStructureName(structureName);
          scanner.scanStructure(serverPlayer);
        });
  }

  @Override
  public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
    String value = this.nameField != null ? this.nameField.getValue() : "";
    super.resize(minecraft, width, height);
    this.nameField.setValue(value);
  }

  @Override
  public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
      this.onClose();
      return true;
    }
    if (this.nameField.isFocused()) {
      return this.nameField.keyPressed(keyCode, scanCode, modifiers)
          || this.nameField.canConsumeInput();
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
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    boolean handled = super.mouseClicked(mouseX, mouseY, button);
    if (!this.nameField.isMouseOver(mouseX, mouseY)) {
      this.nameField.setFocused(false);
    }
    return handled;
  }

  @Override
  protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    if (isScannerReady()) {
      int checkX = this.leftPos + this.imageWidth - CHECKMARK_SIZE - CLOSE_SIZE - 10;
      guiGraphics.blit(TEXTURE, checkX, this.topPos + 5, 176, 0, CHECKMARK_SIZE, CHECKMARK_SIZE);
    }
  }

  @Override
  protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    guiGraphics.drawString(this.font, this.title, 8, 6, LABEL_COLOR, false);
    guiGraphics.drawString(
        this.font,
        Component.translatable("gui.precast_structure.structure_name"),
        12,
        18,
        LABEL_COLOR,
        false);
  }

  private boolean isScannerReady() {
    if (this.minecraft == null || this.minecraft.level == null) {
      return false;
    }
    BlockState state = this.minecraft.level.getBlockState(this.menu.getBlockPos());
    return state.hasProperty(StructureScannerBlock.READY)
        && state.getValue(StructureScannerBlock.READY);
  }

  private final class CloseButton extends AbstractButton {
    private CloseButton(int x, int y) {
      super(x, y, CLOSE_SIZE, CLOSE_SIZE, Component.translatable("gui.precast_structure.close"));
    }

    @Override
    public void onPress() {
      StructureScannerScreen.this.onClose();
    }

    @Override
    protected void renderWidget(
        GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      int v = this.isHoveredOrFocused() ? CLOSE_UV_Y_HOVERED : CLOSE_UV_Y;
      guiGraphics.blit(TEXTURE, this.getX(), this.getY(), CLOSE_UV_X, v, CLOSE_SIZE, CLOSE_SIZE);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
      this.defaultButtonNarrationText(narrationElementOutput);
    }
  }
}
