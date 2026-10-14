package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationCoolingControllerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NEComputationClusterCalculatorTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true", "false, false"})
    void coolingControllersFollowTheValidatedMirrorOrResetOnFailure(boolean normalValid, boolean mirrorValid) {
        var level = mock(ServerLevel.class);
        var controller = mock(ECOComputationSystemBlockEntity.class);
        var state = mock(BlockState.class);
        var firstCooler = mock(ECOComputationCoolingControllerBlockEntity.class);
        var secondCooler = mock(ECOComputationCoolingControllerBlockEntity.class);
        var calculator = mock(NEComputationClusterCalculator.class, CALLS_REAL_METHODS);
        var min = new BlockPos(0, 0, 0);
        var max = new BlockPos(2, 0, 0);
        when(level.getBlockEntity(min)).thenReturn(firstCooler);
        when(level.getBlockEntity(max)).thenReturn(secondCooler);
        doReturn(normalValid, mirrorValid).when(calculator).verifyStructure(eq(level), eq(min), any(),
            any(), any(), any(), any(), any(), any(), any(), anyBoolean());
        doNothing().when(calculator).syncNetworkSwitchState(eq(level), eq(min), eq(state), anyBoolean());
        doNothing().when(calculator).clearNetworkSwitchState(level, min, state);
        var context = new NEClusterCalculator.ControllerContext<>(controller, min, state,
            Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST);

        assertEquals(normalValid || mirrorValid,
            calculator.verifyControllerStructure(level, min, max, context));

        boolean expectedMirror = !normalValid && mirrorValid;
        verify(controller).setMirrored(expectedMirror);
        verify(firstCooler).setMirrored(expectedMirror);
        verify(secondCooler).setMirrored(expectedMirror);
        if (normalValid || mirrorValid) {
            verify(calculator).syncNetworkSwitchState(level, min, state, expectedMirror);
            verify(calculator, never()).clearNetworkSwitchState(level, min, state);
        } else {
            verify(calculator).clearNetworkSwitchState(level, min, state);
            verify(calculator, never()).syncNetworkSwitchState(eq(level), eq(min), eq(state), anyBoolean());
        }
    }
}
