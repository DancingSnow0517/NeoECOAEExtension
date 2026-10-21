package cn.dancingsnow.neoecoae.util;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.LoadingModList;

import java.util.List;

import static org.mockito.Mockito.*;

/** Supplies registry/channel initialization boundaries normally installed by the Forge launcher. */
public final class InventoryTestBootstrap {
    private InventoryTestBootstrap() {}

    public static void initialize() {
        SharedConstants.tryDetectVersion();
        try (var loading = mockStatic(LoadingModList.class);
             var network = mockStatic(net.minecraftforge.network.NetworkHooks.class)) {
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loading.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
            cn.dancingsnow.neoecoae.compat.ae2.AE2PatternOptimizationIntegration.init();
            cn.dancingsnow.neoecoae.compat.ECOPatternIntegrations.initialize();
        }
    }
}
