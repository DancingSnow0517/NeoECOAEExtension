package cn.dancingsnow.neoecoae.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Optional artwork supplied by the block being displayed, independent of its numeric ECO tier. */
public interface IHostPanelTextureProvider {
    enum Part {
        /** Standalone 10x5 image, sampled at (0, 0). */
        COMPUTATION_UPPER,
        /** Standalone 10x6 image, sampled at (0, 0). */
        COMPUTATION_CORE,
        /** Standalone 10x6 image, sampled at (0, 0). */
        COMPUTATION_LOWER,
        /** Standalone 13x18 image, sampled at (0, 0). */
        STORAGE_TIER,
        /** 13x16 image: upper marker at (0, 0), lower marker at (0, 8), each 13x8. */
        STORAGE_MARKER
    }

    /**
     * Called on the server while constructing a display snapshot. Return a texture file location
     * (for example addon:textures/gui/core.png), or null to retain the built-in artwork.
     * Each part's crop is documented above; panels retain their existing positions and dimensions.
     * Implement this on the member block, so block entities need not be subclassed.
     * Computation members receive their actual state; storage hosts receive the default state
     * of their configured host item block, so storage artwork should not depend on facing/formed.
     */
    @Nullable
    default ResourceLocation getPanelTexture(BlockState state, Part part) {
        return null;
    }

    @Nullable
    static ResourceLocation texture(BlockState state, Part part) {
        return state.getBlock() instanceof IHostPanelTextureProvider provider
            ? provider.getPanelTexture(state, part) : null;
    }
}
