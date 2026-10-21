package cn.dancingsnow.neoecoae.platform;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.network.NetworkHooks;
import static org.mockito.Mockito.*;

public final class PlatformTestBootstrap {
    private static boolean keyRegistryInitialized;
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

    /** Install the real Forge key registry once, as AE2 does during launcher startup. */
    @SuppressWarnings("unchecked")
    public static synchronized void initializeKeyRegistry() throws ReflectiveOperationException {
        initialize();
        if (keyRegistryInitialized) return;
        var builder = new net.minecraftforge.registries.RegistryBuilder<appeng.api.stacks.AEKeyType>()
            .setName(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("neoecoae_test", "key_types"));
        var create = net.minecraftforge.registries.RegistryBuilder.class.getDeclaredMethod("create");
        create.setAccessible(true); // No ModLauncher registry lifecycle in JUnit.
        var registry = (net.minecraftforge.registries.IForgeRegistry<appeng.api.stacks.AEKeyType>) create.invoke(builder);
        registry.register(appeng.api.stacks.AEKeyType.items().getId(), appeng.api.stacks.AEKeyType.items());
        registry.register(appeng.api.stacks.AEKeyType.fluids().getId(), appeng.api.stacks.AEKeyType.fluids());
        appeng.api.stacks.AEKeyTypesInternal.setRegistry(() -> registry);
        keyRegistryInitialized = true;
    }
}
