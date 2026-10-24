package cn.dancingsnow.neoecoae.integration.recipe;

import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Shared information-strip layout for both recipe viewers. */
public final class LargeWorkstationRecipeDisplay {
    public static final int BACKGROUND_HEIGHT = 96;
    public static final int CARD_X = 146;
    public static final int CARD_Y = 74;
    public static final int TEXT_X = 7;
    public static final int TEXT_WIDTH = 137;
    public static final int ENERGY_Y = 74;
    public static final int EXTRA_Y = 83;
    public static final int EXTRA_SLOTS_Y = 100;
    public static final int COLUMNS = 9;
    private static final String PREFIX = "gui.neoecoae.large_integrated_working_station.";

    private LargeWorkstationRecipeDisplay() {}

    public static Component energy(LargeWorkstationRecipe recipe) {
        return Component.translatable(PREFIX + "recipe_energy", recipe.energy());
    }

    public static List<Component> extraCosts(LargeWorkstationRecipe recipe) {
        return recipe.extraInputs().stream().map(input -> (Component) Component.translatable(
            PREFIX + "lightning_cost", input.amount(), input.what().getDisplayName())).toList();
    }

    public static Component extraSummary(LargeWorkstationRecipe recipe) {
        var summary = Component.empty();
        for (var line : extraCosts(recipe)) {
            if (!summary.getSiblings().isEmpty()) summary.append("; ");
            summary.append(line);
        }
        return summary;
    }

    public static ItemStack upgradeCard(LargeWorkstationRecipe recipe) {
        var module = LargeWorkstationModule.fromSourceType(recipe.sourceType());
        return module == LargeWorkstationModule.REGULAR ? ItemStack.EMPTY
            : new ItemStack(BuiltInRegistries.ITEM.get(module.upgradeCardId()));
    }

    public static List<Component> moduleTooltip(LargeWorkstationRecipe recipe) {
        var result = new ArrayList<Component>();
        var module = LargeWorkstationModule.fromSourceType(recipe.sourceType());
        if (module == LargeWorkstationModule.REGULAR) {
            result.add(Component.translatable(PREFIX + "no_module_required"));
        } else {
            result.add(Component.translatable(PREFIX + "module_support"));
            result.add(Component.translatable(PREFIX + "module_required", module.title()));
        }
        result.add(recipe.sourceTypeDescription());
        return result;
    }

    public static Component clipped(Font font, Component text) {
        if (font.width(text) <= TEXT_WIDTH) return text;
        return Component.literal(font.plainSubstrByWidth(text.getString(), TEXT_WIDTH - font.width("...")) + "...");
    }

    public static void drawInfo(GuiGraphics graphics, Font font, LargeWorkstationRecipe recipe) {
        graphics.drawString(font, clipped(font, energy(recipe)), TEXT_X, ENERGY_Y, 0x403e53, false);
        graphics.drawString(font, clipped(font, extraSummary(recipe)), TEXT_X, EXTRA_Y, 0x403e53, false);
    }

    public static List<Component> tooltip(LargeWorkstationRecipe recipe, double mouseX, double mouseY) {
        if (mouseX >= TEXT_X && mouseX < TEXT_X + TEXT_WIDTH) {
            if (mouseY >= ENERGY_Y && mouseY < EXTRA_Y) return List.of(energy(recipe));
            if (mouseY >= EXTRA_Y && mouseY < EXTRA_Y + 9) return extraCosts(recipe);
        }
        if (mouseX >= CARD_X && mouseX < CARD_X + 16 && mouseY >= CARD_Y && mouseY < CARD_Y + 16) {
            return moduleTooltip(recipe);
        }
        return List.of();
    }

    public static int rows(int count) { return (count + COLUMNS - 1) / COLUMNS; }
    public static int outputY(LargeWorkstationRecipe recipe) {
        return EXTRA_SLOTS_Y + rows(recipe.additionalFluidInputs().size()) * 18;
    }
    public static int height(LargeWorkstationRecipe recipe) {
        int rows = rows(recipe.additionalFluidInputs().size()) + rows(recipe.additionalOutputs().size());
        return rows == 0 ? BACKGROUND_HEIGHT : EXTRA_SLOTS_Y + rows * 18;
    }
}
