package io.github.ooboomberoo.precaststructure.neoforge;

import net.minecraft.world.item.Item;

public final class PlatformItemFactoryImpl {
    private PlatformItemFactoryImpl() {
    }

    public static Item createPrecastStructureItem(Item.Properties properties) {
        return new ForgePrecastStructureItem(properties);
    }
}
