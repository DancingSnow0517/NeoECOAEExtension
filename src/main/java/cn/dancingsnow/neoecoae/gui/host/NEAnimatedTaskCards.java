package cn.dancingsnow.neoecoae.gui.host;

import net.minecraft.Util;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class NEAnimatedTaskCards {
    private static final long TASK_FADE_MS = 360L;
    private static final long TASK_MOVE_MS = 140L;

    private final Map<String, TaskCardAnimation> animations = new LinkedHashMap<>();
    private List<Frame> lastFrames = List.of();
    private int lastScrollOffset;
    private boolean initialized;

    List<Frame> update(
        List<NECraftingTaskEntry> entries,
        int scrollOffset,
        int visibleRows,
        int firstY,
        int rowStride
    ) {
        long now = Util.getMillis();
        Set<String> visibleKeys = new HashSet<>();
        int visible = Math.min(visibleRows, Math.max(0, entries.size() - scrollOffset));
        int entryOffset = initialized ? Mth.clamp((scrollOffset - lastScrollOffset) * rowStride, -rowStride, rowStride) : 0;
        for (int i = 0; i < visible; i++) {
            int entryIndex = scrollOffset + i;
            NECraftingTaskEntry entry = entries.get(entryIndex);
            String key = taskEntryKey(entry, entryIndex);
            int targetY = firstY + i * rowStride;
            visibleKeys.add(key);
            TaskCardAnimation animation = animations.get(key);
            if (animation == null) {
                animation = new TaskCardAnimation(entry, targetY, entryOffset);
                animations.put(key, animation);
            } else {
                animation.entry = entry;
                animation.targetY = targetY;
                animation.exiting = false;
            }
        }

        for (Map.Entry<String, TaskCardAnimation> entry : animations.entrySet()) {
            TaskCardAnimation animation = entry.getValue();
            if (!visibleKeys.contains(entry.getKey()) && !animation.exiting) {
                animation.exiting = true;
                animation.exitStartedMs = now;
            }
        }

        List<Frame> frames = new ArrayList<>();
        animations.entrySet().removeIf(entry -> {
            TaskCardAnimation animation = entry.getValue();
            animation.update(now);
            if (animation.exiting && animation.alpha <= 0.02F) {
                return true;
            }
            frames.add(new Frame(animation.entry, animation.y, animation.alpha, animation.exiting));
            return false;
        });
        frames.sort(Comparator.comparingDouble(Frame::y));
        lastFrames = List.copyOf(frames);
        lastScrollOffset = scrollOffset;
        initialized = true;
        return frames;
    }

    List<Frame> frames() {
        return lastFrames;
    }

    private static String taskEntryKey(NECraftingTaskEntry entry, int index) {
        return entry.id() == null || entry.id().isBlank() ? "task:" + index : entry.id();
    }

    record Frame(NECraftingTaskEntry entry, float y, float alpha, boolean exiting) {
    }

    private static final class TaskCardAnimation {
        private NECraftingTaskEntry entry;
        private float y;
        private int targetY;
        private float alpha;
        private long lastUpdateMs;
        private boolean exiting;
        private long exitStartedMs;

        private TaskCardAnimation(NECraftingTaskEntry entry, int targetY, int entryOffset) {
            this.entry = entry;
            this.targetY = targetY;
            this.y = targetY + entryOffset;
            this.lastUpdateMs = Util.getMillis();
        }

        private void update(long nowMs) {
            long elapsed = Math.max(0L, Math.min(1000L, nowMs - lastUpdateMs));
            lastUpdateMs = nowMs;
            float moveT = TASK_MOVE_MS <= 0L ? 1.0F : Mth.clamp((float) elapsed / (float) TASK_MOVE_MS, 0.0F, 1.0F);
            y += (targetY - y) * moveT;
            if (Math.abs(targetY - y) < 0.25F) {
                y = targetY;
            }
            if (exiting) {
                long fadeElapsed = Math.max(0L, nowMs - exitStartedMs);
                alpha = 1.0F - Mth.clamp((float) fadeElapsed / (float) TASK_FADE_MS, 0.0F, 1.0F);
            } else {
                float fadeStep = TASK_FADE_MS <= 0L ? 1.0F : Mth.clamp((float) elapsed / (float) TASK_FADE_MS, 0.0F, 1.0F);
                alpha = Math.min(1.0F, alpha + fadeStep);
            }
        }
    }
}
