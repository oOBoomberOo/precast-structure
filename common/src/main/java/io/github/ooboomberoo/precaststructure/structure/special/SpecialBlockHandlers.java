package io.github.ooboomberoo.precaststructure.structure.special;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Ordered registry of {@link SpecialBlockHandler}s. First match wins.
 *
 * <p>Built-ins cover chests/shulkers, beds, and vertical double blocks (doors / tall plants).
 * Soft-compat modules (e.g. Create) append with {@link #register} from mod init when present.
 */
public final class SpecialBlockHandlers {
    private static final List<SpecialBlockHandler> HANDLERS = new ArrayList<>();
    private static boolean bootstrapped;

    static {
        bootstrap();
    }

    private SpecialBlockHandlers() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        register(new BedSpecialHandler());
        register(new ChestSpecialHandler());
        register(new DoubleBlockSpecialHandler());
    }

    /** Append a handler. Earlier registrations take priority on {@link #find}. */
    public static synchronized void register(SpecialBlockHandler handler) {
        HANDLERS.add(handler);
    }

    @Nullable
    public static SpecialBlockHandler find(BlockState state) {
        for (SpecialBlockHandler handler : HANDLERS) {
            if (handler.matches(state)) {
                return handler;
            }
        }
        return null;
    }

    @Nullable
    public static CompoundTag sanitizeCaptured(BlockState state, @Nullable CompoundTag nbt) {
        if (nbt == null || nbt.isEmpty()) {
            return nbt;
        }
        SpecialBlockHandler handler = find(state);
        CompoundTag sanitized = handler != null
            ? handler.sanitizeCapturedNbt(state, nbt)
            : InventoryNbt.stripContainerContents(nbt);
        return sanitized == null || sanitized.isEmpty() ? null : sanitized;
    }

    @Nullable
    public static CompoundTag sanitizePlacement(BlockState state, @Nullable CompoundTag nbt) {
        if (nbt == null || nbt.isEmpty()) {
            return nbt;
        }
        SpecialBlockHandler handler = find(state);
        CompoundTag sanitized = handler != null
            ? handler.sanitizePlacementNbt(state, nbt)
            : InventoryNbt.stripContainerContents(nbt);
        return sanitized == null || sanitized.isEmpty() ? null : sanitized;
    }

    public static OptionalInt materialUnits(BlockState state) {
        SpecialBlockHandler handler = find(state);
        return handler != null ? handler.materialUnits(state) : OptionalInt.empty();
    }

    /**
     * @return {@code true} if a special handler fully determined material costs for this block
     */
    public static boolean mergeRequirements(
        BlockState state,
        @Nullable CompoundTag nbt,
        Map<Item, Integer> requirements,
        HolderLookup.Provider registries
    ) {
        SpecialBlockHandler handler = find(state);
        return handler != null && handler.mergeRequirements(state, nbt, requirements, registries);
    }

    public static boolean shouldRenderPreview(BlockState state) {
        SpecialBlockHandler handler = find(state);
        return handler == null || handler.shouldRenderPreview(state);
    }
}
