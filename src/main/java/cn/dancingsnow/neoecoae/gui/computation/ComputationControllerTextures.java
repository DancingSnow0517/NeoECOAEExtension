package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import net.minecraft.resources.ResourceLocation;

/** Pixel rectangles in the computation controller artwork. */
final class ComputationControllerTextures {
    static final ResourceLocation BACKGROUND_ATLAS = new ResourceLocation("neoecoae", "textures/guis/eco_cpu_controller.png");
    static final ResourceLocation WIDGETS_ATLAS = new ResourceLocation("neoecoae", "textures/guis/cpu_widget.png");
    static final int WIDTH = 253;
    static final int HEIGHT = 231;

    private ComputationControllerTextures() {}

    static SpriteTexture background() {
        return NETextures.panelSprite(BACKGROUND_ATLAS).setSprite(0, 0, WIDTH, HEIGHT);
    }

    static SpriteTexture widget(int x, int y, int width, int height) {
        return NETextures.panelSprite(WIDGETS_ATLAS).setSprite(x, y, width, height);
    }

    static SpriteTexture tier(int tier) {
        return widget((JdkCompat.clamp(tier, 1, 3) - 1) * 13, 0, 13, 18);
    }

    static SpriteTexture hostMarker(int tier, boolean bottom) {
        return widget(17 + (JdkCompat.clamp(tier, 1, 3) - 1) * 16, bottom ? 43 : 19, 14, 8);
    }

    static SpriteTexture columnBackground() {
        return widget(0, 52, 16, 52);
    }

    static SpriteTexture glyphUpper(int tier) {
        return widget(17 + (JdkCompat.clamp(tier, 1, 3) - 1) * 12, 67, 10, 5);
    }

    static SpriteTexture glyphCore(int tier) {
        return widget(17 + (JdkCompat.clamp(tier, 1, 3) - 1) * 12, 53, 10, 6);
    }

    static SpriteTexture glyphLower(int tier) {
        return widget(17 + (JdkCompat.clamp(tier, 1, 3) - 1) * 12, 61, 10, 6);
    }

    static SpriteTexture member(ComputationControllerPanelState.Member member, int row) {
        if (member.texture() != null) return SpriteTexture.of(member.texture()).setSprite(0, 0, 10, row == 0 ? 5 : 6);
        return switch (row) {
            case 0 -> glyphUpper(member.tier());
            case 1 -> glyphCore(member.tier());
            default -> glyphLower(member.tier());
        };
    }

    static SpriteTexture statusIcon(int index, boolean pink) {
        if (pink) return widget(JdkCompat.clamp(index, 0, 4) * 12, 116, 12, 12);
        return widget(JdkCompat.clamp(index, 0, 5) * 12, 104, 12, 12);
    }

    static SpriteTexture switchSprite(boolean on, boolean hover) {
        return widget((on ? 0 : 30) + (hover ? 15 : 0), 128, 15, 26);
    }
}
