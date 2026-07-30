package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import appeng.api.networking.crafting.ICraftingSimulationRequester;
import cn.dancingsnow.neoecoae.network.ECOPlannerNoticePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Delivers a planning-path status to the requester without broadcasting it to unrelated players. */
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
            PacketDistributor.sendToPlayer(player, new ECOPlannerNoticePayload(target.containerId(), reason.id()));
        });
    }

    public record Target(ServerPlayer player, int containerId) {
    }
}
