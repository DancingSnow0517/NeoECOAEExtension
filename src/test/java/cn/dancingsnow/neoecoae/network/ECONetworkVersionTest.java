package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECONetworkVersionTest {
    @BeforeAll static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(NeoECOAE.class.getName());
        }
    }

    @Test void sameEcoReleasePassesBothForgeChannelChecks() {
        var checks = register("20.6.0-dev");
        assertEquals("4/20.6.0-dev", checks.version().get());
        assertTrue(checks.client().test(checks.version().get()));
        assertTrue(checks.server().test(checks.version().get()));
    }

    @Test void differentEcoReleasesAreRejectedInBothDirections() {
        var checks = register("20.6.0-dev");
        String older = ECONetwork.protocolVersion("20.5.0");
        assertFalse(checks.client().test(older));
        assertFalse(checks.server().test(older));
    }

    @Test void legacyAndMissingChannelsAreRejectedInBothDirections() {
        var checks = register("20.6.0-dev");
        for (String version : new String[] {"3", "3/20.6.0-dev", NetworkRegistry.ABSENT.version(), NetworkRegistry.ACCEPTVANILLA}) {
            assertFalse(checks.client().test(version));
            assertFalse(checks.server().test(version));
        }
    }

    private record Checks(Supplier<String> version, Predicate<String> client, Predicate<String> server) {}

    private static Checks register(String version) {
        var modList = mock(ModList.class);
        var container = mock(ModContainer.class);
        var info = mock(IModInfo.class);
        doReturn(Optional.of(container)).when(modList).getModContainerById("neoecoae");
        when(container.getModInfo()).thenReturn(info);
        when(info.getVersion()).thenReturn(new DefaultArtifactVersion(version));
        Checks[] captured = new Checks[1];
        try (var mods = mockStatic(ModList.class); var registry = mockStatic(NetworkRegistry.class)) {
            mods.when(ModList::get).thenReturn(modList);
            registry.when(() -> NetworkRegistry.newSimpleChannel(any(), any(), any(), any()))
                .thenAnswer(call -> {
                    captured[0] = new Checks(call.getArgument(1), call.getArgument(2), call.getArgument(3));
                    return mock(SimpleChannel.class);
                });
            ECONetwork.register();
        }
        assertNotNull(captured[0]);
        return captured[0];
    }
}
