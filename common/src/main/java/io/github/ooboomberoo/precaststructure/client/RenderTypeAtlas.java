package io.github.ooboomberoo.precaststructure.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the bound texture from a {@link RenderType} without stringifying
 * {@code Optional[...]} via {@link RenderType#toString()}.
 */
public final class RenderTypeAtlas {
    private RenderTypeAtlas() {
    }

    @Nullable
    public static ResourceLocation texture(RenderType type) {
        if (!(type instanceof RenderType.CompositeRenderType composite)) {
            return null;
        }
        return composite.state().textureState.cutoutTexture().orElse(null);
    }
}
