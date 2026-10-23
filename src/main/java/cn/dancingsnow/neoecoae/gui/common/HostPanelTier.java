package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Shared tier appearance, with legacy integer-only UI callers retaining their built-in artwork. */
public final class HostPanelTier {
    private HostPanelTier() {}

    @Nullable
    public static IECOTier resolve(@Nullable IECOTier tier, int legacyTier) {
        return tier != null ? tier : switch (legacyTier) {
            case 1 -> ECOTier.L4;
            case 2 -> ECOTier.L6;
            case 3 -> ECOTier.L9;
            default -> null;
        };
    }

    public static Component name(@Nullable IECOTier tier, int legacyTier) {
        IECOTier resolved = resolve(tier, legacyTier);
        return resolved == null ? Component.literal("L" + legacyTier) : resolved.getPanelTierName();
    }

    /** Returns false when the caller should draw the original built-in tier glyph. */
    public static boolean drawName(GUIContext context, @Nullable IECOTier tier,
        float x, float y, float width, float height, int color) {
        return drawName(context, tier, x, y, width, height, color, null);
    }

    /** The optional clean background removes the controller's baked-in placeholder glyph. */
    public static boolean drawName(GUIContext context, @Nullable IECOTier tier,
        float x, float y, float width, float height, int color, @Nullable IGuiTexture background) {
        if (tier == null || tier instanceof ECOTier) return false;
        if (background != null) context.drawTexture(background, x, y, width, height);
        Component name = tier.getPanelTierName();
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1, Math.min(width / Math.max(1, font.width(name)), height / font.lineHeight));
        var pose = context.graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x + (width - font.width(name) * scale) / 2,
                y + (height - font.lineHeight * scale) / 2, 0);
            pose.scale(scale, scale, 1);
            context.graphics.drawString(font, name, 0, 0, color, false);
        } finally {
            pose.popPose();
        }
        return true;
    }

    /** Returns false when the caller should draw the original atlas marker. */
    public static boolean drawMarker(GUIContext context, @Nullable IECOTier tier, boolean bottom,
        float x, float y, float width, float height) {
        var texture = tier == null ? null : tier.getHostMarkerTexture();
        if (texture == null) return false;
        context.drawTexture(SpriteTexture.of(texture).setSprite(0, bottom ? 8 : 0, 13, 8),
            x, y, width, height);
        return true;
    }
}
