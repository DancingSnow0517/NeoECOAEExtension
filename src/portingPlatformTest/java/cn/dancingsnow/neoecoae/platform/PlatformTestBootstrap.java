package cn.dancingsnow.neoecoae.platform;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.network.NetworkHooks;
import static org.mockito.Mockito.*;

public final class PlatformTestBootstrap {
    private PlatformTestBootstrap() {}
    public static void initialize() {
        SharedConstants.tryDetectVersion();
        // JUnit has no ModLauncher event-class transformer; leave Forge channel startup to launch tests.
        try (var loading = mockStatic(LoadingModList.class); var network = mockStatic(NetworkHooks.class)) {
            var list = mock(LoadingModList.class);
            when(list.getModFiles()).thenReturn(List.of());
            loading.when(LoadingModList::get).thenReturn(list);
            Bootstrap.bootStrap();
        }
    }
}
