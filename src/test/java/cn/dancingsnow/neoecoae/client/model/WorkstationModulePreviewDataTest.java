package cn.dancingsnow.neoecoae.client.model;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkstationModulePreviewDataTest {
    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test void addonPreviewReachesTheModelWithoutReadingOrChangingAController() {
        var original = mock(BakedModel.class);
        var world = mock(BlockAndTintGetter.class);
        var state = mock(BlockState.class);
        var extra = new ModelProperty<String>();
        var input = WorkstationModuleBakedModel.previewData(ResourceLocation.parse("addon:workstation"))
            .derive().with(extra, "preserved").build();
        when(original.getModelData(world, BlockPos.ZERO, state, input)).thenReturn(input);

        var output = new WorkstationModuleBakedModel(original).getModelData(world, BlockPos.ZERO, state, input);
        assertEquals("preserved", output.get(extra));
        assertEquals(3, output.getProperties().size(), "Preview identity must be projected onto the model's module property");
        verifyNoInteractions(world);
    }

    @Test void realWorldStillReadsControllerAppearanceOnEveryRefresh() {
        var original = mock(BakedModel.class);
        var world = mock(BlockAndTintGetter.class);
        var state = mock(BlockState.class);
        when(original.getModelData(world, BlockPos.ZERO, state, ModelData.EMPTY)).thenReturn(ModelData.EMPTY);
        var model = new WorkstationModuleBakedModel(original);
        assertSame(ModelData.EMPTY, model.getModelData(world, BlockPos.ZERO, state, ModelData.EMPTY));
        assertSame(ModelData.EMPTY, model.getModelData(world, BlockPos.ZERO, state, ModelData.EMPTY));
        verify(world, times(2)).getBlockEntity(BlockPos.ZERO);
    }
}
