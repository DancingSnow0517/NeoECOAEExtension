package cn.dancingsnow.neoecoae.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.QuadTransformers;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.lighting.FlatQuadLighter;
import net.minecraftforge.client.model.lighting.QuadLighter;
import net.minecraftforge.client.model.lighting.SmoothQuadLighter;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;
import java.util.Map;

/** Applies world lighting to the transformed models baked into a section by fixed renderers. */
final class FixedModelLighting implements MultiBufferSource, AutoCloseable {
    private static final ThreadLocal<Lighters> LIGHTERS = ThreadLocal.withInitial(
        () -> new Lighters(Minecraft.getInstance().getBlockColors()));

    private final MultiBufferSource delegate;
    private final Matrix4f inverseBlockPose;
    private final PoseStack.Pose blockPose;
    private final BlockAndTintGetter level;
    private final BlockPos pos;
    private final BlockState state;
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
        var snapshot=new PoseStack();snapshot.last().pose().set(blockPose.pose());snapshot.last().normal().set(blockPose.normal());
        this.blockPose=snapshot.last();
        this.level = level;
        this.pos = pos.immutable();
        this.state = state;
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
                lightingQuad = new BakedQuad(lightingQuad.getVertices(), lightingQuad.getTintIndex(),
                    FaceBakery.calculateFacing(lightingQuad.getVertices()), lightingQuad.getSprite(),
                    lightingQuad.isShade(), lightingQuad.hasAmbientOcclusion());
                for (var part : BlockGridQuads.split(lightingQuad, brightness)) {
                    BlockPos samplePos = pos.offset(part.origin());
                    BlockState sampleState = part.origin().equals(BlockPos.ZERO) ? state : level.getBlockState(samplePos);
                    QuadLighter lighter = ambientOcclusion && quad.hasAmbientOcclusion() ? lighters.smooth : lighters.flat;
                    lighter.setup(level, samplePos, sampleState);
                    int[] localVertices = part.quad().getVertices().clone();
                    for (int vertex = 0; vertex < 4; vertex++) {
                        int offset = vertex * IQuadTransformer.STRIDE;
                        localVertices[offset] = Float.floatToRawIntBits(Float.intBitsToFloat(localVertices[offset]) - part.origin().getX());
                        localVertices[offset + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(localVertices[offset + 1]) - part.origin().getY());
                        localVertices[offset + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(localVertices[offset + 2]) - part.origin().getZ());
                    }
                    var localQuad=new BakedQuad(localVertices,-1,part.quad().getDirection(),part.quad().getSprite(),quad.isShade(),quad.hasAmbientOcclusion());
                    lighter.process(new VertexConsumerWrapper(parent){
                        @Override public void putBulkData(PoseStack.Pose samplePose,BakedQuad sample,float[] computed,float tintR,float tintG,float tintB,int[] lights,int overlay,boolean baked){
                            for(int vertex=0;vertex<4;vertex++)shadedBrightness[vertex]=part.brightness()[vertex]*computed[vertex];
                            parent.putBulkData(FixedModelLighting.this.blockPose,part.quad(),shadedBrightness,red,green,blue,alpha,lights,packedOverlay,readAlpha);
                        }
                    },blockPose,localQuad,packedOverlay);
                }
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
