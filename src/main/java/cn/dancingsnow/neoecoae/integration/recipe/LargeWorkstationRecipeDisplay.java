package cn.dancingsnow.neoecoae.integration.recipe;

import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.integration.appflux.WorkstationFluxEnergy;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipeGroup;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import cn.dancingsnow.neoecoae.recipe.WorkstationEnergy;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

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
        return cards(recipe, group(recipe));
    }

    /** Supplies the grouped backend data to the existing dynamic card layout. */
    public static List<DisplayCard> cards(LargeWorkstationRecipe recipe, @Nullable LargeWorkstationRecipeGroup group) {
        var result = new ArrayList<DisplayCard>();
        if (group == null) {
            var upgrade = upgradeCard(recipe);
            if (!upgrade.isEmpty()) result.add(new DisplayCard(upgrade, moduleTooltip(recipe, null)));
        } else {
            for (var module : group.supportedModules()) {
                var cardId = ECOWorkstationModules.resolve(module).upgradeCard();
                var stack = BuiltInRegistries.ITEM.getOptional(cardId).map(ItemStack::new).orElse(ItemStack.EMPTY);
                if (stack.isEmpty()) continue;
                result.add(new DisplayCard(stack, variantsTooltip(recipe, group.availableVariants(module))));
            }
        }
        for (var provider : CARD_PROVIDERS.values()) {
            for (var card : provider.apply(recipe)) {
                if (!card.stack().isEmpty()) result.add(card);
            }
        }
        return List.copyOf(result);
    }

    public static WorkstationRecipeInfoLayout layout(LargeWorkstationRecipe recipe) {
        return new WorkstationRecipeInfoLayout(cards(recipe).size(), infoRows(recipe).size());
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
        return Component.translatable(PREFIX + "recipe_energy_value", energyAmount(BigDecimal.valueOf(amount), false, false));
    }
    public static Component energy(LargeWorkstationRecipe recipe) {
        return Component.translatable(PREFIX + "recipe_energy_value", energyValue(recipe, false));
    }

    public static Component energyValue(LargeWorkstationRecipe recipe, boolean compact) {
        var ae = energyAmount(BigDecimal.valueOf(recipe.energy()), compact, false);
        var fe = energyAmount(BigDecimal.valueOf(recipe.feEnergy()), compact, true);
        if (recipe.feEnergy() == 0) return ae;
        if (recipe.feAlternative()) return Component.translatable(PREFIX + "energy_or", ae, fe);
        if (!WorkstationFluxEnergy.available()) {
            return energyAmount(BigDecimal.valueOf(recipe.energy()).add(WorkstationEnergy.aeEquivalent(recipe.feEnergy())), compact, false);
        }
        if (recipe.energy() == 0) return fe;
        return Component.translatable(PREFIX + (recipe.feAlternative() ? "energy_or" : "energy_and"), ae, fe);
    }

    /** The summary prefers FE when its storage integration is installed. */
    public static Component summaryEnergy(LargeWorkstationRecipe recipe) {
        if (recipe.feEnergy() > 0 && WorkstationFluxEnergy.available()
            && (recipe.energy() == 0 || recipe.feAlternative())) {
            return energyAmount(BigDecimal.valueOf(recipe.feEnergy()), true, true);
        }
        if (recipe.feAlternative()) return energyAmount(BigDecimal.valueOf(recipe.energy()), true, false);
        return energyValue(recipe, true);
    }

    private static Component energyAmount(BigDecimal amount, boolean compact, boolean fe) {
        String number = compact && amount.compareTo(BigDecimal.valueOf(1000)) >= 0
            ? BigNumberFormatter.formatSI(amount.toBigInteger(), 1)
            : DisplayNumbers.grouped(amount.stripTrailingZeros().toPlainString());
        return Component.literal(number + " ").append(Component.literal(fe ? "FE" : "AE")
            .withStyle(fe ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_PURPLE));
    }

    public static Component duration(LargeWorkstationRecipe recipe) {
        return Component.translatable(PREFIX + "recipe_duration", recipe.processingTicks(),
            BigDecimal.valueOf(recipe.processingTicks()).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString());
    }

    private record InfoRow(Component label, Component value) {}

    private static boolean hasEnergyCost(LargeWorkstationRecipe recipe) {
        return recipe.energy() > 0 || recipe.feEnergy() > 0;
    }

    /** Keep the representative batch compact; alternate routes and FE details belong in tooltips. */
    private static List<InfoRow> infoRows(LargeWorkstationRecipe recipe) {
        var rows = new ArrayList<InfoRow>();
        if (hasEnergyCost(recipe)) {
            rows.add(new InfoRow(Component.translatable(PREFIX + "recipe_energy_label"),
                summaryEnergy(recipe)));
        }
        rows.add(new InfoRow(Component.translatable(PREFIX + "time_label"),
            Component.literal(recipe.processingTicks() + " t (" + recipe.processingTicks() / 20.0 + " s)")));
        if (!recipe.extraInputs().isEmpty()) rows.add(new InfoRow(Component.translatable(PREFIX + "extra_required_label"), extraSummary(recipe.extraInputs())));
        return rows;
    }
    public static Component extra(GenericStack input) {
        return Component.translatable(PREFIX + "lightning_cost", input.amount(), input.what().getDisplayName());
    }
    public static List<Component> extraCosts(LargeWorkstationRecipe recipe) {
        return recipe.extraInputs().stream().map(LargeWorkstationRecipeDisplay::extra).toList();
    }
    public static Component extraSummary(List<GenericStack> inputs) {
        return resourceSummary(inputs, true);
    }
    private static Component resourceSummary(List<GenericStack> inputs, boolean compact) {
        var text = Component.empty();
        for (var input : inputs) {
            if (!text.getSiblings().isEmpty()) text.append(", ");
            String amount = compact ? compactAmount(input.amount()) : Long.toString(input.amount());
            text.append(Component.literal(amount + "x").append(input.what().getDisplayName()));
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
        return moduleTooltip(recipe, group(recipe));
    }

    private static List<Component> moduleTooltip(LargeWorkstationRecipe recipe, @Nullable LargeWorkstationRecipeGroup group) {
        if (group != null) return variantsTooltip(recipe, group.displayVariants());
        var module = ECOWorkstationModules.fromSourceType(recipe.sourceType());
        var result = new ArrayList<Component>();
        addVariant(result, recipe, module.title().copy().withColor(module.color()), false);
        addFluxHint(result, recipe.feEnergy() > 0);
        return List.copyOf(result);
    }

    private static Component moduleNames(List<ResourceLocation> modules) {
        var result = Component.empty();
        for (var module : modules) {
            if (!result.getSiblings().isEmpty()) result.append(" / ");
            var description = ECOWorkstationModules.resolve(module);
            result.append(description.title().copy().withColor(description.color()));
        }
        return result;
    }

    public static Component variantCost(LargeWorkstationRecipeGroup.Variant variant) {
        return Component.translatable(PREFIX + "recipe_variant_details", variantTitle(variant), energyValue(variant.recipe(), false),
            duration(variant.recipe()), resourceSummary(variant.recipe().outputs(), false));
    }

    private static Component variantTitle(LargeWorkstationRecipeGroup.Variant variant) {
        return variant.unrestricted() ? LargeWorkstationModule.REGULAR.title().copy().withColor(LargeWorkstationModule.REGULAR.color())
            : moduleNames(variant.modules().stream().sorted(java.util.Comparator.comparing(ResourceLocation::toString)).toList());
    }

    private static List<Component> variantsTooltip(LargeWorkstationRecipe representative, List<LargeWorkstationRecipeGroup.Variant> variants) {
        var result = new ArrayList<Component>();
        for (var variant : variants) {
            if (!result.isEmpty()) result.add(Component.empty());
            addVariant(result, variant.recipe(), variantTitle(variant), !variant.recipe().outputs().equals(representative.outputs()));
        }
        addFluxHint(result, variants.stream().anyMatch(variant -> variant.recipe().feEnergy() > 0));
        return List.copyOf(result);
    }

    private static void addVariant(List<Component> lines, LargeWorkstationRecipe recipe, Component title, boolean showBatch) {
        lines.add(title);
        if (hasEnergyCost(recipe)) lines.add(Component.literal("  ").append(energy(recipe)));
        lines.add(detailLine("time_label", duration(recipe)));
        if (showBatch) lines.add(detailLine("batch_label", resourceTooltip(recipe.outputs())));
        if (!recipe.extraInputs().isEmpty()) lines.add(detailLine("extra_required_label", resourceTooltip(recipe.extraInputs())));
    }

    private static Component detailLine(String label, Component value) {
        return Component.literal("  ").append(Component.translatable(PREFIX + label)).append(": ").append(value);
    }

    private static Component resourceTooltip(List<GenericStack> stacks) {
        var result = Component.empty();
        for (var stack : stacks) {
            if (!result.getSiblings().isEmpty()) result.append(", ");
            result.append(stack.what().getDisplayName().copy()).append(" × " + DisplayNumbers.grouped(Long.toString(stack.amount())));
        }
        return result;
    }

    private static void addFluxHint(List<Component> lines, boolean usesFe) {
        if (!usesFe) return;
        lines.add(Component.empty());
        var unit = Component.literal("FE").withStyle(ChatFormatting.DARK_GREEN);
        lines.add(Component.translatable(PREFIX + "fe_supply_hint", unit).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(PREFIX + "fe_storage_hint", unit.copy()).withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    private static LargeWorkstationRecipeGroup group(LargeWorkstationRecipe recipe) {
        var client = Minecraft.getInstance();
        return client == null || client.level == null ? null : LargeWorkstationRecipes.getGroup(client.level, recipe.id());
    }
    public static List<Component> infoTooltip(LargeWorkstationRecipe recipe) {
        return moduleTooltip(recipe);
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
            // Extend the artist's recessed footer to fit the summary and any additional card rows.
            graphics.fill(4, 72, 164, bottom, 0xFFF2F2F2);
            graphics.fill(5, 73, 163, bottom - 1, 0xFF9A9FB4);
            graphics.fill(6, 74, 163, bottom - 1, 0xFFADB0C4);
        }
        int right = TEXT_X + layout.textWidth() + 1;
        var rows = infoRows(recipe);
        for (int i = 0; i < rows.size(); i++) {
            int y = layout.energyY() + i * WorkstationRecipeInfoLayout.ROW_HEIGHT;
            graphics.fill(TEXT_X - 1, y - 1, right, y + 7, i % 2 == 0 ? 0xFFADB0C4 : 0xFF9A9FB4);
            drawRow(graphics, font, rows.get(i).label(), rows.get(i).value(), y, layout.textWidth());
        }
    }

    private static void drawRow(GuiGraphics graphics, Font font, Component label, Component value, int y, int width) {
        int availableWidth = (int) (width / TEXT_SCALE);
        var labelText = clipped(font, label, availableWidth / 2);
        var valueText = clipped(font, value, availableWidth - font.width(labelText) - 8);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(TEXT_X, y, 0);
        pose.scale(TEXT_SCALE, TEXT_SCALE, 1);
        graphics.drawString(font, Language.getInstance().getVisualOrder(labelText), 0, 0, 0x555563, false);
        graphics.drawString(font, Language.getInstance().getVisualOrder(valueText), availableWidth - font.width(valueText), 0, 0x242432, false);
        pose.popPose();
    }
    private static FormattedText clipped(Font font, Component text, int width) {
        return font.width(text) <= width ? text
            : FormattedText.composite(font.substrByWidth(text, Math.max(0, width - font.width("…"))), Component.literal("…"));
    }
}
