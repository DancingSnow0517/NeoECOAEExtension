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
        STORAGE_MARKER,
        /** Standalone 13x18 badge, sampled at (0, 0). */
        COMPUTATION_TIER,
        /** Same 13x16 layout as STORAGE_MARKER. */
        COMPUTATION_MARKER,
        /** Standalone 13x18 badge, sampled at (0, 0). */
        CRAFTING_TIER,
        /** Same 13x16 layout as STORAGE_MARKER. */
        CRAFTING_MARKER,
        /** Standalone 12x12 status icon; index is the original atlas column (0..5). */
        COMPUTATION_STATUS,
        /** Standalone 15x26 switch; indices 0/1/2 are substitutions/cycle planning/fast planner. */
        COMPUTATION_SWITCH,
        /** Standalone 15x26 mode button; indices 0/1 are active cooling/overclock. */
        CRAFTING_MODE
    }

    /**
     * Called on the server while constructing a display snapshot. Return a texture file location
     * (for example addon:textures/gui/core.png), or null to retain the built-in artwork.
     * Each part's crop is documented above; panels retain their existing positions and dimensions.
     * Implement this on the member block, so block entities need not be subclassed.
     * Members and hosts receive their actual placed state, including facing and addon properties.
     */
    @Nullable
    default ResourceLocation getPanelTexture(BlockState state, Part part) {
        return null;
    }

    /**
     * Stateful artwork, sampled at (0, 0) at the part's documented size. For status icons active
     * is the existing pink-row flag and hovered is false. For buttons active is the setting's
     * enabled flag; hovered also supplies the pressed appearance. Visibility and actions are unchanged.
     * All variants are captured on the server and refreshed when the placed state changes.
     */
    @Nullable
    default ResourceLocation getPanelTexture(BlockState state, Part part, int index,
        boolean active, boolean hovered) {
        return getPanelTexture(state, part);
    }

    @Nullable
    static ResourceLocation texture(BlockState state, Part part) {
        return state.getBlock() instanceof IHostPanelTextureProvider provider
            ? provider.getPanelTexture(state, part) : null;
    }

    @Nullable
    static ResourceLocation texture(BlockState state, Part part, int index, boolean active, boolean hovered) {
        return state.getBlock() instanceof IHostPanelTextureProvider provider
            ? provider.getPanelTexture(state, part, index, active, hovered) : null;
    }
}
