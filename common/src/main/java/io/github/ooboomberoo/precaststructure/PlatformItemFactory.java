package io.github.ooboomberoo.precaststructure;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.item.Item;

public final class PlatformItemFactory {
    private PlatformItemFactory() {
    }

    @ExpectPlatform
    public static Item createPrecastStructureItem(Item.Properties properties) {
        throw new AssertionError();
    }
}
