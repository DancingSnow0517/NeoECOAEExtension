package cn.dancingsnow.neoecoae.gui.storage;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.texture.VanillaSpriteTexture;
import net.minecraft.resources.ResourceLocation;

/** Pixel rectangles in the storage host artwork. */
final class StoragePanelTextures {
    private static final ResourceLocation ATLAS = id("storage_widget.png");
    // Let the GUI atlas consume .mcmeta. GeckoLib replaces raw animated textures with a
    // single uploaded frame, so sampling that texture as a 12x120 sheet breaks the UVs.
    static final VanillaSpriteTexture INFINITE_ANIMATION = VanillaSpriteTexture.of(
        ResourceLocation.fromNamespaceAndPath("neoecoae", "storage/infinity"));
    static final SpriteTexture HOST_ICON_BACKGROUND = sprite(0, 18, 16, 34);
    static final SpriteTexture TYPE_PANEL = sprite(0, 104, 68, 29);
    static final SpriteTexture BLUE_BAR = sprite(68, 105, 29, 4);
    static final SpriteTexture PINK_BAR = sprite(68, 109, 29, 4);
    // Keep the highlight's one-pixel end caps when widening the main capacity gauge.
    static final SpriteTexture CAPACITY_BAR = PINK_BAR.copy().setBorder(1, 0, 1, 0);
    static final SpriteTexture SLIDER = sprite(68, 113, 6, 15);
    static final SpriteTexture SCALE = sprite(0, 133, 74, 7);
    static final SpriteTexture INFINITY = sprite(16, 52, 24, 10);
    static final SpriteTexture RESOURCE_HOVER = sprite(24, 78, 8, 8);
    // Marks an unset resource filter; the glyph sits inside the 16px item area.
    static final SpriteTexture EMPTY_FILTER = sprite(240, 0, 16, 16);
    static final SpriteTexture CELL_COLUMN = sprite(0, 52, 16, 52);
    static final int FALLBACK_SWATCH = 14;
    static final int[] COLORS = {
        0x17B86D, 0x3894FF, 0xEC8600, 0xCCCCCC, 0xCC493D, 0xFFB2D8,
        0x72B036, 0x3AD8CB, 0xC93BED, 0x06DFFF,
        0x2CCDB1, 0xCC4BBD, 0x6056AF, 0x6F8259,
        0xCCCCCC
    };

    private StoragePanelTextures() {}

    private static ResourceLocation id(String file) {
        return ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/guis/storage/" + file);
    }

    static SpriteTexture sprite(int x, int y, int width, int height) {
        return SpriteTexture.of(ATLAS).setSprite(x, y, width, height);
    }

    static SpriteTexture tier(int tier) {
        return sprite((Math.clamp(tier, 1, 3) - 1) * 13, 0, 13, 18);
    }

    /** Upper row: inactive symbol; lower row: selected symbol. Blue background is hover/press. */
    static SpriteTexture interfaceMode(int mode, boolean selected, boolean hover) {
        return sprite((Math.clamp(mode, 0, 2) + (hover ? 3 : 0)) * 16,
            selected ? 159 : 140, 16, selected ? 17 : 18);
    }

    static SpriteTexture hostMarker(int tier, boolean bottom) {
        return sprite(18 + (Math.clamp(tier, 1, 3) - 1) * 16, bottom ? 43 : 19, 13, 8);
    }

    /** Two 18px rows and a final 16px slot; the panel artwork supplies the closing border. */
    static SpriteTexture cellBackground(int row) {
        int offset = row * 18;
        return sprite(CELL_COLUMN.spritePosition.x, CELL_COLUMN.spritePosition.y + offset,
            CELL_COLUMN.spriteSize.width, row == 2 ? 16 : 18);
    }

    static SpriteTexture swatch(int index, int color) {
        if (index < 0 || index >= FALLBACK_SWATCH) return sprite(16, 78, 8, 8).setBorder(1, 0, 1, 0)
            .setColor(0xFF000000 | color & 0xFFFFFF);
        if (index < 6) {
            return sprite(16 + index * 8, 62, 8, 8).setBorder(1, 0, 1, 0);
        } else if (index < 10) {
            return sprite(16 + (index - 6) * 8, 70, 8, 8).setBorder(1, 0, 1, 0);
        } else {
            return sprite(64 + (index - 10) * 8, 62, 8, 8).setBorder(1, 0, 1, 0);
        }
    }

    /** Resource pictograms follow the same channel order as the color swatches. */
    static SpriteTexture resourceIcon(int index, int color) {
        if (index < 0 || index >= FALLBACK_SWATCH) return swatch(index, color);
        if (index < 6) {
            return sprite(16 + index * 8, 86, 8, 8);
        } else if (index < 10) {
            return sprite(16 + (index - 6) * 8, 94, 8, 8);
        } else {
            return sprite(64 + (index - 10) * 8, 86, 8, 8);
        }
    }

    /** Fixed resource channels from the supplied legend; other channels use the tintable extra sprite. */
    static int swatchIndex(ResourceLocation id) {
        if (id == null) return FALLBACK_SWATCH;
        return switch (id.toString()) {
            case "neoecoae:mekanism", "neoecoae:mega_chemical", "appmek:chemical" -> 0;
            case "neoecoae:fluids", "neoecoae:mega_fluid", "ae2:f" -> 1;
            case "neoecoae:items", "neoecoae:bulk_item", "neoecoae:mega_item", "ae2:i" -> 2;
            case "neoecoae:air", "neoecoae:mega_air", "appliedpneumatics:air_type" -> 3;
            case "neoecoae:flux", "neoecoae:mega_energy", "appflux:flux" -> 4;
            case "neoecoae:lightning", "ae2lt:lightning" -> 5;
            case "neoecoae:experience", "neoecoae:mega_experience", "appex:experience" -> 6;
            case "neoecoae:soul", "neoecoae:mega_soul", "soulplied_energistics:soul", "appliedsoul:soul" -> 7;
            case "neoecoae:source", "neoecoae:mega_source", "arseng:source" -> 8;
            case "neoecoae:mana", "neoecoae:mega_mana", "appbot:mana" -> 9;
            case "neoecoae:omni", "ae2omnicells:omni", "ae2omnicells:omni_cell" -> 10;
            case "neoecoae:complex_omni", "ae2omnicells:complex_omni", "ae2omnicells:complex_omni_cell" -> 11;
            case "neoecoae:quantum_omni", "ae2omnicells:quantum_omni", "ae2omnicells:quantum_omni_cell" -> 12;
            case "neoecoae:spent_nuclear_waste", "ae2omnicells:spent_nuclear_waste", "ae2omnicells:spent_nuclear_waste_cell", "mekanism:spent_nuclear_waste" -> 13;
            default -> FALLBACK_SWATCH;
        };
    }
}
