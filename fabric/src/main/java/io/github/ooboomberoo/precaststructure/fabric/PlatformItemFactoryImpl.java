package io.github.ooboomberoo.precaststructure.fabric;

import io.github.ooboomberoo.precaststructure.item.PrecastStructureItem;
import net.minecraft.world.item.Item;

public final class PlatformItemFactoryImpl {
    private PlatformItemFactoryImpl() {
    }

    public static Item createPrecastStructureItem(Item.Properties properties) {
        return new PrecastStructureItem(properties);
    }
}
