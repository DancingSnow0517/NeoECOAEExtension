package cn.dancingsnow.neoecoae.gui.task;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class ComputationTaskCards {
    private static final int CARD_EDGE = 0xFFD8D3E4;
    private static final int CARD_BORDER = 0xFF121016;
    private static final int CARD_MID = 0xFF4D4855;
    private static final int CARD_FILL = 0xFF2C2735;
    private static final int PROGRESS_BG = 0xAA1F2F34;
    private static final int PROGRESS_FILL = 0xFF26A6BD;
    private static final int STATUS_BLUE = 0xFF61AFEF;

    private ComputationTaskCards() {
    }

    public static int statusColor(ComputationTaskEntry.Status status) {
        return switch (status) {
            case RUNNING -> HostText.USED;
            case QUEUED -> HostText.WARNING;
            case WAITING_OUTPUT -> STATUS_BLUE;
        };
    }

    public static String statusKey(ComputationTaskEntry.Status status) {
        return switch (status) {
            case RUNNING -> "gui.neoecoae.crafting.task.status.running";
            case QUEUED -> "gui.neoecoae.crafting.task.status.queued";
            case WAITING_OUTPUT -> "gui.neoecoae.crafting.task.status.waiting_output";
        };
    }

    public static void drawCard(
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
        drawString(guiContext, font, name, x + 24, y + 4, HostText.PRIMARY);
        drawRightString(guiContext, font, amountText, x + width - 5, y + 11, HostText.VALUE);
        drawProgressBar(guiContext, entry, x + 24, y + height - 9, width - 29, 4);
    }

    public static List<Component> tooltipLines(ComputationTaskEntry entry) {
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

    public static Component fastPathReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return Component.translatable("gui.neoecoae.crafting.fast_path_reason.unknown");
        }
        if (reason.startsWith("OUTPUT_COUNT_")) {
            return Component.translatable("gui.neoecoae.crafting.fast_path_reason.output_count",
                reason.substring("OUTPUT_COUNT_".length()));
        }
        for (String role : new String[] {"OUTPUT", "REMAINDER", "INPUT"}) {
            String prefix = role + "_";
            if (reason.startsWith(prefix)) {
                String failure = reason.substring(prefix.length());
                String failureKey = validationFailureKey(failure);
                return Component.translatable("gui.neoecoae.crafting.fast_path_reason." + role.toLowerCase(Locale.ROOT),
                    Component.translatable("gui.neoecoae.crafting.fast_path_reason.validation." + failureKey));
            }
        }
        if (reason.startsWith("CLASSIFIER_FAILED:")) {
            return Component.translatable("gui.neoecoae.crafting.fast_path_reason.classifier_failed",
                reason.substring("CLASSIFIER_FAILED:".length()));
        }
        String key = switch (reason) {
            case "CACHE_MISS" -> "cache_miss";
            case "FAST_PATH_DISABLED" -> "fast_path_disabled";
            case "POST_CRAFTING_EVENT_ENABLED" -> "post_crafting_event_enabled";
            case "KEY_BUILD_FAILED" -> "key_build_failed";
            case "AE2_INTROSPECTION_UNAVAILABLE" -> "ae2_introspection_unavailable";
            case "UNSAFE_PATTERN_TYPE" -> "unsafe_pattern_type";
            case "SLOW_EXECUTION_CONTEXT" -> "slow_execution_context";
            case "CACHE_RESULT_MISMATCH" -> "cache_result_mismatch";
            case "NEGATIVE_CACHE" -> "negative_cache";
            case "CACHED_RESULT_MATERIALIZATION_FAILED" -> "cached_result_materialization_failed";
            case "VERIFIED_OUTPUT_OR_INPUT_CONVERSION_FAILED" -> "verified_output_or_input_conversion_failed";
            case "ASSEMBLY_CONTRACT_MISMATCH" -> "assembly_contract_mismatch";
            case "STATE_SECOND_STEP_PROOF_FAILED" -> "state_second_step_proof_failed";
            case "VERIFIED_STACK_VALIDATION_FAILED" -> "verified_stack_validation_failed";
            case "REUSABLE_STATE_MODEL_MISSING" -> "reusable_state_model_missing";
            case "STATE_SLOT_COUNT_MISMATCH" -> "state_slot_count_mismatch";
            case "MIXED_REUSABLE_STATE_MODELS" -> "mixed_reusable_state_models";
            case "DURABILITY_TRANSITION_INVALID" -> "durability_transition_invalid";
            case "STATE_TRANSITION_NOT_PROVABLY_LINEAR" -> "state_transition_not_provably_linear";
            case "VERIFICATION_REJECTED" -> "verification_rejected";
            case "PATTERN_INPUT_INSPECTION_FAILED" -> "pattern_input_inspection_failed";
            case "PATTERN_NULL" -> "pattern_null";
            case "NO_INPUTS" -> "no_inputs";
            case "NO_OUTPUTS" -> "no_outputs";
            case "NON_ITEM_OUTPUT" -> "non_item_output";
            case "INVALID_INPUT" -> "invalid_input";
            case "NON_ITEM_INPUT" -> "non_item_input";
            case "INVALID_ITEM_INPUT" -> "invalid_item_input";
            case "INVALID_REMAINDER" -> "invalid_remainder";
            case "REMAINDER_IS_NOT_REUSABLE_ITEM" -> "remainder_is_not_reusable_item";
            case "RUNTIME_SIMULATION_REQUIRED" -> "runtime_simulation_required";
            case "ONE_TO_ONE_REUSABLE_ITEM_OR_COMPONENT" -> "one_to_one_reusable_item_or_component";
            case "STATIC_ITEM_CONTRACT" -> "static_item_contract";
            case "MULTIPLE" -> "multiple";
            default -> null;
        };
        return key == null
            ? Component.translatable("gui.neoecoae.crafting.fast_path_reason.unknown_code", reason)
            : Component.translatable("gui.neoecoae.crafting.fast_path_reason." + key);
    }

    private static String validationFailureKey(String failure) {
        return switch (failure) {
            case "NULL_COLLECTION" -> "null_collection";
            case "TOO_MANY_ENTRIES" -> "too_many_entries";
            case "EMPTY_REQUIRED" -> "empty_required";
            case "NULL_STACK" -> "null_stack";
            case "INVALID_AMOUNT" -> "invalid_amount";
            case "NON_ITEM_KEY" -> "non_item_key";
            case "EMPTY_ITEM_STACK" -> "empty_item_stack";
            case "DAMAGED_ITEM" -> "damaged_item";
            case "COMPONENT_PATCH" -> "component_patch";
            default -> "unknown_validation";
        };
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

    public static String compactAmount(long value) {
        return HostText.typeProgress(Math.max(0L, value), 0).usedText();
    }

    public static String progressText(ComputationTaskEntry entry) {
        return HostText.percent(entry.progress());
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
        return Math.max(1, Math.min(width, Math.round(Math.clamp(entry.progress(), 0.0F, 1.0F) * width)));
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
        drawString(guiContext, font, text, rightX - font.width(text), y, color);
    }

    private static void drawString(GUIContext guiContext, Font font, String text, int x, int y, int color) {
        guiContext.graphics.drawString(font, text, x, y, color, false);
    }
}
