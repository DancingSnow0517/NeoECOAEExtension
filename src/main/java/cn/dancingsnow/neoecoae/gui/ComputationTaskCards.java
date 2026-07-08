package cn.dancingsnow.neoecoae.gui;

import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ComputationTaskCards {
    private static final int CARD_EDGE = 0xFFD8D3E4;
    private static final int CARD_BORDER = 0xFF121016;
    private static final int CARD_MID = 0xFF4D4855;
    private static final int CARD_FILL = 0xFF2C2735;
    private static final int PROGRESS_BG = 0xAA1F2F34;
    private static final int PROGRESS_FILL = 0xFF26A6BD;
    private static final int STATUS_BLUE = 0xFF61AFEF;

    private ComputationTaskCards() {
    }

    static int statusColor(ComputationTaskEntry.Status status) {
        return switch (status) {
            case RUNNING -> StorageHostText.USED;
            case QUEUED -> StorageHostText.WARNING;
            case WAITING_OUTPUT -> STATUS_BLUE;
        };
    }

    static String statusKey(ComputationTaskEntry.Status status) {
        return switch (status) {
            case RUNNING -> "gui.neoecoae.crafting.task.status.running";
            case QUEUED -> "gui.neoecoae.crafting.task.status.queued";
            case WAITING_OUTPUT -> "gui.neoecoae.crafting.task.status.waiting_output";
        };
    }

    static void drawCard(
        GUIContext guiContext,
        Font font,
        ComputationTaskEntry entry,
        int x,
        int y,
        int width,
        int height
    ) {
        int accent = statusColor(entry.status());
        var graphics = guiContext.graphics;
        graphics.fill(x, y, x + width, y + height, CARD_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, CARD_BORDER);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, CARD_MID);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, CARD_FILL);
        graphics.fill(x + 3, y + height - 3, x + width - 3, y + height - 2, accent);

        if (!entry.output().isEmpty()) {
            DrawerHelper.drawItemStack(graphics, entry.output(), x + 4, y + 4, -1, null);
        }

        String amountText = "x" + compactAmount(entry.outputAmount());
        int amountWidth = font.width(amountText);
        int maxNameWidth = Math.max(16, width - 34 - amountWidth);
        String name = fitWithEllipsis(font, entry.output().getHoverName().getString(), maxNameWidth);
        drawString(guiContext, font, name, x + 24, y + 4, StorageHostText.PRIMARY, 1.0F);
        drawRightString(guiContext, font, amountText, x + width - 5, y + 11, StorageHostText.VALUE);
        drawProgressBar(guiContext, entry, x + 24, y + height - 9, width - 29, 4);
    }

    static List<Component> tooltipLines(ComputationTaskEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(cpuName(entry));

        int coProcessors = entry.cpuCoProcessors();
        if (coProcessors == 1) {
            lines.add(ButtonToolTips.CpuStatusCoProcessor.text(Tooltips.ofNumber(coProcessors))
                .withStyle(ChatFormatting.GRAY));
        } else if (coProcessors > 1) {
            lines.add(ButtonToolTips.CpuStatusCoProcessors.text(Tooltips.ofNumber(coProcessors))
                .withStyle(ChatFormatting.GRAY));
        }

        lines.add(ButtonToolTips.CpuStatusStorage.text(Tooltips.ofBytes(entry.cpuStorage()))
            .withStyle(ChatFormatting.GRAY));

        Component modeText = switch (entry.cpuSelectionMode()) {
            case PLAYER_ONLY -> ButtonToolTips.CpuSelectionModePlayersOnly.text();
            case MACHINE_ONLY -> ButtonToolTips.CpuSelectionModeAutomationOnly.text();
            case ANY -> null;
        };
        if (modeText != null) {
            lines.add(modeText);
        }

        GenericStack currentJob = currentJob(entry);
        if (currentJob != null) {
            lines.add(ButtonToolTips.CpuStatusCrafting.text(Tooltips.ofAmount(currentJob))
                .append(" ")
                .append(currentJob.what().getDisplayName()));
            lines.add(ButtonToolTips.CpuStatusCraftedIn.text(
                Tooltips.ofPercent(entry.progress()),
                Tooltips.ofDuration(entry.elapsedTimeNanos(), TimeUnit.NANOSECONDS)));
        }
        return lines;
    }

    private static Component cpuName(ComputationTaskEntry entry) {
        return entry.cpuName() != null ? entry.cpuName() : GuiText.CPUs.text().append(String.format(" #%d", entry.cpuSerial()));
    }

    private static GenericStack currentJob(ComputationTaskEntry entry) {
        if (entry.output().isEmpty()) {
            return null;
        }
        AEItemKey itemKey = AEItemKey.of(entry.output());
        if (itemKey == null) {
            return null;
        }
        return new GenericStack(itemKey, entry.outputAmount());
    }

    static String progressText(ComputationTaskEntry entry) {
        return StorageHostText.percent(entry.progress());
    }

    static String compactAmount(long value) {
        return StorageHostText.typeProgress(Math.max(0L, value), 0).usedText();
    }

    private static void drawProgressBar(
        GUIContext guiContext,
        ComputationTaskEntry entry,
        int x,
        int y,
        int width,
        int height
    ) {
        guiContext.graphics.fill(x, y, x + width, y + height, PROGRESS_BG);
        int fillWidth = progressWidth(entry, width);
        if (fillWidth > 0) {
            guiContext.graphics.fill(x, y, x + fillWidth, y + height, PROGRESS_FILL);
        }
    }

    private static int progressWidth(ComputationTaskEntry entry, int width) {
        if (entry.status() == ComputationTaskEntry.Status.WAITING_OUTPUT) {
            return width;
        }
        if (entry.status() == ComputationTaskEntry.Status.QUEUED) {
            return 1;
        }
        if (width <= 0 || entry.progress() <= 0.0F) {
            return 0;
        }
        return Math.max(1, Math.min(width, Math.round(Mth.clamp(entry.progress(), 0.0F, 1.0F) * width)));
    }

    private static String fitWithEllipsis(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        StringBuilder builder = new StringBuilder(text);
        while (!builder.isEmpty() && font.width(builder.toString()) + suffixWidth > maxWidth) {
            builder.setLength(builder.length() - 1);
        }
        return builder + suffix;
    }

    private static void drawRightString(GUIContext guiContext, Font font, String text, int rightX, int y, int color) {
        drawString(guiContext, font, text, rightX - font.width(text), y, color, 1.0F);
    }

    private static void drawString(GUIContext guiContext, Font font, String text, int x, int y, int color, float scale) {
        if (scale == 1.0F) {
            guiContext.graphics.drawString(font, text, x, y, color, false);
            return;
        }
        guiContext.graphics.pose().pushPose();
        guiContext.graphics.pose().translate(x, y, 0);
        guiContext.graphics.pose().scale(scale, scale, 1.0F);
        guiContext.graphics.drawString(font, text, 0, 0, color, false);
        guiContext.graphics.pose().popPose();
    }
}
