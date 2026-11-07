package cn.dancingsnow.neoecoae.client.gui.crafting;

import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.me.common.ClientDisplaySlot;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import appeng.menu.me.crafting.CraftConfirmMenu;
import java.util.ArrayList;
import java.util.List;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Shows AE2 crafting submission errors while preserving the ECO confirmation screen as its parent. */
public final class ECOCraftErrorScreen extends AESubScreen<CraftConfirmMenu, ECOCraftConfirmScreen> {
    private ClientDisplaySlot missingIngredientSlot;
    private GenericStack missingIngredient;

    public ECOCraftErrorScreen(ECOCraftConfirmScreen parent, CraftingSubmitErrorCode errorCode, Object details) {
        super(parent, "/screens/craft_error.json");

        var errorText = switch (errorCode) {
            case INCOMPLETE_PLAN -> GuiText.CraftErrorIncompletePlan.text();
            case NO_CPU_FOUND -> GuiText.CraftErrorNoCpuFound.text();
            case NO_SUITABLE_CPU_FOUND -> {
                MutableComponent text = GuiText.CraftErrorNoSuitableCpu.text();
                if (details instanceof UnsuitableCpus unsuitableCpus) {
                    var stats = new ArrayList<Component>();
                    if (unsuitableCpus.offline() > 0) {
                        stats.add(GuiText.CraftErrorNoSuitableCpuOffline.text(unsuitableCpus.offline()));
                    }
                    if (unsuitableCpus.busy() > 0) {
                        stats.add(GuiText.CraftErrorNoSuitableCpuBusy.text(unsuitableCpus.busy()));
                    }
                    if (unsuitableCpus.tooSmall() > 0) {
                        stats.add(GuiText.CraftErrorNoSuitableCpuTooSmall.text(unsuitableCpus.tooSmall()));
                    }
                    if (unsuitableCpus.excluded() > 0) {
                        stats.add(GuiText.CraftErrorNoSuitableCpuExcluded.text(unsuitableCpus.excluded()));
                    }

                    MutableComponent suffix = Component.literal("(");
                    for (int i = 0; i < stats.size(); i++) {
                        if (i != 0) {
                            suffix.append(", ");
                        }
                        suffix.append(stats.get(i));
                    }
                    suffix.append(")");
                    text.append(" ").append(suffix);
                }

                yield text;
            }
            case CPU_BUSY -> GuiText.CraftErrorCpuBusy.text();
            case CPU_OFFLINE -> GuiText.CraftErrorCpuOffline.text();
            case CPU_TOO_SMALL -> GuiText.CraftErrorCpuTooSmall.text();
            case MISSING_INGREDIENT -> {
                if (details instanceof GenericStack missing) {
                    missingIngredient = missing;
                    missingIngredientSlot = new ClientDisplaySlot(missing);
                    addClientSideSlot(missingIngredientSlot, SlotSemantics.MISSING_INGREDIENT);
                }

                yield GuiText.CraftErrorMissingIngredient.text();
            }
        };

        setTextContent("errorText", errorText);

        widgets.addButton("replan", GuiText.CraftErrorReplan.text(), () -> {
            returnToParent();
            menu.replan();
        });
        widgets.addButton("retry", GuiText.CraftErrorRetry.text(), () -> {
            returnToParent();
            parent.retrySubmission();
        });
        widgets.addButton("cancel", GuiText.Cancel.text(), () -> {
            returnToParent();
            menu.goBack();
        });
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (missingIngredient != null && getSlotUnderMouse() == missingIngredientSlot
                && menu.getCarried().isEmpty()) {
            // AE2 reports requested minus extracted, after attempting to return the taken inputs.
            // It does not report which storage backend refused extraction or why.
            List<Component> lines = new ArrayList<>(AEKeyRendering.getTooltip(missingIngredient.what()));
            lines.add(Component.empty());
            lines.add(Component.translatable("gui.neoecoae.craft_error.shortfall",
                DisplayNumbers.grouped(missingIngredient.what().formatAmount(
                    missingIngredient.amount(), AmountFormat.FULL))).withStyle(ChatFormatting.RED));
            for (String detail : List.of("meaning", "unknown_cause", "stock_changed", "storage_offline",
                    "extraction_blocked", "transfer_limit", "retry", "replan")) {
                lines.add(Component.translatable("gui.neoecoae.craft_error." + detail)
                    .withStyle(detail.equals("unknown_cause") ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            }
            drawTooltipWithHeader(graphics, mouseX, mouseY, lines);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void onReturnToParent() {
        menu.clearError();
    }
}
