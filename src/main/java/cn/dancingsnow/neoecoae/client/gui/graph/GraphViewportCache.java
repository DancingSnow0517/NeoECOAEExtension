package cn.dancingsnow.neoecoae.client.gui.graph;

import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dense-overview cache for 26.1's deferred GUI pipeline.
 * <p>
 * The 1.21.1 version rasterised the overview into off-screen {@code TextureTarget} tiles with immediate-mode GL
 * calls that no longer exist. 26.1 rebuilds a {@link GuiRenderState} every frame, so this cache records the
 * overview's submitted render states (quads, glyph text, item icons, blits) once and replays them while the
 * view is stationary. Replaying goes through the same {@code GuiRenderState#add*} calls, in the same order, so
 * layering is identical to painting live. The recording is redone when the graph, layout, viewport, zoom, pose,
 * scissor, GUI scale or client resources change, and at least once per {@link #MAX_AGE_NANOS} so animated item
 * models keep moving.
 */
final class GraphViewportCache implements AutoCloseable {
    private static final long MAX_AGE_NANOS = 1_000_000_000L;
    private static volatile int resourceVersion;

    interface Painter {
        boolean advance(GuiGraphicsExtractor graphics, long deadline);
    }

    interface Factory {
        Painter create(Bounds area, double pixelsPerUnit);
    }

    private @Nullable Bounds viewport;
    private float zoom = 1;
    private @Nullable Factory factory;
    private @Nullable Object graph;
    private @Nullable Object layout;

    private final Recording recording = new Recording();
    private @Nullable Key recordedKey;
    private long recordedAt;

    static void resourcesReloaded() { resourceVersion++; }

    void prepare(GuiGraphicsExtractor parent, Object graph, Object layout, Bounds viewport, float zoom, Factory factory) {
        this.graph = graph;
        this.layout = layout;
        this.viewport = viewport;
        this.zoom = zoom;
        this.factory = factory;
    }

    void draw(GuiGraphicsExtractor graphics) {
        if (viewport == null || factory == null) return;
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        var scissor = graphics.peekScissorStack();
        long now = System.nanoTime();
        if (recordedKey == null || !recordedKey.matches(graph, layout, viewport, zoom, graphics.pose(),
                scissor, guiScale, resourceVersion) || now - recordedAt > MAX_AGE_NANOS) {
            // A stationary replay needs no new key or pose snapshot.
            record(new Key(graph, layout, viewport, zoom, new Matrix3x2f(graphics.pose()),
                scissor, guiScale, resourceVersion), now);
        }
        recording.replay(graphics.guiRenderState);
    }

    private void record(Key key, long now) {
        recording.clear();
        recordedKey = null;
        var extractor = new GuiGraphicsExtractor(Minecraft.getInstance(), recording, -1, -1);
        // Scissor first, at identity pose, so it keeps the parent's screen-space rectangle.
        if (key.scissor() != null) {
            var scissor = key.scissor();
            extractor.enableScissor(scissor.left(), scissor.top(), scissor.right(), scissor.bottom());
        }
        extractor.pose().set(key.pose());
        var painter = factory.create(viewport.padded((float) (32 / Math.max(zoom, 0.0001f))), zoom * key.guiScale());
        // Paint to completion; the painter is incremental only to bound per-frame tile work.
        while (!painter.advance(extractor, Long.MAX_VALUE)) {
            // keep going
        }
        recordedKey = key;
        recordedAt = now;
    }

    @Override
    public void close() {
        viewport = null;
        factory = null;
        graph = null;
        layout = null;
        recordedKey = null;
        recording.clear();
    }

    /** Graph and layout compare by identity: a new snapshot is a new picture even if it is equal. */
    private record Key(@Nullable Object graph, @Nullable Object layout, Bounds viewport, float zoom, Matrix3x2f pose,
            @Nullable ScreenRectangle scissor, double guiScale, int resources) {
        boolean matches(@Nullable Object nextGraph, @Nullable Object nextLayout, Bounds nextViewport, float nextZoom,
                Matrix3x2f nextPose, @Nullable ScreenRectangle nextScissor, double nextGuiScale, int nextResources) {
            return graph == nextGraph && layout == nextLayout && zoom == nextZoom && guiScale == nextGuiScale
                && resources == nextResources && viewport.equals(nextViewport) && pose.equals(nextPose)
                && Objects.equals(scissor, nextScissor);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && matches(key.graph, key.layout, key.viewport, key.zoom,
                key.pose, key.scissor, key.guiScale, key.resources);
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(graph), System.identityHashCode(layout), viewport, zoom,
                pose, scissor, guiScale, resources);
        }
    }

    /** Records the state-building calls GuiGraphicsExtractor makes, for replay into the frame's real state. */
    private static final class Recording extends GuiRenderState {
        private static final byte ELEMENT = 0, ITEM = 1, TEXT = 2, PIP = 3, BLIT = 4, GLYPH = 5, UP = 6, STRATUM = 7;
        // Null only while GuiRenderState's constructor runs (it calls nextStratum()).
        private @Nullable List<Object> states;
        private byte[] kinds = new byte[256];

        Recording() {
            super();
            states = new ArrayList<>();
        }

        private void add(byte kind, @Nullable Object state) {
            int index = states.size();
            if (index == kinds.length) kinds = java.util.Arrays.copyOf(kinds, index * 2);
            kinds[index] = kind;
            states.add(state);
        }

        void clear() {
            // The superclass tree is never used: every mutator below only appends to the log.
            states.clear();
        }

        void replay(GuiRenderState target) {
            for (int i = 0; i < states.size(); i++) {
                Object state = states.get(i);
                switch (kinds[i]) {
                    case ELEMENT -> target.addGuiElement((GuiElementRenderState) state);
                    case ITEM -> target.addItem((GuiItemRenderState) state);
                    case TEXT -> target.addText((GuiTextRenderState) state);
                    case PIP -> target.addPicturesInPictureState((PictureInPictureRenderState) state);
                    case BLIT -> target.addBlitToCurrentLayer((BlitRenderState) state);
                    case GLYPH -> target.addGlyphToCurrentLayer((GuiElementRenderState) state);
                    case UP -> target.up();
                    case STRATUM -> target.nextStratum();
                    default -> throw new IllegalStateException("Unknown recorded GUI state kind " + kinds[i]);
                }
            }
        }

        @Override public void nextStratum() { if (states == null) super.nextStratum(); else add(STRATUM, null); }
        // A replayed blur would conflict with the screen's own; the overview never requests one.
        @Override public void blurBeforeThisStratum() {}
        @Override public void up() { if (states == null) super.up(); else add(UP, null); }
        @Override public void addItem(GuiItemRenderState itemState) { add(ITEM, itemState); }
        @Override public void addText(GuiTextRenderState textState) { add(TEXT, textState); }
        @Override public void addPicturesInPictureState(PictureInPictureRenderState state) { add(PIP, state); }
        @Override public void addGuiElement(GuiElementRenderState blitState) { add(ELEMENT, blitState); }
        @Override public void addBlitToCurrentLayer(BlitRenderState blitState) { add(BLIT, blitState); }
        @Override public void addGlyphToCurrentLayer(GuiElementRenderState glyphState) { add(GLYPH, glyphState); }
    }
}
