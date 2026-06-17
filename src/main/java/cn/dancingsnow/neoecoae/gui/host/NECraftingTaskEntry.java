package cn.dancingsnow.neoecoae.gui.host;

import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public record NECraftingTaskEntry(
    String id,
    ItemStack output,
    long outputAmount,
    long craftCount,
    long totalTicks,
    long remainingTicks,
    Status status
) {
    public enum Status {
        RUNNING,
        QUEUED,
        WAITING_OUTPUT
    }

    int statusColor() {
        return switch (status) {
            case RUNNING -> NEHostCanvas.TEXT_SUCCESS;
            case QUEUED -> NEHostCanvas.TEXT_WARNING;
            case WAITING_OUTPUT -> NEHostCanvas.TEXT_BLUE;
        };
    }

    String statusKey() {
        return switch (status) {
            case RUNNING -> "gui.neoecoae.crafting.task.status.running";
            case QUEUED -> "gui.neoecoae.crafting.task.status.queued";
            case WAITING_OUTPUT -> "gui.neoecoae.crafting.task.status.waiting_output";
        };
    }

    long elapsedTicks() {
        return Math.max(0L, totalTicks - remainingTicks);
    }

    int progressWidth(int width) {
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

    String elapsedTimeText() {
        return formatTicks(elapsedTicks());
    }

    String totalTimeText() {
        return formatTicks(totalTicks);
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
