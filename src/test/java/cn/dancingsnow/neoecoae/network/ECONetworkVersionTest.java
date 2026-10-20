package cn.dancingsnow.neoecoae.network;

import java.util.Optional;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent;
import net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ECONetworkVersionTest {
    private static NegotiableNetworkComponent component(String version) {
        return new NegotiableNetworkComponent(
            ResourceLocation.fromNamespaceAndPath("neoecoae", "menu_chunk"), version,
            Optional.of(PacketFlow.CLIENTBOUND), false);
    }

    @Test
    void sameEcoReleasePassesNeoForgeVersionValidation() {
        var server = component(ECONetwork.protocolVersion("21.2.0-beta6"));
        var client = component(ECONetwork.protocolVersion("21.2.0-beta6"));
        assertTrue(NetworkComponentNegotiator.validateComponent(server, client, "client").isEmpty());
        assertTrue(NetworkComponentNegotiator.validateComponent(client, server, "server").isEmpty());
    }

    @Test
    void differentEcoReleasesAreRejectedWithBothVersions() {
        assertRejected(ECONetwork.protocolVersion("21.2.0-beta4"),
            ECONetwork.protocolVersion("21.2.0-beta3"));
    }

    @Test
    void legacyConstantProtocolIsRejectedInEitherDirection() {
        String current = ECONetwork.protocolVersion("21.2.0-beta6");
        assertRejected(current, "3");
        assertRejected("3", current);
    }

    private static void assertRejected(String serverVersion, String clientVersion) {
        var result = NetworkComponentNegotiator.validateComponent(
            component(serverVersion), component(clientVersion), "client").orElseThrow();
        assertFalse(result.success());
        var reason = assertInstanceOf(TranslatableContents.class, result.failureReason().getContents());
        assertEquals("neoforge.network.negotiation.failure.version.mismatch", reason.getKey());
        assertArrayEquals(new Object[] {serverVersion, clientVersion}, reason.getArgs());
    }
}
