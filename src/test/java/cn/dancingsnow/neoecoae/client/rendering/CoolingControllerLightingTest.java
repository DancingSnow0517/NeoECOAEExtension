package cn.dancingsnow.neoecoae.client.rendering;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.lighting.QuadLighter;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;
import net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoolingControllerLightingTest {
    private static final List<BlockModelRotation> ROTATIONS = List.of(BlockModelRotation.X0_Y0,
        BlockModelRotation.X0_Y90, BlockModelRotation.X0_Y180, BlockModelRotation.X0_Y270);

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void exposedTopDoesNotSampleAnInternalDriveInAnyFacingOrMirror() throws Exception {
        for (boolean mirrored : List.of(false, true)) {
            for (var rotation : ROTATIONS) {
                BakedQuad top = bodyFace(mirrored, rotation, Direction.UP);
                var result = render(top, region(pos -> pos.getY() == 1 && (pos.getX() != 0 || pos.getZ() != 0)), true, 0, 0, 0);
                assertEquals(2, result.size(), "The two-block top must have a sample origin for each block");
                for (var rendered : result) {
                    for (float brightness : rendered.brightness) assertEquals(1F, brightness, 0.0001F,
                        mirrored + " " + rotation + " must sample the open sky above y=2");
                    for (int light : rendered.lightmap) assertEquals(15, LightTexture.sky(light));
                }
            }
        }
    }

    @Test
    void aRealObstructionAboveTheTopStillProducesAmbientOcclusion() throws Exception {
        BakedQuad top = bodyFace(false, BlockModelRotation.X0_Y0, Direction.UP);
        var region = region(pos -> pos.getY() == 2 && pos.getX() == 1);
        var smooth = render(top, region, true, 0, 0, 0);
        assertTrue(smooth.stream().anyMatch(quad -> {
            for (float value : quad.brightness) if (value < 0.99F) return true;
            return false;
        }), "Correct sampling must retain shadows from actual external blocks");
        for (var rendered : render(top, region, false, 0, 0, 0)) {
            for (float brightness : rendered.brightness) assertEquals(1F, brightness, 0.0001F);
        }
    }

    @Test
    void eachHeightOfTheBodyReceivesItsOwnLocalLightInAllFacings() throws Exception {
        BlockAndTintGetter region = region(pos -> false);
        when(region.getBrightness(eq(LightLayer.SKY), any())).thenReturn(0);
        when(region.getBrightness(eq(LightLayer.BLOCK), any())).thenAnswer(inv ->
            inv.<BlockPos>getArgument(1).getY() >= 1 ? 15 : 0);
        for (boolean mirrored : List.of(false, true)) {
            for (var rotation : ROTATIONS) {
                var rendered = render(bodyFace(mirrored, rotation, Direction.WEST), region, false, 0, 0, 0);
                assertEquals(6, rendered.size());
                for (var piece : rendered) {
                    float centerY = 0;
                    for (int vertex = 0; vertex < 4; vertex++) centerY += coordinate(piece.quad, vertex, 1) / 4;
                    int expected = centerY >= 1 ? 15 : 0;
                    for (int light : piece.lightmap) assertEquals(expected, LightTexture.block(light),
                        mirrored + " " + rotation + " at y=" + centerY);
                }
            }
        }
    }

    @Test
    void sectionTranslationDoesNotAffectEitherGeometryOrLightSamples() throws Exception {
        BakedQuad body = bodyFace(true, BlockModelRotation.X0_Y90, Direction.WEST);
        BlockAndTintGetter region = region(pos -> pos.getY() == 2);
        var atOrigin = render(body, region, true, 0, 0, 0);
        var inSection = render(body, region, true, 11, 7, 13);
        assertEquals(atOrigin.size(), inSection.size());
        for (int index = 0; index < atOrigin.size(); index++) {
            var first = atOrigin.get(index);
            var second = inSection.get(index);
            assertArrayEquals(first.lightmap, second.lightmap);
            assertArrayEquals(first.brightness, second.brightness, 0.0001F);
            for (int vertex = 0; vertex < 4; vertex++) {
                Vector3f a = first.pose.transformPosition(position(first.quad, vertex));
                Vector3f b = second.pose.transformPosition(position(second.quad, vertex));
                assertEquals(11, b.x - a.x, 0.0001F);
                assertEquals(7, b.y - a.y, 0.0001F);
                assertEquals(13, b.z - a.z, 0.0001F);
            }
        }
    }

    @Test
    void subdivisionsPreserveAreaTextureSeamsAndEmissiveData() throws Exception {
        FaceBakery bakery = new FaceBakery();
        for (boolean mirrored : List.of(false, true)) {
            BlockModel model = model(mirrored);
            for (var rotation : ROTATIONS) {
                for (var element : model.getElements()) {
                    for (var entry : element.faces.entrySet()) {
                        BakedQuad quad = bakery.bakeQuad(element.from, element.to, entry.getValue(),
                            UnitTextureAtlasSprite.INSTANCE, entry.getKey(), rotation, element.rotation, element.shade);
                        int[] original = quad.getVertices().clone();
                        List<BlockGridQuads.Part> parts = BlockGridQuads.split(quad, new float[]{1, 1, 1, 1});
                        assertEquals(area(quad), parts.stream().mapToDouble(part -> area(part.quad())).sum(), 0.0001);
                        for (var part : parts) {
                            assertEquals(quad.getTintIndex(), part.quad().getTintIndex());
                            assertEquals(quad.hasAmbientOcclusion(), part.quad().hasAmbientOcclusion());
                            assertEquals(quad.isShade(), part.quad().isShade());
                            assertSame(quad.getSprite(), part.quad().getSprite());
                            for (int vertex = 0; vertex < 4; vertex++) {
                                Vector3f local = position(part.quad(), vertex).sub(part.origin().getX(),
                                    part.origin().getY(), part.origin().getZ());
                                assertTrue(local.x >= -0.0001 && local.x <= 1.0001);
                                assertTrue(local.y >= -0.0001 && local.y <= 1.0001);
                                assertTrue(local.z >= -0.0001 && local.z <= 1.0001);
                                assertEquals(quad.getVertices()[IQuadTransformer.UV2],
                                    part.quad().getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2]);
                            }
                        }
                        // Every shared position must retain exactly the same UV, including mirrored UVs.
                        var allVertices = new java.util.HashMap<Vector3f, Vector3f>();
                        for (var part : parts) {
                            for (int vertex = 0; vertex < 4; vertex++) {
                                int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
                                Vector3f uv = new Vector3f(Float.intBitsToFloat(part.quad().getVertices()[offset]),
                                    Float.intBitsToFloat(part.quad().getVertices()[offset + 1]), 0);
                                Vector3f previous = allVertices.putIfAbsent(position(part.quad(), vertex), uv);
                                if (previous != null) assertTrue(previous.distance(uv) < 0.0001F);
                            }
                        }
                        assertArrayEquals(original, quad.getVertices(), "Shared resource models must remain immutable");
                    }
                }
            }
        }
    }

    private static BakedQuad bodyFace(boolean mirrored, BlockModelRotation rotation, Direction face) throws Exception {
        var body = model(mirrored).getElements().getLast();
        return new FaceBakery().bakeQuad(body.from, body.to, body.faces.get(face),
            UnitTextureAtlasSprite.INSTANCE, face, rotation, body.rotation, body.shade);
    }

    private static BlockModel model(boolean mirrored) throws Exception {
        return BlockModel.fromString(Files.readString(Path.of("src/main/resources/assets/neoecoae/models/block/"
            + "computation_cooling_controller/controller_formed_base" + (mirrored ? "_mirrored" : "") + ".json")));
    }

    private static List<RenderedQuad> render(BakedQuad quad, BlockAndTintGetter region, boolean ao,
                                              int sectionX, int sectionY, int sectionZ) {
        List<RenderedQuad> result = new ArrayList<>();
        VertexConsumer output = new VertexConsumerWrapper(mock(VertexConsumer.class)) {
            @Override
            public void putBulkData(PoseStack.Pose pose, BakedQuad data, float[] brightness, float red, float green,
                                    float blue, float alpha, int[] lightmap, int overlay, boolean readAlpha) {
                result.add(new RenderedQuad(data, new Matrix4f(pose.pose()), brightness.clone(), lightmap.clone()));
            }
        };
        PoseStack pose = new PoseStack();
        pose.translate(sectionX, sectionY, sectionZ);
        try (var lighting = new FixedModelLighting(region, BlockPos.ZERO, Blocks.GLASS.defaultBlockState(),
            pose.last(), layer -> output, new FixedModelLighting.Lighters(new BlockColors()), ao)) {
            lighting.getBuffer(RenderType.cutout()).putBulkData(pose.last(), quad, 1, 1, 1, 1, 0, 0);
        }
        return result;
    }

    private static BlockAndTintGetter region(Predicate<BlockPos> solid) {
        BlockAndTintGetter region = mock(BlockAndTintGetter.class);
        when(region.getBlockState(any())).thenAnswer(inv -> solid.test(inv.getArgument(0))
            ? Blocks.IRON_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
        when(region.getBrightness(eq(LightLayer.SKY), any())).thenReturn(15);
        when(region.getShade(anyFloat(), anyFloat(), anyFloat(), anyBoolean())).thenAnswer(inv ->
            inv.<Boolean>getArgument(3) ? QuadLighter.calculateShade(inv.getArgument(0),
                inv.getArgument(1), inv.getArgument(2), false) : 1F);
        return region;
    }

    private static float coordinate(BakedQuad quad, int vertex, int axis) {
        return Float.intBitsToFloat(quad.getVertices()[vertex * IQuadTransformer.STRIDE + axis]);
    }

    private static Vector3f position(BakedQuad quad, int vertex) {
        return new Vector3f(coordinate(quad, vertex, 0), coordinate(quad, vertex, 1), coordinate(quad, vertex, 2));
    }

    private static float area(BakedQuad quad) {
        Vector3f a = position(quad, 1).sub(position(quad, 0));
        Vector3f b = position(quad, 3).sub(position(quad, 0));
        return a.cross(b).length();
    }

    private record RenderedQuad(BakedQuad quad, Matrix4f pose, float[] brightness, int[] lightmap) {}
}
