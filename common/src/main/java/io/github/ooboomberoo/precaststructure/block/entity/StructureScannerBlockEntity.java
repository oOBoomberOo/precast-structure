package io.github.ooboomberoo.precaststructure.block.entity;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import io.github.ooboomberoo.precaststructure.block.StructureScannerBlock;
import io.github.ooboomberoo.precaststructure.config.ModConfig;
import io.github.ooboomberoo.precaststructure.menu.StructureScannerMenu;
import io.github.ooboomberoo.precaststructure.registry.ModBlockEntityTypes;
import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import io.github.ooboomberoo.precaststructure.registry.ModBlockTags;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.registry.ModSounds;
import io.github.ooboomberoo.precaststructure.structure.BlueprintCapture;
import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import io.github.ooboomberoo.precaststructure.structure.StructureFrame;
import io.github.ooboomberoo.precaststructure.structure.StructureFrameDetector;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class StructureScannerBlockEntity extends BlockEntity implements ExtendedMenuProvider {
    public static final int MAX_NAME_LENGTH = 48;
    private static final int RECHECK_INTERVAL = 10;
    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

    /** Client-tracked scanners that are currently animating ghost geometry. */
    private static final Map<BlockPos, StructureScannerBlockEntity> CLIENT_ACTIVE_SCANS = new ConcurrentHashMap<>();

    private String structureName = "";

    private boolean scanning;
    private int scanProgress;
    private int scanDuration;
    private long scanStartGameTime;
    private BlockPos scanOrigin = BlockPos.ZERO;
    private BlockPos scanSize = BlockPos.ZERO;
    @Nullable
    private UUID scanPlayerId;
    @Nullable
    private StructureBlueprint ghostBlueprint;
    @Nullable
    private StructureFrame pendingFrame;
    private boolean needsScanClockAlign;

    public StructureScannerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.STRUCTURE_SCANNER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StructureScannerBlockEntity scanner) {
        if (scanner.scanning) {
            scanner.tickScanning();
            return;
        }
        if (level.getGameTime() % RECHECK_INTERVAL == 0) {
            scanner.recheckReady();
        }
    }

    public static Iterable<StructureScannerBlockEntity> clientActiveScans() {
        return CLIENT_ACTIVE_SCANS.values();
    }

    public void recheckReady() {
        if (level == null || level.isClientSide() || scanning) {
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

    public boolean isBusy() {
        return scanning;
    }

    public boolean isScanning() {
        return scanning;
    }

    @Nullable
    public StructureBlueprint getGhostBlueprint() {
        return ghostBlueprint;
    }

    public BlockPos getScanOrigin() {
        return scanOrigin;
    }

    public BlockPos getScanSize() {
        return scanSize;
    }

    public long getScanStartGameTime() {
        return scanStartGameTime;
    }

    public int getScanDuration() {
        return scanDuration;
    }

    /** World Y of the scan plane; progresses from the top of the volume down to the floor. */
    public float getScanLineY(float partialTick) {
        if (!scanning || scanDuration <= 0 || scanSize.getY() <= 0) {
            return scanOrigin.getY() + scanSize.getY();
        }
        float top = scanOrigin.getY() + scanSize.getY();
        float bottom = scanOrigin.getY();
        return Mth.lerp(getScanProgress(partialTick), top, bottom);
    }

    public float getScanProgress(float partialTick) {
        if (!scanning || scanDuration <= 0) {
            return 0.0F;
        }
        if (level == null) {
            return Mth.clamp(scanProgress / (float) scanDuration, 0.0F, 1.0F);
        }
        float elapsed = (float) (level.getGameTime() - scanStartGameTime) + partialTick;
        return Mth.clamp(elapsed / scanDuration, 0.0F, 1.0F);
    }

    public void scanStructure(ServerPlayer player) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (scanning) {
            player.displayClientMessage(Component.translatable("message.precast_structure.scan_in_progress"), true);
            return;
        }

        StructureFrameDetector.ScanResult result = StructureFrameDetector.detect(level, worldPosition);
        if (!result.successful()) {
            player.displayClientMessage(result.error(), true);
            recheckReady();
            return;
        }

        StructureFrame frame = result.frameOptional().orElseThrow();
        Direction scannerFacing = getBlockState().getValue(StructureScannerBlock.FACING);
        StructureBlueprint blueprint = BlueprintCapture.capture(level, frame, scannerFacing);
        if (blueprint.blocks().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.precast_structure.empty_scan"), true);
            return;
        }

        if (!hasEmptyBlueprint(player)) {
            player.displayClientMessage(Component.translatable("message.precast_structure.needs_empty_blueprint"), true);
            return;
        }
        if (!consumeEmptyBlueprint(player)) {
            player.displayClientMessage(Component.translatable("message.precast_structure.needs_empty_blueprint"), true);
            return;
        }

        // Digitize immediately: real blocks become client-rendered ghosts for the scan pass.
        clearInterior(level, frame);

        ghostBlueprint = blueprint;
        pendingFrame = frame;
        scanPlayerId = player.getUUID();
        scanOrigin = frame.interiorOrigin();
        scanSize = frame.size();
        scanDuration = Math.max(ModConfig.get().scanning.minTicks, scanSize.getY() * ModConfig.get().scanning.ticksPerHeight);
        scanProgress = 0;
        scanStartGameTime = level.getGameTime();
        scanning = true;
        needsScanClockAlign = false;
        setChanged();
        syncToClient();

        player.closeContainer();
        player.displayClientMessage(Component.translatable("message.precast_structure.scan_started"), true);
    }

    private void tickScanning() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (needsScanClockAlign) {
            scanStartGameTime = level.getGameTime() - scanProgress;
            needsScanClockAlign = false;
            syncToClient();
        }

        scanProgress++;
        if (level.getGameTime() % ModConfig.get().scanning.soundIntervalTicks == 0) {
            level.playSound(null, worldPosition, ModSounds.SCANNING.get(), SoundSource.BLOCKS, 0.7F, 1.35F);
        }

        if (scanProgress >= scanDuration) {
            finishScan();
            return;
        }

        if (scanProgress % 10 == 0) {
            syncToClient();
        }
        setChanged();
    }

    private void finishScan() {
        if (!(level instanceof ServerLevel serverLevel)) {
            resetScanState();
            syncToClient();
            return;
        }

        StructureBlueprint blueprint = ghostBlueprint;
        UUID playerId = scanPlayerId;
        resetScanState();
        syncToClient();

        if (blueprint == null) {
            recheckReady();
            return;
        }

        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
        if (player == null) {
            Block.popResource(serverLevel, worldPosition.above(), createBlueprintStack(blueprint));
            playCompleteSound();
            recheckReady();
            return;
        }

        ItemStack blueprintStack = createBlueprintStack(blueprint);
        if (!player.addItem(blueprintStack)) {
            player.drop(blueprintStack, false);
        }
        playCompleteSound();
        player.displayClientMessage(Component.translatable("message.precast_structure.scan_complete", blueprint.blocks().size()), true);
        recheckReady();
    }

    private ItemStack createBlueprintStack(StructureBlueprint blueprint) {
        ItemStack blueprintStack = new ItemStack(ModItems.BLUEPRINT.get());
        BlueprintItemData.write(blueprintStack, blueprint, structureName.isBlank() ? null : Component.literal(structureName));
        return blueprintStack;
    }

    private void playCompleteSound() {
        if (level != null) {
            level.playSound(null, worldPosition, ModSounds.SCAN_COMPLETE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private static void clearInterior(Level level, StructureFrame frame) {
        BlockPos origin = frame.interiorOrigin();
        BlockPos size = frame.size();
        for (int y = size.getY() - 1; y >= 0; y--) {
            for (int x = 0; x < size.getX(); x++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!ModBlockTags.isBlueprintExcluded(state)) {
                        // Omit UPDATE_KNOWN_SHAPE so perimeter fences/gates update connections.
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), CLEAR_FLAGS);
                    }
                }
            }
        }

        AABB volume = new AABB(
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            origin.getX() + size.getX(),
            origin.getY() + size.getY(),
            origin.getZ() + size.getZ()
        ).inflate(0.25);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, volume)) {
            item.discard();
        }
    }

    private void resetScanState() {
        scanning = false;
        scanProgress = 0;
        scanDuration = 0;
        scanStartGameTime = 0L;
        scanOrigin = BlockPos.ZERO;
        scanSize = BlockPos.ZERO;
        scanPlayerId = null;
        ghostBlueprint = null;
        pendingFrame = null;
        needsScanClockAlign = false;
        setChanged();
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private static boolean hasEmptyBlueprint(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.EMPTY_BLUEPRINT.get())) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumeEmptyBlueprint(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ModItems.EMPTY_BLUEPRINT.get())) {
                continue;
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
            return true;
        }
        return false;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_ACTIVE_SCANS.remove(worldPosition);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        updateClientActiveScan();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        updateClientActiveScan();
    }

    private void updateClientActiveScan() {
        if (level != null && level.isClientSide()) {
            if (scanning && ghostBlueprint != null) {
                CLIENT_ACTIVE_SCANS.put(worldPosition.immutable(), this);
            } else {
                CLIENT_ACTIVE_SCANS.remove(worldPosition);
            }
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
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
        tag.putBoolean("Scanning", scanning);
        if (scanning) {
            tag.putInt("ScanProgress", scanProgress);
            tag.putInt("ScanDuration", scanDuration);
            tag.putLong("ScanStart", scanStartGameTime);
            tag.putInt("ScanOX", scanOrigin.getX());
            tag.putInt("ScanOY", scanOrigin.getY());
            tag.putInt("ScanOZ", scanOrigin.getZ());
            tag.putInt("ScanSX", scanSize.getX());
            tag.putInt("ScanSY", scanSize.getY());
            tag.putInt("ScanSZ", scanSize.getZ());
            if (scanPlayerId != null) {
                tag.putUUID("ScanPlayer", scanPlayerId);
            }
            if (ghostBlueprint != null) {
                tag.put("GhostBlueprint", ghostBlueprint.save());
            }
            if (pendingFrame != null) {
                tag.putInt("FrameOX", pendingFrame.interiorOrigin().getX());
                tag.putInt("FrameOY", pendingFrame.interiorOrigin().getY());
                tag.putInt("FrameOZ", pendingFrame.interiorOrigin().getZ());
                tag.putInt("FrameSX", pendingFrame.size().getX());
                tag.putInt("FrameSY", pendingFrame.size().getY());
                tag.putInt("FrameSZ", pendingFrame.size().getZ());
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        structureName = normalizeStructureName(tag.getString("StructureName"));
        scanning = tag.getBoolean("Scanning");
        if (scanning) {
            scanProgress = tag.getInt("ScanProgress");
            scanDuration = Math.max(1, tag.getInt("ScanDuration"));
            scanStartGameTime = tag.getLong("ScanStart");
            scanOrigin = new BlockPos(tag.getInt("ScanOX"), tag.getInt("ScanOY"), tag.getInt("ScanOZ"));
            scanSize = new BlockPos(tag.getInt("ScanSX"), tag.getInt("ScanSY"), tag.getInt("ScanSZ"));
            scanPlayerId = tag.hasUUID("ScanPlayer") ? tag.getUUID("ScanPlayer") : null;
            if (tag.contains("GhostBlueprint", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                ghostBlueprint = StructureBlueprint.load(tag.getCompound("GhostBlueprint"), registries).orElse(null);
            } else if (tag.contains("PendingBlueprint", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                // Backward compatible with older in-progress scans.
                ghostBlueprint = StructureBlueprint.load(tag.getCompound("PendingBlueprint"), registries).orElse(null);
            } else {
                ghostBlueprint = null;
            }
            if (tag.contains("FrameOX")) {
                pendingFrame = new StructureFrame(
                    new BlockPos(tag.getInt("FrameOX"), tag.getInt("FrameOY"), tag.getInt("FrameOZ")),
                    new BlockPos(tag.getInt("FrameSX"), tag.getInt("FrameSY"), tag.getInt("FrameSZ"))
                );
            } else {
                pendingFrame = null;
            }
            needsScanClockAlign = true;
        } else {
            resetScanState();
        }
        updateClientActiveScan();
    }

    private static String normalizeStructureName(String structureName) {
        String trimmed = structureName == null ? "" : structureName.trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }
}
