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
import com.tterrag.registrate.util.nullness.NonNullBiFunction;
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
        L4(ECOTier.L4, "16m", Rarity.UNCOMMON),
        L6(ECOTier.L6, "64m", Rarity.RARE),
        L9(ECOTier.L9, "256m", Rarity.EPIC);

        private final ECOTier tier;
        private final String suffix;
        private final Rarity rarity;

        Size(ECOTier tier, String suffix, Rarity rarity) {
            this.tier = tier;
            this.suffix = suffix;
            this.rarity = rarity;
        }

        public ECOTier tier() {
            return tier;
        }

        /** Size suffix used in ids and unlock criteria, e.g. {@code "16m"}. */
        public String suffix() {
            return suffix;
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
     * Starts a cell item builder with the common stack size and rarity for {@code size}. The factory
     * receives the tier of {@code size}.
     * Chain {@code .recipe(...)}, {@code .lang(...)}, {@code .model(...)} and {@code .register()} as usual.
     */
    public static <T extends Item> ItemBuilder<T, NERegistrate> cell(String id, Size size, NonNullBiFunction<Item.Properties, ECOTier, T> factory) {
        return REGISTRATE.item(id, p -> factory.apply(p, size.tier())).properties(p -> p.stacksTo(1).rarity(size.rarity()));
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
     * Maps the L4, L6 and L9 cells, in that order, to their model ids. The model paths are passed in
     * literally so each integration shows the resource ids it registers.
     */
    public static void registerModels(List<? extends ItemEntry<? extends Item>> cells, String l4Model, String l6Model, String l9Model) {
        if (cells.size() != 3) {
            throw new IllegalArgumentException("Expected 3 cells (L4, L6, L9), got " + cells.size());
        }
        ECOCellModels.register(cells.get(0), NeoECOAE.id(l4Model));
        ECOCellModels.register(cells.get(1), NeoECOAE.id(l6Model));
        ECOCellModels.register(cells.get(2), NeoECOAE.id(l9Model));
    }
}
