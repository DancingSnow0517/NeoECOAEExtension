package cn.dancingsnow.neoecoae.client.model;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.util.TriState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoolingControllerBakedModelTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void onlyTheTwentyFourFormedBlockVariantsMoveIntoSectionRendering() {
        var models = new HashMap<ModelResourceLocation, BakedModel>();
        BakedModel original = mock(BakedModel.class);
        for (String tier : List.of("l4", "l6", "l9")) {
            var id = new ResourceLocation("neoecoae", "computation_cooling_controller_" + tier);
            models.put(ModelResourceLocation.inventory(id), original);
            for (String facing : List.of("north", "east", "south", "west")) {
                for (boolean formed : List.of(false, true)) {
                    for (boolean mirrored : List.of(false, true)) {
                        models.put(new ModelResourceLocation(id, "facing=" + facing + ",formed=" + formed + ",mirrored=" + mirrored), original);
                    }
                }
            }
        }
        var unrelated = new ModelResourceLocation(new ResourceLocation("neoecoae", "computation_system_l9"), "formed=true");
        models.put(unrelated, original);
        CoolingControllerBakedModel.onModifyBakingResult(new ModelEvent.ModifyBakingResult(models, null, null));
        assertEquals(24, models.values().stream().filter(CoolingControllerBakedModel.class::isInstance).count());
        assertSame(original, models.get(unrelated));
        for (var entry : models.entrySet()) {
            assertEquals(entry.getKey().variant().contains("formed=true") && !entry.getKey().equals(unrelated),
                entry.getValue() instanceof CoolingControllerBakedModel, entry.getKey().toString());
        }
    }

    @Test
    void modelPreviewsAndBreakingOverlaysKeepTheOriginalGeometry() {
        BakedModel original = mock(BakedModel.class);
        var quads = List.of(mock(BakedQuad.class));
        var state = Blocks.GLASS.defaultBlockState();
        var random = RandomSource.create();
        when(original.getQuads(state, null, random)).thenReturn(quads);
        when(original.getQuads(state, null, random, ModelData.EMPTY, null)).thenReturn(quads);
        var wrapped = new CoolingControllerBakedModel(original);
        assertSame(quads, wrapped.getQuads(state, null, random));
        assertSame(quads, wrapped.getQuads(state, null, random, ModelData.EMPTY, null));
        assertTrue(wrapped.getQuads(state, null, random, ModelData.EMPTY, RenderType.cutout()).isEmpty());
    }

    @Test
    void opaqueAndGlassLayersAreEachEmittedOnceAndNeverDuplicatedByTheOrdinaryBlockRenderer() {
        BakedModel original = mock(BakedModel.class);
        BakedQuad opaque = mock(BakedQuad.class);
        BakedQuad glass = mock(BakedQuad.class);
        var state = Blocks.GLASS.defaultBlockState();
        when(original.useAmbientOcclusion()).thenReturn(true);
        when(original.useAmbientOcclusion(any(), any(), any())).thenReturn(TriState.DEFAULT);
        when(original.getRenderTypes(eq(state), any(), eq(ModelData.EMPTY)))
            .thenReturn(ChunkRenderTypeSet.of(RenderType.cutout(), RenderType.translucent()));
        when(original.getQuads(eq(state), nullable(Direction.class), any(), eq(ModelData.EMPTY), any()))
            .thenAnswer(inv -> inv.getArgument(1) != null ? List.of()
                : List.of(inv.getArgument(4) == RenderType.cutout() ? opaque : glass));
        var wrapped = new CoolingControllerBakedModel(original);
        assertTrue(wrapped.getQuads(state, null, RandomSource.create()).isEmpty());
        assertTrue(wrapped.getQuads(state, null, RandomSource.create(), ModelData.EMPTY, RenderType.cutout()).isEmpty());
        VertexConsumer opaqueOutput = mock(VertexConsumer.class);
        VertexConsumer glassOutput = mock(VertexConsumer.class);
        var pose = new PoseStack();
        wrapped.renderFixed(state, pose, layer -> layer == RenderType.cutout() ? opaqueOutput : glassOutput, 123, 456);
        verify(opaqueOutput).putBulkData(pose.last(), opaque, 1F, 1F, 1F, 1F, 123, 456);
        verify(glassOutput).putBulkData(pose.last(), glass, 1F, 1F, 1F, 1F, 123, 456);
        verifyNoMoreInteractions(opaqueOutput, glassOutput);
    }
}
