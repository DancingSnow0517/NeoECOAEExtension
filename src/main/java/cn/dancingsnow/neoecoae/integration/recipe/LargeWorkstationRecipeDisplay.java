package cn.dancingsnow.neoecoae.integration.recipe;

import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Shared presentation of the artist's information bar in JEI and EMI. */
public final class LargeWorkstationRecipeDisplay {
    public static final int WIDTH = 168;
    public static final int BACKGROUND_HEIGHT = 96;
    public static final int INFO_X = 4;
    public static final int INFO_Y = 72;
    public static final int INFO_WIDTH = 160;
    public static final int INFO_HEIGHT = 20;
    public static final int CARD_SIZE = 16;
    public static final int CARD_PADDING = (INFO_HEIGHT - CARD_SIZE) / 2;
    public static final int CARD_X = INFO_X + INFO_WIDTH - CARD_PADDING - CARD_SIZE;
    public static final int CARD_Y = INFO_Y + CARD_PADDING;
    public static final int TEXT_X = 7;
    public static final int TEXT_WIDTH = CARD_X - TEXT_X - 2;
    public static final float TEXT_SCALE = 0.75f;
    public static final int ENERGY_Y = 74;
    public static final int EXTRA_Y = 84;
    public static final int DIVIDER_Y = 82;
    public static final int EXTRA_SLOTS_Y = 100;
    public static final int COLUMNS = 9;
    private static final int COLOR = 0x403e53;
    private static final String PREFIX = "gui.neoecoae.large_integrated_working_station.";

    private LargeWorkstationRecipeDisplay() {}

    public static int rows(int slots) { return (slots + COLUMNS - 1) / COLUMNS; }
    public static int slotX(int slot) { return 4 + slot % COLUMNS * 18; }
    public static int slotY(int slot) { return EXTRA_SLOTS_Y + slot / COLUMNS * 18; }

    public static int height(int fluidInputs, int outputs) {
        int rows = rows(fluidInputs) + rows(outputs);
        return rows == 0 ? BACKGROUND_HEIGHT : EXTRA_SLOTS_Y + rows * 18;
    }

    public static Component energy(long amount) {
        return Component.translatable(PREFIX + "recipe_energy", amount);
    }

    public static Component extra(GenericStack input) {
        return Component.translatable(PREFIX + "lightning_cost", input.amount(), input.what().getDisplayName());
    }

    public static Component extraSummary(List<GenericStack> inputs) {
        var text = Component.empty();
        for (var input : inputs) {
            if (!text.getSiblings().isEmpty()) text.append(", ");
            text.append(extra(input));
        }
        return text;
    }

    public static ItemStack upgradeCard(LargeWorkstationRecipe recipe) {
        var module = ECOWorkstationModules.fromSourceType(recipe.sourceType());
        if (!module.custom() && (module.legacy() == LargeWorkstationModule.REGULAR || !module.legacy().hasUpgradeCard())) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(module.upgradeCard()).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    public static List<Component> moduleTooltip(LargeWorkstationRecipe recipe) {
        var card = upgradeCard(recipe);
        return List.of(Component.translatable(PREFIX + "module_support"),
            card.isEmpty() ? Component.translatable(PREFIX + "no_module_required")
                : Component.translatable(PREFIX + "module_required", card.getHoverName()),
            recipe.sourceTypeDescription());
    }

    public static List<Component> infoTooltip(LargeWorkstationRecipe recipe) {
        var text = new ArrayList<Component>();
        text.add(energy(recipe.energy()));
        for (var input : recipe.extraInputs()) text.add(extra(input));
        text.addAll(moduleTooltip(recipe));
        return text;
    }

    public static void drawInfo(GuiGraphics graphics, LargeWorkstationRecipe recipe) {
        graphics.fill(TEXT_X, DIVIDER_Y, TEXT_X + TEXT_WIDTH, DIVIDER_Y + 1, 0xFF878FA5);
        drawLine(graphics, energy(recipe.energy()), ENERGY_Y);
        if (!recipe.extraInputs().isEmpty()) drawLine(graphics, extraSummary(recipe.extraInputs()), EXTRA_Y);
    }

    private static void drawLine(GuiGraphics graphics, Component component, int y) {
        var font = Minecraft.getInstance().font;
        int availableWidth = (int) (TEXT_WIDTH / TEXT_SCALE);
        String text = component.getString();
        if (font.width(text) > availableWidth) {
            text = font.plainSubstrByWidth(text, availableWidth - font.width("…")) + "…";
        }
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(TEXT_X, y, 0);
        pose.scale(TEXT_SCALE, TEXT_SCALE, 1);
        graphics.drawString(font, text, 0, 0, COLOR, false);
        pose.popPose();
    }
}
