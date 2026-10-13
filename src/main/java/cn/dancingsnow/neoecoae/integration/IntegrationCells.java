package cn.dancingsnow.neoecoae.integration;

import appeng.recipes.game.StorageCellDisassemblyRecipe;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

/**
 * Shared Registrate and datagen helpers for the optional integrations' storage cells.
 * Each integration ships three cells (16m / 64m / 256m) that differ only in tier, rarity,
 * upgrade component and model family.
 */
public final class IntegrationCells {
    private IntegrationCells() {
    }

    /** The three cell sizes an integration ships, in L4, L6, L9 order. */
    public enum Size {
        L4(ECOTier.L4, "16m", "l4", Rarity.UNCOMMON),
        L6(ECOTier.L6, "64m", "l6", Rarity.RARE),
        L9(ECOTier.L9, "256m", "l9", Rarity.EPIC);

        private final ECOTier tier;
        private final String suffix;
        private final String modelTier;
        private final Rarity rarity;

        Size(ECOTier tier, String suffix, String modelTier, Rarity rarity) {
            this.tier = tier;
            this.suffix = suffix;
            this.modelTier = modelTier;
            this.rarity = rarity;
        }

        public ECOTier tier() {
            return tier;
        }

        /** Size suffix used in ids and unlock criteria, e.g. {@code "16m"}. */
        public String suffix() {
            return suffix;
        }

        /** Tier name used in model paths, e.g. {@code "l4"}. */
        public String modelTier() {
            return modelTier;
        }

        public Rarity rarity() {
            return rarity;
        }

        /**
         * The ECO cell component for this size. A method rather than a constructor argument so the
         * {@code NEItems} fields are only read at datagen or recipe time, not during enum init.
         */
        public ItemEntry<?> component() {
            return switch (this) {
                case L4 -> NEItems.ECO_CELL_COMPONENT_16M;
                case L6 -> NEItems.ECO_CELL_COMPONENT_64M;
                case L9 -> NEItems.ECO_CELL_COMPONENT_256M;
            };
        }
    }

    /**
     * Starts a cell item builder with the common stack size and rarity for {@code size}.
     * Chain {@code .recipe(...)}, {@code .lang(...)}, {@code .model(...)} and {@code .register()} as usual.
     */
    public static <T extends Item> ItemBuilder<T, NERegistrate> cell(String id, Size size, NonNullFunction<Item.Properties, T> factory) {
        return REGISTRATE.item(id, factory).properties(p -> p.stacksTo(1).rarity(size.rarity()));
    }

    /**
     * Recipe pair for an integration cell, gated on {@code modId} being loaded: a shapeless
     * {@code housing + component} recipe, and its storage-cell disassembly. The unlock criterion is
     * {@code has_<suffix>_component}.
     */
    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateRecipeProvider> housingRecipe(String modId, ItemLike housing, Size size) {
        return (ctx, prov) -> {
            RecipeOutput installed = prov.withConditions(new ModLoadedCondition(modId));
            ItemEntry<?> component = size.component();
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(housing)
                .requires(component)
                .unlockedBy("has_" + size.suffix() + "_component", RegistrateRecipeProvider.has(component))
                .save(installed);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(
                ctx.get(), List.of(new ItemStack(housing), component.asStack()));
            installed.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        };
    }

    /**
     * Registers the cell models for one family. {@code cells} must be the L4, L6 and L9 cells in that order;
     * each is mapped to {@code block/cell/storage_cell_<tier>_<family>}.
     */
    public static void registerModels(String family, List<? extends ItemEntry<? extends Item>> cells) {
        Size[] sizes = Size.values();
        if (cells.size() != sizes.length) {
            throw new IllegalArgumentException("Expected " + sizes.length + " cells (L4, L6, L9) for family " + family + ", got " + cells.size());
        }
        for (int i = 0; i < sizes.length; i++) {
            ECOCellModels.register(cells.get(i), NeoECOAE.id("block/cell/storage_cell_" + sizes[i].modelTier() + "_" + family));
        }
    }
}
