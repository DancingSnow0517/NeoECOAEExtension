package cn.dancingsnow.neoecoae.util;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import net.neoforged.fml.ModList;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

import java.util.List;

import static org.mockito.Mockito.*;

/** Supplies the mod list normally installed by the NeoForge launcher. */
public final class InventoryTestBootstrap {
    private InventoryTestBootstrap() {}

    public static void initializeUi() throws ClassNotFoundException {
        initialize();
        try (var mods = mockStatic(ModList.class); var registrate = mockStatic(NERegistrate.class)) {
            ModList modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(UIElement.class.getName());
            Class.forName(NETextures.class.getName());
            AccessorRegistries.init();
        }
    }

    public static void initialize() {
        SharedConstants.tryDetectVersion();
        try (var loading = mockStatic(LoadingModList.class)) {
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loading.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
            cn.dancingsnow.neoecoae.compat.ae2.AE2PatternOptimizationIntegration.init();
            cn.dancingsnow.neoecoae.compat.ECOPatternIntegrations.initialize();
        }
    }
}
