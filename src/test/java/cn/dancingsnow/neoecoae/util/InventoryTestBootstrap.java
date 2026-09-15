package cn.dancingsnow.neoecoae.util;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

import java.util.List;

import static org.mockito.Mockito.*;

/** Supplies the mod list normally installed by the NeoForge launcher. */
public final class InventoryTestBootstrap {
    private InventoryTestBootstrap() {}

    public static void initialize() {
        SharedConstants.tryDetectVersion();
        try (var loading = mockStatic(LoadingModList.class)) {
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loading.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }
}
