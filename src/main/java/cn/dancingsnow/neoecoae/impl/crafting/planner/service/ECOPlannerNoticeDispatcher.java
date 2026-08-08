package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import appeng.api.networking.crafting.ICraftingSimulationRequester;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOCyclePlanningDiagnostics;
import net.minecraft.server.level.ServerPlayer;
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
        // Planner notices are intentionally silent on the 1.20.1 Forge port.
    }

    public static void sendOverflow(
        @Nullable Target target,
        long elapsedNanos,
        java.math.BigInteger exactBytes
    ) {
        send(target, ECOPlannerFallbackReason.OVERFLOW, elapsedNanos);
    }

    public static void sendCycleDiagnostics(
        @Nullable Target target,
        ECOCyclePlanningDiagnostics diagnostics
    ) {
        // The Forge 1.20.1 port has no payload channel for planner diagnostics yet.
    }

    public record Target(ServerPlayer player, int containerId) {
    }
}
