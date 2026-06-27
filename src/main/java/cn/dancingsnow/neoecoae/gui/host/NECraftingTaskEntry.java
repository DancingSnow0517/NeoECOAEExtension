package cn.dancingsnow.neoecoae.gui.host;

import net.minecraft.world.item.ItemStack;
import net.minecraft.Util;

import java.util.Locale;

public record NECraftingTaskEntry(
    String id,
    ItemStack output,
    long outputAmount,
    long craftCount,
    long totalTicks,
    long remainingTicks,
    Status status,
    long receivedAtMillis
) {
    public NECraftingTaskEntry(
        String id,
        ItemStack output,
        long outputAmount,
        long craftCount,
        long totalTicks,
        long remainingTicks,
        Status status
    ) {
        this(id, output, outputAmount, craftCount, totalTicks, remainingTicks, status, Util.getMillis());
    }

    public enum Status {
        RUNNING,
        QUEUED,
        WAITING_OUTPUT
    }

    public int statusColor() {
        return switch (status) {
            case RUNNING -> 0xFF6CFFA0;
            case QUEUED -> 0xFFFFD65A;
            case WAITING_OUTPUT -> 0xFF3FD6FF;
        };
    }

    public String statusKey() {
        return switch (status) {
            case RUNNING -> "gui.neoecoae.crafting.task.status.running";
            case QUEUED -> "gui.neoecoae.crafting.task.status.queued";
            case WAITING_OUTPUT -> "gui.neoecoae.crafting.task.status.waiting_output";
        };
    }

    long elapsedTicks() {
        return Math.max(0L, totalTicks - liveRemainingTicks());
    }

    public int progressWidth(int width) {
        if (width <= 0) {
            return 0;
        }
        if (status == Status.WAITING_OUTPUT) {
            return width;
        }
        if (status == Status.QUEUED) {
            return 1;
        }
        return ratioWidth(elapsedTicks(), totalTicks, width);
    }

    public String elapsedTimeText() {
        return formatTicks(elapsedTicks());
    }

    public String totalTimeText() {
        return formatTicks(totalTicks);
    }

    private long liveRemainingTicks() {
        if (status != Status.RUNNING || remainingTicks <= 0L) {
            return Math.max(0L, remainingTicks);
        }
        long elapsedTicks = Math.max(0L, (Util.getMillis() - receivedAtMillis) / 50L);
        return Math.max(0L, remainingTicks - elapsedTicks);
    }

    private static int ratioWidth(long used, long total, int width) {
        if (width <= 0 || total <= 0 || used <= 0) {
            return 0;
        }
        long clamped = Math.max(0L, Math.min(used, total));
        return (int) Math.max(1L, Math.min(width, clamped * width / total));
    }

    private static String formatTicks(long ticks) {
        long safe = Math.max(0L, ticks);
        if (safe < 20L) {
            return safe + "t";
        }
        double seconds = safe / 20.0D;
        if (seconds < 60.0D) {
            return String.format(Locale.US, "%.1fs", seconds);
        }
        long minutes = (long) (seconds / 60.0D);
        double remainder = seconds - minutes * 60.0D;
        return String.format(Locale.US, "%dm %.0fs", minutes, remainder);
    }
}
