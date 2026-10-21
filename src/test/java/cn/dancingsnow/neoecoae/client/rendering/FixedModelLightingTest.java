package cn.dancingsnow.neoecoae.client.rendering;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.lighting.QuadLighter;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FixedModelLightingTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void directionalShadingFollowsTheRotatedModel() {
        BakedQuad quad = quad(Direction.NORTH, false);
        int[] original = quad.getVertices().clone();
        for (int rotation = 0; rotation < 360; rotation += 90) {
            RecordingConsumer result = render(quad, region(15, false), rotation, false, 0, 0, 0);
            float expected = rotation % 180 == 0 ? 0.8F : 0.6F;
            for (float brightness : result.brightness) assertEquals(expected, brightness, 0.001F);
            for (int light : result.lightmap) assertEquals(15, LightTexture.sky(light));
        }
        assertArrayEquals(original, quad.getVertices(), "Shared baked model vertices must remain immutable");
    }

    @Test
    void sectionTranslationDoesNotMoveTheLightSamples() {
        BakedQuad quad = quad(Direction.NORTH, true);
        BlockAndTintGetter level = region(15, true);
        RecordingConsumer atOrigin = render(quad, level, 90, true, 0, 0, 0);
        RecordingConsumer withinSection = render(quad, level, 90, true, 11, 7, 13);
        assertArrayEquals(atOrigin.lightmap, withinSection.lightmap);
        assertArrayEquals(atOrigin.brightness, withinSection.brightness, 0.0001F);
    }

    @Test
    void coveredCavityStillReceivesLightFromItsOpenSides() {
        RecordingConsumer result = render(quad(Direction.UP, true), region(15, true), 0, true, 0, 0, 0);
        for (int light : result.lightmap) assertTrue(LightTexture.sky(light) > 0);
        for (float brightness : result.brightness) assertTrue(brightness > 0 && brightness < 1);
    }

    @Test
    void globalAndPerFaceAmbientOcclusionSettingsAreRespected() {
        BlockAndTintGetter level = region(15, true);
        RecordingConsumer smooth = render(quad(Direction.UP, true), level, 0, true, 0, 0, 0);
        RecordingConsumer disabled = render(quad(Direction.UP, true), level, 0, false, 0, 0, 0);
        RecordingConsumer faceDisabled = render(quad(Direction.UP, false), level, 0, true, 0, 0, 0);
        assertArrayEquals(disabled.brightness, faceDisabled.brightness);
        assertArrayEquals(disabled.lightmap, faceDisabled.lightmap);
        assertTrue(smooth.brightness[0] < disabled.brightness[0]);
    }

    @Test
    void darkWorldIsNotReplacedByTheUniformHostLight() {
        RecordingConsumer result = render(quad(Direction.NORTH, true), region(0, false), 0, true, 0, 0, 0);
        for (int light : result.lightmap) assertEquals(0, light);
    }

    @Test
    void bakedEmissiveFacesKeepTheirOwnLightmap() {
        BakedQuad quad = quad(Direction.NORTH, false);
        for (int vertex = 0; vertex < 4; vertex++) {
            quad.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2] = LightTexture.FULL_BRIGHT;
        }
        RecordingConsumer result = render(quad, region(0, false), 180, true, 0, 0, 0);
        for (int light : result.lightmap) assertEquals(LightTexture.FULL_BRIGHT, light);
    }

    private RecordingConsumer render(BakedQuad quad, BlockAndTintGetter level, int rotation, boolean ao,
                                     int sectionX, int sectionY, int sectionZ) {
        PoseStack pose = new PoseStack();
        pose.translate(sectionX, sectionY, sectionZ);
        RecordingConsumer output = new RecordingConsumer();
        try (var lighting = new FixedModelLighting(level, BlockPos.ZERO, Blocks.GLASS.defaultBlockState(),
            pose.last(), layer -> output, new FixedModelLighting.Lighters(new BlockColors()), ao)) {
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(rotation));
            pose.translate(-0.5, -0.5, -0.5);
            lighting.getBuffer(RenderType.cutout()).putBulkData(pose.last(), quad, 1, 1, 1, LightTexture.FULL_BRIGHT, 0);
        }
        return output;
    }

    private BlockAndTintGetter region(int sky, boolean covered) {
        BlockAndTintGetter level = mock(BlockAndTintGetter.class);
        when(level.getBlockState(any())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            return covered && pos.equals(BlockPos.ZERO.above()) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        });
        when(level.getBrightness(eq(LightLayer.SKY), any())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(1);
            return covered && pos.equals(BlockPos.ZERO.above()) ? 0 : sky;
        });
        when(level.getShade(anyFloat(), anyFloat(), anyFloat(), anyBoolean())).thenAnswer(invocation ->
            invocation.<Boolean>getArgument(3) ? QuadLighter.calculateShade(invocation.getArgument(0),
                invocation.getArgument(1), invocation.getArgument(2), false) : 1F);
        return level;
    }

    private BakedQuad quad(Direction direction, boolean ao) {
        var result=new java.util.concurrent.atomic.AtomicReference<BakedQuad>();
        QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer(result::set);
        builder.setDirection(direction);
        builder.setShade(true);
        builder.setHasAmbientOcclusion(ao);
        float[][] positions = direction == Direction.UP
            ? new float[][]{{0.2F, 0.2F, 0.2F}, {0.2F, 0.2F, 0.8F}, {0.8F, 0.2F, 0.8F}, {0.8F, 0.2F, 0.2F}}
            : new float[][]{{0.2F, 0.2F, 0}, {0.2F, 0.8F, 0}, {0.8F, 0.8F, 0}, {0.8F, 0.2F, 0}};
        for (float[] position : positions) {
            builder.vertex(position[0], position[1], position[2]).color(255, 255, 255, 255)
                .normal(direction.getStepX(), direction.getStepY(), direction.getStepZ()).endVertex();
        }
        return result.get();
    }

    private static class RecordingConsumer extends VertexConsumerWrapper {
        private float[] brightness;
        private int[] lightmap;

        RecordingConsumer() {
            super(mock(VertexConsumer.class));
        }

        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float red, float green,
                                float blue, float alpha, int[] lightmap, int packedOverlay, boolean readAlpha) {
            this.brightness = brightness.clone();
            this.lightmap = lightmap.clone();
        }
    }
}
