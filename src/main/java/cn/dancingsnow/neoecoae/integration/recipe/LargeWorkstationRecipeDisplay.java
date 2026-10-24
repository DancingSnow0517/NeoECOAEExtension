package cn.dancingsnow.neoecoae.integration.recipe;

import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Shared information rows and extensible, non-consuming recipe cards for both viewers. */
public final class LargeWorkstationRecipeDisplay {
    public static final int BACKGROUND_HEIGHT = 96;
    public static final int TEXT_X = WorkstationRecipeInfoLayout.TEXT_X;
    public static final float TEXT_SCALE = 0.75f;
    public static final int COLUMNS = 9;
    private static final String PREFIX = "gui.neoecoae.large_integrated_working_station.";
    private static final Map<ResourceLocation, Function<LargeWorkstationRecipe, List<DisplayCard>>> CARD_PROVIDERS = new LinkedHashMap<>();

    private LargeWorkstationRecipeDisplay() {}

    public record DisplayCard(ItemStack stack, List<Component> tooltip) {
        public DisplayCard {
            stack = Objects.requireNonNull(stack).copyWithCount(1);
            tooltip = List.copyOf(tooltip);
        }
    }

    /** Register during client setup, before the recipe viewers build their layouts. Providers must be deterministic. */
    public static void registerCardProvider(ResourceLocation id, Function<LargeWorkstationRecipe, List<DisplayCard>> provider) {
        CARD_PROVIDERS.put(Objects.requireNonNull(id), Objects.requireNonNull(provider));
    }

    public static List<DisplayCard> cards(LargeWorkstationRecipe recipe) {
        var result = new ArrayList<DisplayCard>();
        var upgrade = upgradeCard(recipe);
        if (!upgrade.isEmpty()) result.add(new DisplayCard(upgrade, moduleTooltip(recipe)));
        for (var provider : CARD_PROVIDERS.values()) {
            for (var card : provider.apply(recipe)) {
                if (!card.stack().isEmpty()) result.add(card);
            }
        }
        return List.copyOf(result);
    }

    public static WorkstationRecipeInfoLayout layout(LargeWorkstationRecipe recipe) {
        return new WorkstationRecipeInfoLayout(cards(recipe).size());
    }

    public static int rows(int slots) { return (slots + COLUMNS - 1) / COLUMNS; }
    public static int slotX(int slot) { return 4 + slot % COLUMNS * 18; }
    public static int slotY(LargeWorkstationRecipe recipe, int slot) {
        return layout(recipe).extraSlotsY() + slot / COLUMNS * 18;
    }
    public static int outputY(LargeWorkstationRecipe recipe) {
        return slotY(recipe, 0) + rows(recipe.additionalFluidInputs().size()) * 18;
    }
    public static int height(LargeWorkstationRecipe recipe) {
        return layout(recipe).height(recipe.additionalFluidInputs().size(), recipe.additionalOutputs().size());
    }

    public static String compactAmount(long amount) {
        return BigNumberFormatter.formatSI(BigInteger.valueOf(amount), 1);
    }

    /** Tooltips retain the complete amount even when the table uses a compact unit. */
    public static Component energy(long amount) {
        return Component.translatable(PREFIX + "recipe_energy", amount);
    }
    public static Component energy(LargeWorkstationRecipe recipe) { return energy(recipe.energy()); }
    public static Component extra(GenericStack input) {
        return Component.translatable(PREFIX + "lightning_cost", input.amount(), input.what().getDisplayName());
    }
    public static List<Component> extraCosts(LargeWorkstationRecipe recipe) {
        return recipe.extraInputs().stream().map(LargeWorkstationRecipeDisplay::extra).toList();
    }
    public static Component extraSummary(List<GenericStack> inputs) {
        var text = Component.empty();
        for (var input : inputs) {
            if (!text.getSiblings().isEmpty()) text.append(", ");
            text.append(Component.literal(compactAmount(input.amount()) + "x").append(input.what().getDisplayName()));
        }
        return text;
    }

    public static ItemStack upgradeCard(LargeWorkstationRecipe recipe) {
        var module = LargeWorkstationModule.fromSourceType(recipe.sourceType());
        return module == LargeWorkstationModule.REGULAR ? ItemStack.EMPTY
            : BuiltInRegistries.ITEM.getOptional(module.upgradeCardId()).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    public static List<Component> moduleTooltip(LargeWorkstationRecipe recipe) {
        var card = upgradeCard(recipe);
        return List.of(Component.translatable(PREFIX + "module_support"),
            card.isEmpty() ? Component.translatable(PREFIX + "no_module_required")
                : Component.translatable(PREFIX + "module_required", card.getHoverName()),
            recipe.sourceTypeDescription());
    }
    public static List<Component> infoTooltip(LargeWorkstationRecipe recipe) {
        var result = new ArrayList<Component>();
        result.add(energy(recipe));
        result.addAll(extraCosts(recipe));
        result.addAll(moduleTooltip(recipe));
        return result;
    }
    public static List<Component> tooltip(LargeWorkstationRecipe recipe, double x, double y) {
        return layout(recipe).containsText(x, y) ? infoTooltip(recipe) : List.of();
    }

    public static void drawInfo(GuiGraphics graphics, LargeWorkstationRecipe recipe) {
        drawInfo(graphics, Minecraft.getInstance().font, recipe);
    }
    public static void drawInfo(GuiGraphics graphics, Font font, LargeWorkstationRecipe recipe) {
        var layout = layout(recipe);
        if (layout.extraHeight() > 0) {
            int bottom = WorkstationRecipeInfoLayout.INFO_Y + layout.infoHeight();
            // Extend the artist's recessed footer only when the cards need more than one row.
            graphics.fill(4, 72, 164, bottom, 0xFFF2F2F2);
            graphics.fill(5, 73, 163, bottom - 1, 0xFF9A9FB4);
            graphics.fill(6, 74, 163, bottom - 1, 0xFFADB0C4);
        }
        int right = TEXT_X + layout.textWidth() + 1;
        graphics.fill(TEXT_X - 1, layout.energyY() - 1, right, layout.energyY() + 7, 0xFFADB0C4);
        graphics.fill(TEXT_X - 1, layout.extraY() - 1, right, layout.extraY() + 7, 0xFF9A9FB4);
        drawRow(graphics, font, Component.translatable(PREFIX + "recipe_energy_label"),
            Component.literal(compactAmount(recipe.energy()) + " AE"), layout.energyY(), layout.textWidth());
        if (!recipe.extraInputs().isEmpty()) {
            drawRow(graphics, font, Component.translatable(PREFIX + "extra_required_label"),
                extraSummary(recipe.extraInputs()), layout.extraY(), layout.textWidth());
        }
    }

    private static void drawRow(GuiGraphics graphics, Font font, Component label, Component value, int y, int width) {
        int availableWidth = (int) (width / TEXT_SCALE);
        String labelText = clipped(font, label, availableWidth / 2);
        String valueText = clipped(font, value, availableWidth - font.width(labelText) - 8);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(TEXT_X, y, 0);
        pose.scale(TEXT_SCALE, TEXT_SCALE, 1);
        graphics.drawString(font, labelText, 0, 0, 0x555563, false);
        graphics.drawString(font, valueText, availableWidth - font.width(valueText), 0, 0x242432, false);
        pose.popPose();
    }
    private static String clipped(Font font, Component text, int width) {
        String value = text.getString();
        return font.width(value) <= width ? value : font.plainSubstrByWidth(value, width - font.width("…")) + "…";
    }
}
