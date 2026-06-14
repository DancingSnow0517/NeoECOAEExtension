package cn.dancingsnow.neoecoae.client.multiblock.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import org.joml.Matrix4f;

public final class MultiblockPreviewScrollHandler {
    private static final long TRACK_TIMEOUT_NANOS = 250_000_000L;

    private static NEMultiblockSceneRenderer activeRenderer;
    private static Screen activeScreen;
    private static int activeX;
    private static int activeY;
    private static int activeW;
    private static int activeH;
    private static long lastTrackNanos;

    private MultiblockPreviewScrollHandler() {}

    public static void track(NEMultiblockSceneRenderer renderer, GuiGraphics g, int x, int y, int width, int height) {
        Matrix4f matrix = g.pose().last().pose();
        activeRenderer = renderer;
        activeScreen = Minecraft.getInstance().screen;
        activeX = Math.round(x + matrix.m30());
        activeY = Math.round(y + matrix.m31());
        activeW = width;
        activeH = height;
        lastTrackNanos = System.nanoTime();
    }

    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        NEMultiblockSceneRenderer renderer = activeRenderer;
        if (renderer == null || event.getScreen() != activeScreen) {
            return;
        }
        if (System.nanoTime() - lastTrackNanos > TRACK_TIMEOUT_NANOS) {
            return;
        }

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        if (mouseX < activeX || mouseX >= activeX + activeW || mouseY < activeY || mouseY >= activeY + activeH) {
            return;
        }

        renderer.adjustZoom(event.getScrollDelta());
        event.setCanceled(true);
    }
}
