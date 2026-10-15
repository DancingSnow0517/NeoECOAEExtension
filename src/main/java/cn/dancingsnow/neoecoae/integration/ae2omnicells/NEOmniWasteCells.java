package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.item.ECOSpentNuclearWasteCellItem;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.wintercogs.ae2omnicells.common.init.OCItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

/** Loaded only when both OmniCells and Applied Mekanistics are installed. */
public final class NEOmniWasteCells {
    public static final NECellTypeEntry SPENT_NUCLEAR_WASTE = REGISTRATE.cellType("spent_nuclear_waste")
        .desc(Component.translatable("cell_type.neoecoae.spent_nuclear_waste").withColor(0x6F8259))
        .typeCount(1)
        .register();

    public static final ItemEntry<MaterialItem> ECO_SPENT_NUCLEAR_WASTE_CELL_HOUSING = REGISTRATE
        .item("eco_spent_nuclear_waste_cell_housing", MaterialItem::new)
        .lang("ECO Spent Nuclear Waste Storage Matrix Housing")
        .model((ctx, prov) -> {})
        .register();

    public static final ItemEntry<ECOSpentNuclearWasteCellItem> ECO_SPENT_NUCLEAR_WASTE_CELL_16M =
        cell("16m", ECOTier.L4, 1L << 28, 6561, 2, 4, 1000, Rarity.UNCOMMON);
    public static final ItemEntry<ECOSpentNuclearWasteCellItem> ECO_SPENT_NUCLEAR_WASTE_CELL_64M =
        cell("64m", ECOTier.L6, 1L << 30, 19683, 4, 16, 12000, Rarity.RARE);
    public static final ItemEntry<ECOSpentNuclearWasteCellItem> ECO_SPENT_NUCLEAR_WASTE_CELL_256M =
        cell("256m", ECOTier.L9, 1L << 32, 59049, 8, 64, 144000, Rarity.EPIC);

    private static ItemEntry<ECOSpentNuclearWasteCellItem> cell(
        String size, ECOTier tier, long bytes, double drain, int processors, int singularities, int energy, Rarity rarity
    ) {
        return REGISTRATE.item("eco_spent_nuclear_waste_cell_" + size,
                properties -> new ECOSpentNuclearWasteCellItem(
                    properties.rarity(rarity), tier, SPENT_NUCLEAR_WASTE, drain, bytes))
            .lang("ECO - LE" + switch (tier) { case L4 -> "4"; case L6 -> "6"; case L9 -> "9"; }
                + " Storage Matrix (Spent Nuclear Waste)")
            .model((ctx, prov) -> {})
            .recipe((ctx, prov) -> IntegratedWorkingStationRecipe.builder()
                .require(OCItems.SPENT_NUCLEAR_WASTE_COMPONENT, 10)
                .require(OCItems.MULTIDIMENSIONAL_EXPANSION_PROCESSOR, processors)
                .require(OCItems.SPENT_NUCLEAR_WASTE_SINGULARITY, singularities)
                .require(ECO_SPENT_NUCLEAR_WASTE_CELL_HOUSING)
                .energy(energy)
                .itemOutput(ctx.get())
                .save(prov.withConditions(new ModLoadedCondition("ae2omnicells"), new ModLoadedCondition("appmek")),
                    ctx.getId().withPrefix("integrated_working_station/")))
            .register();
    }

    public static List<ItemEntry<ECOSpentNuclearWasteCellItem>> cells() {
        return List.of(ECO_SPENT_NUCLEAR_WASTE_CELL_16M, ECO_SPENT_NUCLEAR_WASTE_CELL_64M,
            ECO_SPENT_NUCLEAR_WASTE_CELL_256M);
    }

    public static void registerModels(boolean resolved) {
        var cells = cells();
        String[] levels = {"l4", "l6", "l9"};
        for (int i = 0; i < cells.size(); i++) {
            var cell = cells.get(i);
            String level = levels[i];
            var model = NeoECOAE.id("block/cell/storage_cell_" + level + "_neuclear_waste");
            if (resolved) ECOCellModels.register(cell.get(), model);
            else ECOCellModels.register(cell, model);
        }
    }

    public static void registerUpgrades() {
        String group = GuiText.StorageCells.getTranslationKey();
        for (var cell : cells()) {
            Upgrades.add(AEItems.FUZZY_CARD.get(), cell, 1, group);
            Upgrades.add(AEItems.INVERTER_CARD, cell, 1, group);
            Upgrades.add(AEItems.VOID_CARD, cell, 1, group);
        }
    }

    private NEOmniWasteCells() {}
}
