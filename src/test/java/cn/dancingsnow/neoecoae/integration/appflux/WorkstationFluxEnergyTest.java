package cn.dancingsnow.neoecoae.integration.appflux;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkstationFluxEnergyTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void havingTheApiOnTheClasspathDoesNotPermitFeWithoutTheLoadedMod() {
        var mods = mock(ModList.class);
        var storage = mock(MEStorage.class);
        try (var list = mockStatic(ModList.class)) {
            list.when(ModList::get).thenReturn(mods);
            assertFalse(WorkstationFluxEnergy.available());
            assertEquals(0, WorkstationFluxEnergy.extract(storage, 100, Actionable.MODULATE, mock(IActionSource.class)));
            verifyNoInteractions(storage);
        }
    }

    @Test void loadedModExtractsTheExactLongFeKeyAmountFromMeStorage() {
        var mods = mock(ModList.class);
        var storage = mock(MEStorage.class);
        var source = mock(IActionSource.class);
        var key = FluxKey.of(EnergyType.FE);
        long requested = 9_007_199_254_740_993L;
        when(mods.isLoaded("appflux")).thenReturn(true);
        when(storage.extract(eq(key), eq(requested), eq(Actionable.MODULATE), eq(source))).thenReturn(requested);
        try (var list = mockStatic(ModList.class)) {
            list.when(ModList::get).thenReturn(mods);
            assertTrue(WorkstationFluxEnergy.available());
            assertEquals(requested, WorkstationFluxEnergy.extract(storage, requested, Actionable.MODULATE, source));
            verify(storage).extract(eq(key), eq(requested), eq(Actionable.MODULATE), eq(source));
            verifyNoMoreInteractions(storage);
        }
    }
}
