package cn.dancingsnow.neoecoae.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.RejectedExecutionException;

public final class ServerTaskUtil {
    private ServerTaskUtil() {
    }

    public static void executeIfServerRunning(ServerLevel level, Runnable task) {
        try {
            level.getServer().executeIfPossible(task);
        } catch (RejectedExecutionException ignored) {
            // The server is already shutting down, so late persistence sync callbacks can be dropped.
        }
    }

    public static boolean isServerStopping(@Nullable Level level) {
        return level instanceof ServerLevel serverLevel && serverLevel.getServer().isStopped();
    }
}
