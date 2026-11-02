package cn.dancingsnow.neoecoae.client.model;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 26.1 port: {@link WorkstationModuleBakedModel} is now a {@code DelegateBlockStateModel} that wraps the formed
 * workstation block-state models and emits a standalone module model part; the old quad-rotation helper is gone
 * (rotation now happens at bake time through {@code BlockModelRotation}).
 */
class WorkstationModuleBakedModelTest {
    private static Block workstation;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        Identifier id = NeoECOAE.id("integrated_working_station");
        var existing = BuiltInRegistries.BLOCK.getOptional(id);
        if (existing.isPresent()) {
            workstation = existing.get();
            return;
        }
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        registry.unfreeze(false);
        try {
            workstation = new ECOIntegratedWorkingStation(BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, id)));
            Registry.register(registry, id, workstation);
        } finally {
            registry.freeze();
        }
    }

    @Test
    void wrapsOnlyFormedWorkstationStates() {
        BlockState formed = workstation.defaultBlockState().setValue(ECOIntegratedWorkingStation.FORMED, true);
        BlockState small = workstation.defaultBlockState().setValue(ECOIntegratedWorkingStation.FORMED, false);
        BlockState stone = Blocks.STONE.defaultBlockState();
        var original = mock(BlockStateModel.class);
        var alreadyWrapped = new WorkstationModuleBakedModel(original);
        BlockState formedWorking = formed.setValue(ECOIntegratedWorkingStation.WORKING, true);

        Map<BlockState, BlockStateModel> models = new HashMap<>();
        models.put(formed, original);
        models.put(small, original);
        models.put(stone, original);
        models.put(formedWorking, alreadyWrapped);
        var result = mock(ModelBakery.BakingResult.class);
        when(result.blockStateModels()).thenReturn(models);
        var event = mock(ModelEvent.ModifyBakingResult.class);
        when(event.getBakingResult()).thenReturn(result);

        WorkstationModuleBakedModel.wrapModels(event);

        assertInstanceOf(WorkstationModuleBakedModel.class, models.get(formed));
        assertSame(original, models.get(small));
        assertSame(original, models.get(stone));
        assertSame(alreadyWrapped, models.get(formedWorking), "an already wrapped model must not be wrapped again");
    }

    @Test
    void fallsBackToTheOriginalGeometryWithoutAModuleHost() {
        var original = mock(BlockStateModel.class);
        var wrapped = new WorkstationModuleBakedModel(original);
        var level = mock(BlockAndTintGetter.class);
        BlockState state = workstation.defaultBlockState().setValue(ECOIntegratedWorkingStation.FORMED, true);
        var random = RandomSource.create(0);
        var parts = new ArrayList<BlockStateModelPart>();

        wrapped.collectParts(level, BlockPos.ZERO, state, random, parts);

        verify(original).collectParts(level, BlockPos.ZERO, state, random, parts);
    }
}
