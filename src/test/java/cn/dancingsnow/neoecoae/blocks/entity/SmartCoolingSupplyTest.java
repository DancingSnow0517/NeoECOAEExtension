package cn.dancingsnow.neoecoae.blocks.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.storage.StorageTestContext;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual tank, storage engine, tick method and AE2 power helper; only the surrounding grid/world are supplied. */
class SmartCoolingSupplyTest {
    @BeforeAll static void bootstrap() throws Exception { StorageTestContext.bootstrap(); }

    @Test void thresholdUsesLocalStockAndPreservesPartialRefillsAcrossDisconnects() throws Exception {
        Fixture f = new Fixture();
        var water = AEFluidKey.of(Fluids.WATER);
        f.engine.insert(water, 4000, Actionable.MODULATE);
        f.tank.setFluid(new FluidStack(Fluids.WATER, 15000));
        f.tick(0);
        assertEquals(0, f.extractions); assertEquals(15000, f.tank.getFluidAmount());
        f.tank.drain(5000, IFluidHandler.FluidAction.EXECUTE);
        f.tick(200);
        assertEquals(14000, f.tank.getFluidAmount()); assertEquals(1, f.extractions);
        assertEquals(0, f.engine.getAmount(water).toLongSaturated());
        f.tick(400); assertEquals(1, f.extractions);
        f.tank.drain(3000, IFluidHandler.FluidAction.EXECUTE);
        when(f.node.getGrid()).thenReturn(null);
        f.tick(600); assertEquals(11000, f.tank.getFluidAmount());
        when(f.node.getGrid()).thenReturn(f.grid);
        f.engine.insert(water, 10000, Actionable.MODULATE);
        f.tick(800);
        assertEquals(16000, f.tank.getFluidAmount()); assertEquals(2, f.extractions);
        assertEquals(5000, f.engine.getAmount(water).toLongSaturated());
        assertEquals(9000.0 / water.getAmountPerOperation(), f.paidPower);
    }

    @Test void changingTheMarkedFluidReturnsOldStockBeforeAnyNewExtraction() throws Exception {
        Fixture f = new Fixture();
        f.field("markedCoolant", new FluidStack(Fluids.LAVA, 1));
        f.tank.setFluid(new FluidStack(Fluids.WATER, 8000));
        f.engine.insert(AEFluidKey.of(Fluids.LAVA), 10000, Actionable.MODULATE);
        f.allowReturns = false;
        f.tick(0); assertEquals(8000, f.tank.getFluidAmount()); assertEquals(0, f.extractions);
        f.allowReturns = true;
        f.tick(200);
        assertEquals(Fluids.LAVA, f.tank.getFluid().getFluid()); assertEquals(10000, f.tank.getFluidAmount());
        assertEquals(8000, f.engine.getAmount(AEFluidKey.of(Fluids.WATER)).toLongSaturated());
        assertEquals(1, f.extractions);
    }

    private static final class Fixture {
        final SavedDataInfiniteStorageEngine engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        final ECOLargeIntegratedWorkingStationInputHatchBlockEntity hatch = mock(ECOLargeIntegratedWorkingStationInputHatchBlockEntity.class);
        final FluidTank tank = new FluidTank(16000);
        final IManagedGridNode node = mock(IManagedGridNode.class);
        final IGrid grid = mock(IGrid.class);
        final Level world = mock(Level.class);
        boolean allowReturns = true;
        int extractions;
        int simulations;
        double paidPower;
        long currentTick;

