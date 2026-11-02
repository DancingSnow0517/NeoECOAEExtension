package cn.dancingsnow.neoecoae.util;

import net.minecraft.server.level.ServerLevel;

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
}
