package cn.dancingsnow.neoecoae.client.rendering;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Client-only, time-limited see-through outlines for blocks located from a GUI (e.g. pattern providers found via
 * "locate pattern"). Highlights are filtered by dimension, drawn
 * steadily through all terrain at any distance, and kept until they expire.
 */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public final class BlockHighlightRenderer {
    private static final long DURATION_MS = 15_000L;
    private static final List<Highlight> HIGHLIGHTS = new ArrayList<>();
    private static final MultiBufferSource.BufferSource BUFFERS =
        MultiBufferSource.immediate(new BufferBuilder(RenderType.TRANSIENT_BUFFER_SIZE));

    private record Highlight(ResourceLocation dimension, BlockPos position, long expiresAt) {}

    private BlockHighlightRenderer() {}

    /** Replaces the current highlights with the given positions in the given dimension. */
    public static void highlight(ResourceLocation dimension, List<BlockPos> positions) {
        long expiresAt = Util.getMillis() + DURATION_MS;
        HIGHLIGHTS.clear();
        for (var position : positions) HIGHLIGHTS.add(new Highlight(dimension, position.immutable(), expiresAt));
    }

    /** Adds one highlight without clearing the others. */
    public static void add(ResourceLocation dimension, BlockPos position) {
        HIGHLIGHTS.removeIf(h -> h.dimension.equals(dimension) && h.position.equals(position));
        HIGHLIGHTS.add(new Highlight(dimension, position.immutable(), Util.getMillis() + DURATION_MS));
    }

    public static void clear() {
        HIGHLIGHTS.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || HIGHLIGHTS.isEmpty()) return;
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) {
            HIGHLIGHTS.clear();
            return;
        }
        long now = Util.getMillis();
        HIGHLIGHTS.removeIf(h -> h.expiresAt <= now);
        if (HIGHLIGHTS.isEmpty()) return;

        ResourceLocation dimension = level.dimension().location();
        var camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        // Keep every box inside the projection's far plane: a box farther than this is drawn scaled toward the
        // camera along the same ray, which looks identical on screen, so highlights are visible map-wide.
        double maxDistance = Math.max(16.0, minecraft.gameRenderer.getDepthFar() * 0.5);

        // Build the camera matrices ourselves instead of trusting the stage's pose/model-view state, which other
        // renderers (Embeddium, Flywheel, Oculus) may have left in a different space by the end of the frame.
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        pose.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));

        var consumer = BUFFERS.getBuffer(HighlightRenderType.SEE_THROUGH_LINES);
        for (var highlight : HIGHLIGHTS) {
            if (!highlight.dimension.equals(dimension)) continue;
            double distance = Vec3.atCenterOf(highlight.position).distanceTo(cameraPos);
            float scale = (float) (distance > maxDistance ? maxDistance / distance : 1.0);
            pose.pushPose();
            pose.scale(scale, scale, scale);
            AABB box = new AABB(highlight.position).move(-cameraPos.x, -cameraPos.y, -cameraPos.z).inflate(0.004);
            LevelRenderer.renderLineBox(pose, consumer, box, 1.0F, 0.85F, 0.1F, 1.0F);
            pose.popPose();
        }

        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousSorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        // The lines shader applies fog; push it out so distant boxes stay fully bright.
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        try {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
            // In 1.20.1 NO_DEPTH_TEST is a no-op: it does not disable a depth test left enabled by world rendering.
            RenderSystem.disableDepthTest();
            BUFFERS.endBatch(HighlightRenderType.SEE_THROUGH_LINES);
        } finally {
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** Subclass only to reach RenderType's protected render-state shards. */
    private static final class HighlightRenderType extends RenderType {
        static final RenderType SEE_THROUGH_LINES = create("neoecoae_see_through_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_LINES_SHADER)
                .setLineState(new LineStateShard(OptionalDouble.of(3.0)))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setWriteMaskState(COLOR_WRITE)
                .setCullState(NO_CULL)
                .setDepthTestState(NO_DEPTH_TEST)
                .createCompositeState(false));

        private HighlightRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                    boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
        }
    }
}
