package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LongFluidTank;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FluidHatchNetworkPullTest {
    private LongFluidTank tank;
    private MEStorage network;
    private IEnergySource energy;
    private IActionSource source;
    private AEFluidKey water;

    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @BeforeEach
    void setup() {
        tank = new LongFluidTank();
        network = mock(MEStorage.class);
        energy = mock(IEnergySource.class);
        source = mock(IActionSource.class);
        water = AEFluidKey.of(Fluids.WATER);
        when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(network.extract(any(), anyLong(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(network.insert(any(), anyLong(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    void stocksBeyondBothSixteenBucketsAndIntRangeAndOnlyRefillsDeficit() {
        long target = 5_000_000_000L;
        sync(water, target);
        assertEquals(target, tank.getStoredAmount());
        assertEquals(Integer.MAX_VALUE, tank.getFluidAmount());
        clearInvocations(network, energy);
        sync(water, target);
        verifyNoInteractions(network, energy);
        tank.drain(1000, FluidAction.EXECUTE);
        sync(water, target);
        verify(network).extract(water, 1000, Actionable.MODULATE, source);
        assertEquals(target, tank.getStoredAmount());
    }

    @Test
    void returnsExcessWhenTargetDecreases() {
        tank.add(water.toStack(1), 50_000);
        sync(water, 10_000);
        assertEquals(10_000, tank.getStoredAmount());
        verify(network).insert(water, 40_000, Actionable.MODULATE, source);
        verify(network, never()).extract(any(), anyLong(), any(), any());
    }

    @Test
    void rejectsInvalidNegativeTargetsWithoutChangingStockOrCallingTheNetwork() {
        tank.add(water.toStack(1), Long.MAX_VALUE);
        sync(water, -1);
        assertEquals(Long.MAX_VALUE, tank.getStoredAmount());
        verifyNoInteractions(network, energy);
    }

    @Test
    void switchingFluidReturnsOldStockBeforePullingNewFluid() {
        tank.add(water.toStack(1), 30_000);
        var lava = AEFluidKey.of(Fluids.LAVA);
        sync(lava, 20_000);
        assertEquals(20_000, tank.getStoredAmount());
        assertTrue(FluidStack.isSameFluidSameComponents(tank.getFluid(), lava.toStack(1)));
        var order = inOrder(network);
        order.verify(network).insert(water, 30_000, Actionable.MODULATE, source);
        order.verify(network).extract(lava, 20_000, Actionable.SIMULATE, source);
    }

    @Test
    void rejectedOrPartialReturnsNeverVoidFluidOrMixTypes() {
        tank.add(water.toStack(1), 30_000);
        when(network.insert(any(), anyLong(), any(), any())).thenReturn(4000L, 0L);
        sync(AEFluidKey.of(Fluids.LAVA), 20_000);
        assertEquals(26_000, tank.getStoredAmount());
        sync(null, 20_000);
        assertEquals(26_000, tank.getStoredAmount());
        verify(network, never()).extract(any(), anyLong(), any(), any());
    }

    @Test
    void clearingMarkOrZeroTargetReturnsAllStock() {
        tank.add(water.toStack(1), 70_000);
        sync(null, 16_000);
        assertTrue(tank.isEmpty());
        tank.add(water.toStack(1), 20_000);
        sync(water, 0);
        assertTrue(tank.isEmpty());
        verify(network, never()).extract(any(), anyLong(), any(), any());
    }

    @Test
    void absentPowerOrStockDoesNotCreateFluid() {
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG))).thenReturn(0.0);
        sync(water, 80_000);
        assertTrue(tank.isEmpty());
        verify(network, never()).extract(any(), anyLong(), eq(Actionable.MODULATE), any());
        when(network.extract(any(), anyLong(), any(), any())).thenReturn(0L);
        sync(water, 80_000);
        assertTrue(tank.isEmpty());
    }

    @Test
    void partialExtractionAndLongMaxTargetRemainExact() {
        when(network.extract(any(), anyLong(), any(), any())).thenReturn(12_345L);
        sync(water, Long.MAX_VALUE);
        assertEquals(12_345, tank.getStoredAmount());
        tank.add(water.toStack(1), Long.MAX_VALUE - 12_345);
        assertEquals(Long.MAX_VALUE, tank.getStoredAmount());
        assertEquals(0, tank.fill(water.toStack(1), FluidAction.EXECUTE));
        tank.drain(Integer.MAX_VALUE, FluidAction.SIMULATE);
        assertEquals(Long.MAX_VALUE, tank.getStoredAmount());
        tank.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
        assertEquals(Long.MAX_VALUE - Integer.MAX_VALUE, tank.getStoredAmount());
    }

    @Test
    void longBufferPersistsAndLegacyTankMigratesWithoutLoss() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        tank.add(water.toStack(1), 9_000_000_000L);
        var saved = tank.writeToNBT(registries, new CompoundTag());
        var loaded = new LongFluidTank();
        loaded.readFromNBT(registries, saved);
        assertEquals(9_000_000_000L, loaded.getStoredAmount());
        assertEquals(Integer.MAX_VALUE, loaded.getFluidAmount());
        var legacy = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(16000);
        legacy.fill(water.toStack(7500), FluidAction.EXECUTE);
        loaded.readFromNBT(registries, legacy.writeToNBT(registries, new CompoundTag()));
        assertEquals(7500, loaded.getStoredAmount());
        assertEquals(7500, loaded.getFluidAmount());
    }

    @Test
    void ticksOnlyPullEverySecondWithAnInstalledCardAndAnActiveNode() {
        var hatch = mock(ECOFluidInputHatchBlockEntity.class, CALLS_REAL_METHODS);
        hatch.tank = tank;
        doNothing().when(hatch).saveChanges();
        doReturn(true).when(hatch).hasNetworkPullCard();
        hatch.setMarkedFluid(water.toStack(1000));
        hatch.setTargetAmount(90_000);
        var node = mock(IManagedGridNode.class);
        doReturn(node).when(hatch).getMainNode();
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(node.getGrid()).thenReturn(grid);
        when(node.isActive()).thenReturn(true);
        when(grid.getStorageService().getInventory()).thenReturn(network);
        when(grid.getEnergyService()).thenReturn(mock(appeng.api.networking.energy.IEnergyService.class));
        // A powered network grants exactly the extraction energy requested by StorageHelper.
        when(grid.getEnergyService().extractAEPower(anyDouble(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        var level = mock(Level.class);
        var state = Blocks.AIR.defaultBlockState();
        when(level.getGameTime()).thenReturn(1L, 19L);
        hatch.tick(level, BlockPos.ZERO, state);
        hatch.tick(level, BlockPos.ZERO, state);
        assertTrue(tank.isEmpty());
        when(level.getGameTime()).thenReturn(20L);
        hatch.tick(level, BlockPos.ZERO, state);
        assertEquals(90_000, tank.getStoredAmount());
        tank.drain(1000, FluidAction.EXECUTE);
        clearInvocations(network);
        when(node.isActive()).thenReturn(false);
        when(level.getGameTime()).thenReturn(40L);
        hatch.tick(level, BlockPos.ZERO, state);
        assertEquals(89_000, tank.getStoredAmount());
        when(node.isActive()).thenReturn(true);
        doReturn(false).when(hatch).hasNetworkPullCard();
        hatch.tick(level, BlockPos.ZERO, state);
        verifyNoInteractions(network);
        assertEquals(89_000, tank.getStoredAmount());
    }

    private void sync(AEFluidKey key, long target) {
        FluidHatchNetworkPull.sync(tank, key, target, network, energy, source);
    }
}
