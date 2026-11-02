package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationCoolingAppearanceTest {
    private static ECOIntegratedWorkingStation workstation;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        registry.unfreeze();
        try {
            workstation = new ECOIntegratedWorkingStation(BlockBehaviour.Properties.of());
            Registry.register(registry, "neoecoae_cooling_appearance_test:workstation", workstation);
            workstation.getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
        } finally {
            registry.freeze();
        }
    }

    @Test
    void changingTheInstalledHatchSwitchesAppearanceWithoutChangingTheModuleOrFacing() throws Exception {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var fixture = fixture(facing, true);
            fixture.host().updateState(false);
            assertTrue(fixture.state().get().getValue(ECOIntegratedWorkingStation.SMART_COOLING));

            when(fixture.hatch().isSmartCoolingModule()).thenReturn(false);
            fixture.host().updateState(false);
            assertFalse(fixture.state().get().getValue(ECOIntegratedWorkingStation.SMART_COOLING));
            assertEquals(facing, fixture.state().get().getValue(ECOIntegratedWorkingStation.FACING));
            assertEquals(LargeWorkstationModule.ADVANCED_AE,
                fixture.state().get().getValue(ECOIntegratedWorkingStation.MODULE));
            assertTrue(fixture.state().get().getValue(ECOIntegratedWorkingStation.FORMED));
            verify(fixture.level(), times(2)).setBlock(eq(BlockPos.ZERO), any(), eq(Block.UPDATE_CLIENTS));
        }
    }

    @Test
    void disassemblyClearsTheSmartAppearanceEvenIfTheOldClusterIsStillAttached() throws Exception {
        var fixture = fixture(Direction.NORTH, true);
        fixture.host().updateState(false);
        field(fixture.host(), "formed", false);
        fixture.host().updateState(false);
        assertFalse(fixture.state().get().getValue(ECOIntegratedWorkingStation.SMART_COOLING));
        assertFalse(fixture.state().get().getValue(ECOIntegratedWorkingStation.FORMED));
    }

    @Test
    void replacingAnAlreadyFormedClusterRefreshesItsAppearance() throws Exception {
        var fixture = fixture(Direction.WEST, false);
        fixture.host().updateState(false);
        assertFalse(fixture.state().get().getValue(ECOIntegratedWorkingStation.SMART_COOLING));
        var next = mock(NEIntegratedWorkingStationCluster.class);
        when(fixture.hatch().isSmartCoolingModule()).thenReturn(true);
        when(next.getInputHatch()).thenReturn(fixture.hatch());
        fixture.host().updateCluster(next);
        assertTrue(fixture.state().get().getValue(ECOIntegratedWorkingStation.SMART_COOLING));
    }

    private static Fixture fixture(Direction facing, boolean smart) throws Exception {
        var host = mock(ECOLargeIntegratedWorkingStationBlockEntity.class, CALLS_REAL_METHODS);
        var level = mock(Level.class);
        var hatch = mock(ECOLargeIntegratedWorkingStationInputHatchBlockEntity.class);
        var cluster = mock(NEIntegratedWorkingStationCluster.class);
        var state = new AtomicReference<>(workstation.defaultBlockState()
            .setValue(ECOIntegratedWorkingStation.FACING, facing)
            .setValue(ECOIntegratedWorkingStation.FORMED, true)
            .setValue(ECOIntegratedWorkingStation.MODULE, LargeWorkstationModule.ADVANCED_AE));
        when(level.getBlockState(BlockPos.ZERO)).thenAnswer(call -> state.get());
        when(level.setBlock(eq(BlockPos.ZERO), any(), eq(Block.UPDATE_CLIENTS))).thenAnswer(call -> {
            state.set(call.getArgument(1));
            return true;
        });
        when(hatch.isSmartCoolingModule()).thenReturn(smart);
        when(cluster.getInputHatch()).thenReturn(hatch);
        doReturn(LargeWorkstationModule.ADVANCED_AE).when(host).getInstalledModule();
        doReturn(false).when(host).isWorking();
        field(host, "level", level);
        field(host, "worldPosition", BlockPos.ZERO);
        field(host, "formed", true);
        field(host, "cluster", cluster);
        return new Fixture(host, level, hatch, state);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private record Fixture(ECOLargeIntegratedWorkingStationBlockEntity host, Level level,
                           ECOLargeIntegratedWorkingStationInputHatchBlockEntity hatch,
                           AtomicReference<BlockState> state) { }
}
