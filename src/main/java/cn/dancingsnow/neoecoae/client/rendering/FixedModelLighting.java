package cn.dancingsnow.neoecoae.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.lighting.FlatQuadLighter;
import net.neoforged.neoforge.client.model.lighting.QuadLighter;
import net.neoforged.neoforge.client.model.lighting.SmoothQuadLighter;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;
import java.util.Map;

/** Applies world lighting to the transformed models baked into a section by fixed renderers. */
final class FixedModelLighting implements MultiBufferSource, AutoCloseable {
    private static final ThreadLocal<Lighters> LIGHTERS = ThreadLocal.withInitial(
        () -> new Lighters(Minecraft.getInstance().getBlockColors()));

    private final MultiBufferSource delegate;
    private final Matrix4f inverseBlockPose;
    private final Lighters lighters;
    private final boolean ambientOcclusion;
    private final Map<RenderType, VertexConsumer> buffers = new IdentityHashMap<>();

    FixedModelLighting(BlockAndTintGetter level, BlockPos pos, BlockState state, PoseStack.Pose blockPose,
                       MultiBufferSource delegate) {
        this(level, pos, state, blockPose, delegate, LIGHTERS.get(), Minecraft.useAmbientOcclusion());
    }

    FixedModelLighting(BlockAndTintGetter level, BlockPos pos, BlockState state, PoseStack.Pose blockPose,
                       MultiBufferSource delegate, Lighters lighters, boolean ambientOcclusion) {
        this.delegate = delegate;
        this.inverseBlockPose = new Matrix4f(blockPose.pose()).invert();
        this.lighters = lighters;
        this.ambientOcclusion = ambientOcclusion;
        lighters.smooth.setup(level, pos, state);
        lighters.flat.setup(level, pos, state);
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return buffers.computeIfAbsent(renderType, layer -> new VertexConsumerWrapper(delegate.getBuffer(layer)) {
            private final float[] shadedBrightness = new float[4];

            @Override
            public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
                                    float red, float green, float blue, float alpha, int[] lightmap,
                                    int packedOverlay, boolean readAlpha) {
                // Lighting needs block-local positions and world-facing normals. The draw pose also
                // contains the section translation, which must not influence the light samples.
                Matrix4f modelTransform = new Matrix4f(inverseBlockPose).mul(pose.pose());
                BakedQuad lightingQuad = new BakedQuad(quad.getVertices().clone(), quad.getTintIndex(),
                    quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
                QuadTransformers.applying(new Transformation(modelTransform)).processInPlace(lightingQuad);
                QuadLighter lighter = ambientOcclusion && quad.hasAmbientOcclusion() ? lighters.smooth : lighters.flat;
                lighter.computeLightingForQuad(lightingQuad);
                for (int vertex = 0; vertex < 4; vertex++) {
                    shadedBrightness[vertex] = brightness[vertex] * lighter.getComputedBrightness()[vertex];
                }
                // Draw the original quad so geometry is transformed only once. The lighter also
                // preserves baked emissive lightmaps instead of replacing them with host light.
                parent.putBulkData(pose, quad, shadedBrightness, red, green, blue, alpha,
                    lighter.getComputedLightmap(), packedOverlay, readAlpha);
            }
        });
    }

    @Override
    public void close() {
        // Section worker threads outlive their worlds; do not retain the render region in a cache.
        lighters.smooth.reset();
        lighters.flat.reset();
    }

    static final class Lighters {
        private final QuadLighter smooth;
        private final QuadLighter flat;

        Lighters(BlockColors colors) {
            smooth = new SmoothQuadLighter(colors);
            flat = new FlatQuadLighter(colors);
        }
    }
}
