package cn.dancingsnow.neoecoae.all;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Rarity;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.STORAGE);
    }

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_16M = REGISTRATE
        .item("eco_item_storage_cell_16m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.UNCOMMON),
            ECOTier.L4,
            AEKeyType.items()
        ))
        .lang("ECO - LE4 Storage Matrix (Item)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_64M = REGISTRATE
        .item("eco_item_storage_cell_64m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.RARE),
            ECOTier.L6,
            AEKeyType.items()
        ))
        .lang("ECO - LE6 Storage Matrix (Item)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_256M = REGISTRATE
        .item("eco_item_storage_cell_256m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.EPIC),
            ECOTier.L9,
            AEKeyType.items()
        ))
        .lang("ECO - LE9 Storage Matrix (Item)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_16M = REGISTRATE
        .item("eco_fluid_storage_cell_16m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.UNCOMMON),
            ECOTier.L4,
            AEKeyType.fluids()
        ))
        .lang("ECO - LE4 Storage Matrix (Fluid)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_64M = REGISTRATE
        .item("eco_fluid_storage_cell_64m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.RARE),
            ECOTier.L6,
            AEKeyType.fluids()
        ))
        .lang("ECO - LE6 Storage Matrix (Fluid)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_256M = REGISTRATE
        .item("eco_fluid_storage_cell_256m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.EPIC),
            ECOTier.L9,
            AEKeyType.fluids()
        ))
        .lang("ECO - LE9 Storage Matrix (Fluid)")
        .register();

    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.COMPUTATION);
    }

    public static final ItemEntry<ECOComputationCellItem> ECO_COMPUTATION_CELL_L4 = createComputationCell(
        "l4",
        ECOTier.L4,
        Rarity.UNCOMMON
    );

    public static final ItemEntry<ECOComputationCellItem> ECO_COMPUTATION_CELL_L6 = createComputationCell(
        "l6",
        ECOTier.L6,
        Rarity.RARE
    );
    public static final ItemEntry<ECOComputationCellItem> ECO_COMPUTATION_CELL_L9 = createComputationCell(
        "l9",
        ECOTier.L9,
        Rarity.EPIC
    );

    private static ItemEntry<ECOComputationCellItem> createComputationCell(
        String tierString,
        IECOTier tier,
        Rarity rarity
    ) {
        return REGISTRATE
            .item("eco_computation_cell_" + tierString, p -> new ECOComputationCellItem(
                p.stacksTo(1).rarity(rarity),
                tier
            ))
            .lang("ECO - %s Synthetic Memory Element".formatted(tierString.replace("l", "CE")))
            .model((ctx, prov) -> {
                prov.withExistingParent("eco_computation_cell_" + tierString, prov.modLoc("item/computation_cell"));
            })
            .register();
    }

    public static void register() {

    }
}
