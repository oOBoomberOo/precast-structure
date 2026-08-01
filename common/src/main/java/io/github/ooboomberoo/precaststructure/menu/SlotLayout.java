package io.github.ooboomberoo.precaststructure.menu;

import net.minecraft.world.inventory.Slot;

/** Slot.x/y are made mutable via access widener for dynamic printer layout. */
final class SlotLayout {
    private SlotLayout() {
    }

    static void set(Slot slot, int x, int y) {
        slot.x = x;
        slot.y = y;
    }
}
