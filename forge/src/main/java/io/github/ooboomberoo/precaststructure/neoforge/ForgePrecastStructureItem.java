package io.github.ooboomberoo.precaststructure.neoforge;

import io.github.ooboomberoo.precaststructure.item.PrecastStructureItem;
import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class ForgePrecastStructureItem extends PrecastStructureItem {
    public ForgePrecastStructureItem(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new ForgeStructureItemRenderer();
                }
                return renderer;
            }
        });
    }
}
