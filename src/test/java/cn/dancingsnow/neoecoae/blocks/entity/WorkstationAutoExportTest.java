package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkstationAutoExportTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 375, 1000})
    void exportsFluidWithoutCrashingOrLosingRejectedContents(int accepted) throws Exception {
        var fixture = fixture();
        var tag = new CompoundTag();
        tag.putString("batch", "preserve_me");
        fixture.tank.setFluid(new FluidStack(Fluids.WATER, 1000, tag));
        var expectedKey = AEFluidKey.of(fixture.tank.getFluid());
        when(fixture.target.insert(eq(expectedKey), eq(1000L), eq(Actionable.MODULATE), any()))
            .thenReturn((long) accepted);

        assertEquals(accepted > 0, export(fixture.host));
        assertEquals(1000 - accepted, fixture.tank.getFluidAmount());
        verify(fixture.target).insert(eq(expectedKey), eq(1000L), eq(Actionable.MODULATE), any());
        if (accepted < 1000) {
            assertSame(Fluids.WATER, fixture.tank.getFluid().getFluid());
            assertEquals(tag, fixture.tank.getFluid().getTag());

            long remaining = 1000L - accepted;
            when(fixture.target.insert(eq(expectedKey), eq(remaining), eq(Actionable.MODULATE), any()))
                .thenReturn(remaining);
            assertTrue(export(fixture.host));
        }
        assertTrue(fixture.tank.isEmpty());
    }

    @Test
    void emptyOutputDoesNotAttemptInsertion() throws Exception {
        var fixture = fixture();
        assertFalse(export(fixture.host));
        verifyNoInteractions(fixture.target);
    }

    @Test
    void disabledAutoExportPreservesTheTank() throws Exception {
        var fixture = fixture();
        fixture.tank.setFluid(new FluidStack(Fluids.WATER, 1000));
        when(fixture.config.getSetting(Settings.AUTO_EXPORT)).thenReturn(YesNo.NO);
        assertFalse(export(fixture.host));
        assertEquals(1000, fixture.tank.getFluidAmount());
        verifyNoInteractions(fixture.target);
    }

    private static Fixture fixture() throws Exception {
        var host = mock(ECOIntegratedWorkingStationBlockEntity.class, CALLS_REAL_METHODS);
        var tank = new FluidTank(64000);
        var config = mock(IConfigManager.class);
        when(config.getSetting(Settings.AUTO_EXPORT)).thenReturn(YesNo.YES);
        field(host, "configManager", config);
        field(host, "outputInv", new AppEngInternalInventory(null, 1));
        field(host, "outputTank", tank);
        field(host, "allowOutputs", EnumSet.of(RelativeSide.BOTTOM));
        var orientation = mock(BlockOrientation.class);
        when(orientation.getSide(RelativeSide.BOTTOM)).thenReturn(Direction.DOWN);
        doReturn(orientation).when(host).getOrientation();
        var target = mock(MEStorage.class);
        var strategy = mock(ExternalStorageStrategy.class);
        when(strategy.createWrapper(eq(false), any())).thenReturn(target);
        var strategies = new HashMap<Direction, Map<AEKeyType, ExternalStorageStrategy>>();
        strategies.put(Direction.DOWN, Map.of(AEKeyType.fluids(), strategy));
        field(host, "exportStrategies", strategies);
        return new Fixture(host, tank, target, config);
    }

    private static boolean export(ECOIntegratedWorkingStationBlockEntity host) throws Exception {
        var method = ECOIntegratedWorkingStationBlockEntity.class.getDeclaredMethod("pushOutResult");
        method.setAccessible(true);
        return (boolean) method.invoke(host);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        var field = ECOIntegratedWorkingStationBlockEntity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record Fixture(ECOIntegratedWorkingStationBlockEntity host, FluidTank tank,
                           MEStorage target, IConfigManager config) {}
}
