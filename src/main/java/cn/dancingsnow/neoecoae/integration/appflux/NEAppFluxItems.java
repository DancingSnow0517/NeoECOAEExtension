package cn.dancingsnow.neoecoae.integration.appflux;

import appeng.items.materials.MaterialItem;
import appeng.core.definitions.AEItems;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.appflux.item.ECOFeStorageCellItem;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.glodblock.github.appflux.common.AFSingletons;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEAppFluxItems {

    private static final long LE4_CAPACITY = 1L << 28;
    private static final long LE6_CAPACITY = 1L << 30;
    private static final long LE9_CAPACITY = 1L << 32;

    public static final ItemEntry<MaterialItem> ECO_FE_CELL_HOUSING = REGISTRATE
        .item("eco_fe_cell_housing", MaterialItem::new)
        .recipe((ctx, prov) -> {
            RecipeOutput appFluxInstalled = prov.withConditions(new ModLoadedCondition("appflux"));
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("B B")
                .pattern("CCC")
                .define('A', NEItems.CRYSTAL_MATRIX)
                .define('B', Tags.Items.DUSTS_REDSTONE)
                .define('C', AFSingletons.HARDEN_INSULATING_RESIN)
                .unlockedBy("has_crystal_matrix", RegistrateRecipeProvider.has(NEItems.CRYSTAL_MATRIX))
                .unlockedBy("has_redstone", RegistrateRecipeProvider.has(Tags.Items.DUSTS_REDSTONE))
                .unlockedBy("has_harden_insulating_resin", RegistrateRecipeProvider.has(AFSingletons.HARDEN_INSULATING_RESIN))
                .save(appFluxInstalled);
        })
        .lang("ECO Storage Matrix Housing (FE)")
        .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_16M = REGISTRATE
        .item("eco_fe_storage_cell_16m", p -> new ECOFeStorageCellItem(
            p,
            ECOTier.L4,
            NEAppFluxCellTypes.FLUX,
            LE4_CAPACITY
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.UNCOMMON))
        .recipe((ctx, prov) -> {
            RecipeOutput appFluxInstalled = prov.withConditions(new ModLoadedCondition("appflux"));
            IntegratedWorkingStationRecipe.builder()
                .require(AFSingletons.CORE_16M, 10)
                .require(AFSingletons.ENERGY_PROCESSOR, 2)
                .require(AEItems.SINGULARITY)
                .require(ECO_FE_CELL_HOUSING)
                .energy(1_000)
                .itemOutput(ctx.get())
                .save(appFluxInstalled, ctx.getId().withPrefix("integrated_working_station/"));
            appFluxInstalled.accept(
                ctx.getId().withPrefix("disassembly/"),
                new StorageCellDisassemblyRecipe(ctx.get(), List.of(new ItemStack(AFSingletons.CORE_16M, 10))),
                null
            );
        })
        .lang("ECO - LE4 Storage Matrix (FE)")
        .model(ItemModelUtil.cellModel("fe", "16m"))
        .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_64M = REGISTRATE
        .item("eco_fe_storage_cell_64m", p -> new ECOFeStorageCellItem(
            p,
            ECOTier.L6,
            NEAppFluxCellTypes.FLUX,
            LE6_CAPACITY
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.RARE))
        .recipe((ctx, prov) -> {
            RecipeOutput appFluxInstalled = prov.withConditions(new ModLoadedCondition("appflux"));
            IntegratedWorkingStationRecipe.builder()
                .require(AFSingletons.CORE_64M, 10)
                .require(AFSingletons.ENERGY_PROCESSOR, 4)
                .require(AEItems.SINGULARITY, 4)
                .require(ECO_FE_CELL_HOUSING)
                .energy(12_000)
                .itemOutput(ctx.get())
                .save(appFluxInstalled, ctx.getId().withPrefix("integrated_working_station/"));
            appFluxInstalled.accept(
                ctx.getId().withPrefix("disassembly/"),
                new StorageCellDisassemblyRecipe(ctx.get(), List.of(new ItemStack(AFSingletons.CORE_64M, 10))),
                null
            );
        })
        .lang("ECO - LE6 Storage Matrix (FE)")
        .model(ItemModelUtil.cellModel("fe", "64m"))
        .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_256M = REGISTRATE
        .item("eco_fe_storage_cell_256m", p -> new ECOFeStorageCellItem(
            p,
            ECOTier.L9,
            NEAppFluxCellTypes.FLUX,
            LE9_CAPACITY
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.EPIC))
        .recipe((ctx, prov) -> {
            RecipeOutput appFluxInstalled = prov.withConditions(new ModLoadedCondition("appflux"));
            IntegratedWorkingStationRecipe.builder()
                .require(AFSingletons.CORE_256M, 10)
                .require(AFSingletons.ENERGY_PROCESSOR, 8)
                .require(AEItems.SINGULARITY, 16)
                .require(ECO_FE_CELL_HOUSING)
                .energy(144_000)
                .itemOutput(ctx.get())
                .save(appFluxInstalled, ctx.getId().withPrefix("integrated_working_station/"));
            appFluxInstalled.accept(
                ctx.getId().withPrefix("disassembly/"),
                new StorageCellDisassemblyRecipe(ctx.get(), List.of(new ItemStack(AFSingletons.CORE_256M, 10))),
                null
            );
        })
        .lang("ECO - LE9 Storage Matrix (FE)")
        .model(ItemModelUtil.cellModel("fe", "256m"))
        .register();

    public static void register() {
    }
}
