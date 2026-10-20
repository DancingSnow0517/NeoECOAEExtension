package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.api.IECOTier;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/** Pixel rectangles in the computation controller artwork. */
final class ComputationControllerTextures {
    static final ResourceLocation BACKGROUND_ATLAS = ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/guis/eco_cpu_controller.png");
    static final ResourceLocation WIDGETS_ATLAS = ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/guis/cpu_widget.png");
    static final int WIDTH = 253;
    static final int HEIGHT = 231;

    private ComputationControllerTextures() {}

    static SpriteTexture background() {
        return background(() -> null);
    }

    static SpriteTexture background(Supplier<IECOTier> tier) {
        return NETextures.panelSprite(BACKGROUND_ATLAS, tier).setSprite(0, 0, WIDTH, HEIGHT);
    }

    static SpriteTexture widget(int x, int y, int width, int height) {
        return widget(x, y, width, height, () -> null);
    }

    static SpriteTexture widget(int x, int y, int width, int height, Supplier<IECOTier> tier) {
        return NETextures.panelSprite(WIDGETS_ATLAS, tier).setSprite(x, y, width, height);
    }

    static SpriteTexture tier(int tier) {
        return tier(tier, () -> null, null);
    }

    static SpriteTexture tier(int tier, Supplier<IECOTier> panelTier, @Nullable ResourceLocation custom) {
        return custom != null ? SpriteTexture.of(custom).setSprite(0, 0, 13, 18)
            : widget((Math.clamp(tier, 1, 3) - 1) * 13, 0, 13, 18, panelTier);
    }

    static SpriteTexture hostMarker(int tier, boolean bottom) {
        return hostMarker(tier, bottom, () -> null, null);
    }

    static SpriteTexture hostMarker(int tier, boolean bottom, Supplier<IECOTier> panelTier,
        @Nullable ResourceLocation custom) {
        return custom != null ? SpriteTexture.of(custom).setSprite(0, bottom ? 8 : 0, 13, 8)
            : widget(17 + (Math.clamp(tier, 1, 3) - 1) * 16, bottom ? 43 : 19, 14, 8, panelTier);
    }

    static SpriteTexture columnBackground() {
        return columnBackground(() -> null);
    }

    static SpriteTexture columnBackground(Supplier<IECOTier> tier) {
        return widget(0, 52, 16, 52, tier);
    }

    static SpriteTexture glyphUpper(int tier) {
        return widget(17 + (Math.clamp(tier, 1, 3) - 1) * 12, 67, 10, 5);
    }

    static SpriteTexture glyphCore(int tier) {
        return widget(17 + (Math.clamp(tier, 1, 3) - 1) * 12, 53, 10, 6);
    }

    static SpriteTexture glyphLower(int tier) {
        return widget(17 + (Math.clamp(tier, 1, 3) - 1) * 12, 61, 10, 6);
    }

    static SpriteTexture member(ComputationControllerPanelState.Member member, int row) {
        return member(member, row, () -> null);
    }

    static SpriteTexture member(ComputationControllerPanelState.Member member, int row, Supplier<IECOTier> tier) {
        if (member.texture() != null) return SpriteTexture.of(member.texture()).setSprite(0, 0, 10, row == 0 ? 5 : 6);
        SpriteTexture legacy = switch (row) {
            case 0 -> glyphUpper(member.tier());
            case 1 -> glyphCore(member.tier());
            default -> glyphLower(member.tier());
        };
        return widget(legacy.spritePosition.x, legacy.spritePosition.y, legacy.spriteSize.width, legacy.spriteSize.height, tier);
    }

    static SpriteTexture statusIcon(int index, boolean pink) {
        return statusIcon(index, pink, () -> null, null);
    }

    static SpriteTexture statusIcon(int index, boolean pink, Supplier<IECOTier> tier, @Nullable ResourceLocation custom) {
        if (custom != null) return SpriteTexture.of(custom).setSprite(0, 0, 12, 12);
        return widget(Math.clamp(index, 0, pink ? 4 : 5) * 12, pink ? 116 : 104, 12, 12, tier);
    }

    static SpriteTexture switchSprite(boolean on, boolean hover) {
        return switchSprite(on, hover, () -> null, null);
    }

    static SpriteTexture switchSprite(boolean on, boolean hover, Supplier<IECOTier> tier,
        @Nullable ResourceLocation custom) {
        return custom != null ? SpriteTexture.of(custom).setSprite(0, 0, 15, 26)
            : widget((on ? 0 : 30) + (hover ? 15 : 0), 128, 15, 26, tier);
    }
}