        Fixture() throws Exception {
            field("tank", tank); field("markedCoolant", new FluidStack(Fluids.WATER, 1));
            var cluster = mock(NEIntegratedWorkingStationCluster.class);
            var controller = mock(ECOLargeIntegratedWorkingStationBlockEntity.class);
            when(controller.isActiveCooling()).thenReturn(true);
            when(cluster.getController()).thenReturn(controller);
            when(hatch.getCluster()).thenReturn(cluster); when(hatch.getMainNode()).thenReturn(node);
            when(hatch.isSmartCoolingModule()).thenReturn(true);
            when(node.isActive()).thenReturn(true); when(node.getGrid()).thenReturn(grid);
            when(world.getGameTime()).thenAnswer(call -> currentTick);
            var storage = mock(IStorageService.class); when(grid.getStorageService()).thenReturn(storage);
            when(storage.getInventory()).thenReturn(new MEStorage() {
                public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                    return allowReturns ? engine.insert(key, amount, mode) : 0;
                }
                public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                    if (mode == Actionable.MODULATE) extractions++;
                    else simulations++;
                    return engine.extract(key, amount, mode);
                }
                public Component getDescription() { return Component.empty(); }
            });
            var power = mock(IEnergyService.class); when(grid.getEnergyService()).thenReturn(power);
            when(power.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> {
                double amount = call.getArgument(0);
                if (call.getArgument(1) == Actionable.MODULATE) paidPower += amount;
                return amount;
            });
            doCallRealMethod().when(hatch).tick(any(), any(), any());
        }
        void field(String name, Object value) throws Exception {
            var field = ECOLargeIntegratedWorkingStationInputHatchBlockEntity.class.getDeclaredField(name);
            field.setAccessible(true); field.set(hatch, value);
        }
        void tick(long tick) {
            int saved = NEConfig.smartCoolingRefillThresholdPercent;
            try {
                NEConfig.smartCoolingRefillThresholdPercent = 75;
                currentTick = tick;
                hatch.tick(world, BlockPos.ZERO, Blocks.AIR.defaultBlockState());
            } finally { NEConfig.smartCoolingRefillThresholdPercent = saved; }
        }
    }

    /** Deterministic device trace, not a Minecraft tick-time benchmark. Both runs end with identical stock. */
    public static void main(String[] args) throws Exception {
        bootstrap();
        var rows = new com.google.gson.JsonArray();
        int saved = NEConfig.smartCoolingRefillThresholdPercent;
        try {
            for (int threshold : new int[]{100, 75}) {
                Fixture f = new Fixture();
                f.tank.setCapacity(ECOLargeIntegratedWorkingStationInputHatchBlockEntity.SMART_COOLING_CAPACITY);
                f.tank.setFluid(new FluidStack(Fluids.WATER, f.tank.getCapacity()));
                f.engine.insert(AEFluidKey.of(Fluids.WATER), 100_000_000, Actionable.MODULATE);
                NEConfig.smartCoolingRefillThresholdPercent = threshold;
                f.currentTick = 0;
                f.hatch.tick(f.world, BlockPos.ZERO, Blocks.AIR.defaultBlockState());
                int minimum = f.tank.getCapacity();
                for (int tick = 1; tick <= 82_000; tick++) {
                    if (f.tank.drain(100, IFluidHandler.FluidAction.EXECUTE).getAmount() != 100)
                        throw new AssertionError("coolant starvation");
                    minimum = Math.min(minimum, f.tank.getFluidAmount());
                    f.currentTick = tick;
                    f.hatch.tick(f.world, BlockPos.ZERO, Blocks.AIR.defaultBlockState());
                    minimum = Math.min(minimum, f.tank.getFluidAmount());
                }
                var row = new com.google.gson.JsonObject();
                row.addProperty("thresholdPercent", threshold); row.addProperty("ticks", 82000);
                row.addProperty("consumedMb", 8_200_000); row.addProperty("modulateRequests", f.extractions);
                row.addProperty("simulateRequests", f.simulations); row.addProperty("minimumStockMb", minimum);
                row.addProperty("finalStockMb", f.tank.getFluidAmount()); row.addProperty("paidAE", f.paidPower);
                rows.add(row);
                assertEquals(f.tank.getCapacity(), f.tank.getFluidAmount());
                assertEquals(91_800_000, f.engine.getAmount(AEFluidKey.of(Fluids.WATER)).toLongSaturated());
            }
        } finally { NEConfig.smartCoolingRefillThresholdPercent = saved; }
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));
    }
}
