package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import appeng.api.networking.crafting.ICraftingSimulationRequester;
import cn.dancingsnow.neoecoae.network.ECOPlannerNoticePayload;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOCyclePlanningDiagnostics;
import cn.dancingsnow.neoecoae.network.ECOCycleDiagnosticsPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Delivers planner status only to the player viewing the affected crafting menu. */
public final class ECOPlannerNoticeDispatcher {
    private ECOPlannerNoticeDispatcher() {
    }

    public static @Nullable Target targetFor(ICraftingSimulationRequester requester) {
        var actionSource = requester.getActionSource();
        if (actionSource == null) {
            return null;
        }
        return actionSource.player()
            .filter(ServerPlayer.class::isInstance)
            .map(ServerPlayer.class::cast)
            .map(player -> new Target(player, player.containerMenu.containerId))
            .orElse(null);
    }

    public static void send(@Nullable Target target, ECOPlannerFallbackReason reason) {
        send(target, reason, 0L);
    }

    public static void send(@Nullable Target target, ECOPlannerFallbackReason reason, long elapsedNanos) {
        if (target == null) {
            return;
        }
        MinecraftServer server = target.player().getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer player = target.player();
            if (player.getServer() != server || player.containerMenu.containerId != target.containerId()) {
                return;
            }
            PacketDistributor.sendToPlayer(
                player,
                new ECOPlannerNoticePayload(target.containerId(), reason.id(), Math.max(0L, elapsedNanos))
            );
        });
    }

    public static void sendCycleDiagnostics(
        @Nullable Target target,
        ECOCyclePlanningDiagnostics diagnostics
    ) {
        if (target == null) {
            return;
        }
        MinecraftServer server = target.player().getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer player = target.player();
            if (player.getServer() != server || player.containerMenu.containerId != target.containerId()) {
                return;
            }
            PacketDistributor.sendToPlayer(
                player,
                new ECOCycleDiagnosticsPayload(target.containerId(), diagnostics)
            );
        });
    }

    public record Target(ServerPlayer player, int containerId) {
    }
}
