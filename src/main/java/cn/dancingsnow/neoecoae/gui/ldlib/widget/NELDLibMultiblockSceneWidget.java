package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewScene;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibGuiRenderState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibMultiblockSceneAdapter;
import com.lowdragmc.lowdraglib.client.scene.FBOWorldSceneRenderer;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

public class NELDLibMultiblockSceneWidget extends SceneWidget {
    private static final int FBO_SCALE = 4;
    private static final int MIN_FBO_SIDE = 128;
    private static final int MAX_FBO_SIDE = 2048;
    private static final float FINE_ZOOM_STEP = 0.1F;
    private static final float MIN_ZOOM = 0.1F;
    private static final float MAX_ZOOM = 999.0F;
    private static float savedYaw = NELDLibMultiblockSceneAdapter.DEFAULT_YAW;
    private static float savedPitch = NELDLibMultiblockSceneAdapter.DEFAULT_PITCH;
    private static float savedZoom = NELDLibMultiblockSceneAdapter.DEFAULT_ZOOM;
    private static float savedRange = -1.0F;

    private final Supplier<MultiblockPreviewScene> sceneSupplier;
    private SceneKey appliedSceneKey;
    private boolean hasRenderableScene;
    private boolean cameraInitialized;

    public NELDLibMultiblockSceneWidget(
            int x, int y, int width, int height, Supplier<MultiblockPreviewScene> sceneSupplier) {
        super(x, y, width, height, null, true);
        this.sceneSupplier = sceneSupplier;
        setRenderFacing(false);
        setRenderSelect(false);
        setDraggable(true);
        setScalable(true);
        setIntractable(true);
        setHoverTips(true);
        useOrtho(true);
        useCache = false;
        autoReleased = true;
        setOnAddedTooltips((widget, tooltips) -> {
            if (!hasRenderableScene) {
                tooltips.add(Component.translatable("emi.neoecoae.multiblock.empty_scene"));
            }
        });
    }

    @Override
    public void updateScreen() {
        if (isRemote()) {
            ensureSceneCreated();
            applySceneIfChanged();
        }
        super.updateScreen();
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ensureSceneCreated();
        applySceneIfChanged();
        if (hasRenderableScene) {
            saveCameraState();
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
            NELDLibGuiRenderState.restoreGui2dStateAfterScene(graphics);
            saveCameraState();
        } else {
            drawEmptyScene(graphics);
        }
    }

    @Override
    public void releaseCacheBuffer() {
        saveCameraState();
        super.releaseCacheBuffer();
        if (renderer instanceof FBOWorldSceneRenderer fboRenderer) {
            fboRenderer.releaseFBO();
        }
        renderer = null;
        dummyWorld = null;
        core = null;
        appliedSceneKey = null;
        hasRenderableScene = false;
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (Screen.hasShiftDown() && wheelDelta != 0.0D && isScalable() && isMouseOverElement(mouseX, mouseY)) {
            float zoomDelta = wheelDelta < 0.0D ? FINE_ZOOM_STEP : -FINE_ZOOM_STEP;
            setZoom(Mth.clamp(getZoom() + zoomDelta, MIN_ZOOM, MAX_ZOOM));
            saveCameraState();
            return true;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    private void ensureSceneCreated() {
        if (getDummyWorld() != null) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            return;
        }
        createPreviewScene();
        setClearColor(0x00000000);
    }

    private void createPreviewScene() {
        core = new HashSet<>();
        dummyWorld = new TrackedDummyWorld();
        dummyWorld.setRenderFilter(pos -> renderer != null
                && renderer.renderedBlocksMap.keySet().stream().anyMatch(blocks -> blocks.contains(pos)));
        if (renderer != null) {
            renderer.deleteCacheBuffer();
        }
        renderer = new FBOWorldSceneRenderer(dummyWorld, fboWidth(), fboHeight());
        center = new Vector3f();
        renderer.useOrtho(useOrtho);
        renderer.setOnLookingAt(hit -> {});
        renderer.setBeforeBatchEnd(this::renderBeforeBatchEnd);
        renderer.setAfterWorldRender(this::renderBlockOverLay);
        if (beforeWorldRender != null) {
            renderer.setBeforeWorldRender(scene -> beforeWorldRender.accept(this));
        }
        renderer.setCameraLookAt(center, camZoom(), Math.toRadians(rotationPitch), Math.toRadians(rotationYaw));
        renderer.useCacheBuffer(false);
        renderer.setParticleManager(createParticleManager());
        clickPosFace = null;
        hoverPosFace = null;
        hoverItem = null;
        selectedPosFace = null;
    }

    private int fboWidth() {
        return scaledFboSide(getSizeWidth());
    }

    private int fboHeight() {
        return scaledFboSide(getSizeHeight());
    }

    private static int scaledFboSide(int widgetSide) {
        return Math.max(MIN_FBO_SIDE, Math.min(MAX_FBO_SIDE, widgetSide * FBO_SCALE));
    }

    private void applySceneIfChanged() {
        if (getDummyWorld() == null) {
            return;
        }
        MultiblockPreviewScene scene = sceneSupplier.get();
        SceneKey key = SceneKey.of(scene);
        if (Objects.equals(appliedSceneKey, key)) {
            return;
        }
        hasRenderableScene =
                NELDLibMultiblockSceneAdapter.apply(this, scene, !cameraInitialized, savedYaw, savedPitch, savedZoom);
        if (cameraInitialized && savedRange > 0.0F) {
            setOrthoRange(savedRange);
            setZoom(savedZoom);
            setCameraYawAndPitch(savedYaw, savedPitch);
        }
        cameraInitialized = true;
        appliedSceneKey = key;
    }

    private void saveCameraState() {
        savedYaw = getRotationYaw();
        savedPitch = getRotationPitch();
        savedZoom = getZoom();
        savedRange = getRange();
    }

    private void drawEmptyScene(GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        Component text = Component.translatable("emi.neoecoae.multiblock.empty_scene");
        int textX = getPositionX() + Math.max(0, (getSizeWidth() - font.width(text)) / 2);
        int textY = getPositionY() + Math.max(0, (getSizeHeight() - font.lineHeight) / 2);
        graphics.drawString(font, text, textX, textY, 0xFF777777, false);
    }

    private record SceneKey(
            Object definition,
            int expand,
            boolean formed,
            int blocks,
            int contentHash,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ) {
        private static SceneKey of(MultiblockPreviewScene scene) {
            if (scene == null) {
                return new SceneKey(null, 0, false, 0, 0, 0, 0, 0, 0, 0, 0);
            }
            return new SceneKey(
                    scene.definition(),
                    scene.expand(),
                    scene.formed(),
                    scene.blocks().size(),
                    scene.blocks().hashCode(),
                    scene.minX(),
                    scene.minY(),
                    scene.minZ(),
                    scene.maxX(),
                    scene.maxY(),
                    scene.maxZ());
        }
    }
}
