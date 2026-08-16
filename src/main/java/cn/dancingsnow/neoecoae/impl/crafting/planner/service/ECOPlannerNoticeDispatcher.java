package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import appeng.api.networking.crafting.ICraftingSimulationRequester;
import cn.dancingsnow.neoecoae.network.ECOPlannerNoticePayload;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOCyclePlanningDiagnostics;
import cn.dancingsnow.neoecoae.network.ECOCycleDiagnosticsPayload;
import cn.dancingsnow.neoecoae.network.ECOPlanningMissingPayload;
import appeng.api.stacks.AEKey;
import java.util.Map;
import cn.dancingsnow.neoecoae.util.ByteAmountFormatter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Delivers planner status only to the player viewing the affected crafting menu. */
public final class ECOPlannerNoticeDispatcher {
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();

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
            .map(player -> new Target(player, player.containerMenu.containerId, NEXT_REQUEST_ID.incrementAndGet()))
            .orElse(null);
    }

    public static void send(@Nullable Target target, ECOPlannerFallbackReason reason) {
        send(target, reason, 0L);
    }

    public static void send(@Nullable Target target, ECOPlannerFallbackReason reason, long elapsedNanos) {
        send(target, reason, elapsedNanos, "", List.of());
    }

    public static void send(
        @Nullable Target target,
        ECOPlannerFallbackReason reason,
        long elapsedNanos,
        List<ECOPlannerDiagnostic> diagnostics
    ) {
        send(target, reason, elapsedNanos, "", diagnostics);
    }

    public static void sendOverflow(
        @Nullable Target target,
        long elapsedNanos,
        java.math.BigInteger exactBytes
    ) {
        send(target, ECOPlannerFallbackReason.OVERFLOW, elapsedNanos, ByteAmountFormatter.format(exactBytes), List.of());
    }

    private static void send(
        @Nullable Target target,
        ECOPlannerFallbackReason reason,
        long elapsedNanos,
        String formattedBytes,
        List<ECOPlannerDiagnostic> diagnostics
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
                new ECOPlannerNoticePayload(
                    target.containerId(),
                    target.requestId(),
                    reason.id(),
                    Math.max(0L, elapsedNanos),
                    formattedBytes,
                    diagnostics.stream().map(ECOPlannerDiagnostic::id).reduce((left, right) -> left + "," + right).orElse("")
                )
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

    public record Target(ServerPlayer player, int containerId, long requestId) {
    }

    public static void sendPlanningMissing(@Nullable Target target, Map<AEKey, Long> missing) {
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
                new ECOPlanningMissingPayload(target.containerId(), target.requestId(), missing)
            );
        });
    }
}
