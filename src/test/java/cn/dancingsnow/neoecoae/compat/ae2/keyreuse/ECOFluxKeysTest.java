package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ECOFluxKeysTest {
    @BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }
    @AfterEach void stop() { KeyReuse.configure(false); }

    @Test void reuseIsConfinedToEcoAndLeavesTheAddonFactoryUnchanged() {
        KeyReuse.configure(true);
        FluxKey eco = ECOFluxKeys.of(EnergyType.FE);
        assertSame(eco, ECOFluxKeys.of(EnergyType.FE));
        FluxKey nativeFirst = FluxKey.of(EnergyType.FE);
        FluxKey nativeSecond = FluxKey.of(EnergyType.FE);
        assertNotSame(nativeFirst, nativeSecond);
        assertNotSame(eco, nativeFirst);
        assertEquals(eco, nativeFirst);
        assertEquals(nativeFirst, nativeSecond);
    }

    @Test void factoryAllocatesOncePerGenerationAndPreservesTheDisabledAndNullPaths() {
        var allocations = new AtomicInteger();
        java.util.function.Function<EnergyType, FluxKey> constructor = type -> {
            allocations.incrementAndGet();
            return FluxKey.of(type);
        };
        KeyReuse.configure(true);
        FluxKey first = ECOFluxKeys.getOrCreate(EnergyType.FE, constructor);
        for (int i = 0; i < 1000; i++) assertSame(first, ECOFluxKeys.getOrCreate(EnergyType.FE, constructor));
        assertEquals(1, allocations.get());
        assertNull(ECOFluxKeys.getOrCreate(null, constructor));
        KeyReuse.invalidate();
        FluxKey second = ECOFluxKeys.getOrCreate(EnergyType.FE, constructor);
        assertNotSame(first, second);
        assertEquals(first, second);
        KeyReuse.configure(false);
        assertNotSame(ECOFluxKeys.getOrCreate(EnergyType.FE, constructor),
                ECOFluxKeys.getOrCreate(EnergyType.FE, constructor));
        assertEquals(4, allocations.get());
    }

    @Test void concurrentFactoryCallsPublishOnlyOneCompletelyInitializedKey() throws Exception {
        KeyReuse.configure(true);
        var allocations = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = new ArrayList<java.util.concurrent.Callable<FluxKey>>();
            for (int i = 0; i < 64; i++) calls.add(() -> ECOFluxKeys.getOrCreate(EnergyType.FE, type -> {
                allocations.incrementAndGet();
                return FluxKey.of(type);
            }));
            var results = executor.invokeAll(calls);
            FluxKey first = results.getFirst().get();
            for (var result : results) assertSame(first, result.get());
            assertEquals(1, allocations.get());
            assertEquals(EnergyType.FE, first.getEnergyType());
        }
    }
}
