package cn.dancingsnow.neoecoae.achievement;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Small server-side bridge for achievements whose condition is a runtime event. */
public final class ECOAdvancementService {
    private ECOAdvancementService() {
    }

    public static void award(ServerPlayer player, String advancementPath, String criterion) {
        if (player == null || player.getServer() == null) {
            return;
        }

        Advancement advancement = player.getServer().getAdvancements().getAdvancement(NeoECOAE.id(advancementPath));
        if (advancement != null) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    public static void awardNearest(
        ServerLevel level,
        BlockPos position,
        double radius,
        String advancementPath,
        String criterion,
        @Nullable ServerPlayer preferredPlayer
    ) {
        if (preferredPlayer != null) {
            award(preferredPlayer, advancementPath, criterion);
            return;
        }

        double radiusSquared = radius * radius;
        ServerPlayer nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        double x = position.getX() + 0.5D;
        double y = position.getY() + 0.5D;
        double z = position.getZ() + 0.5D;
        for (ServerPlayer player : level.players()) {
            double distanceSquared = player.distanceToSqr(x, y, z);
            if (distanceSquared <= radiusSquared && distanceSquared < nearestDistanceSquared) {
                nearest = player;
                nearestDistanceSquared = distanceSquared;
            }
        }
        award(nearest, advancementPath, criterion);
    }
}
