package cn.dancingsnow.neoecoae.integration.megacells;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaFluidStorageCellItem;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaItemStorageCellItem;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaLongBulkStorageCellItem;
import cn.dancingsnow.neoecoae.integration.megacells.item.MegaCellHousingItem;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.function.Supplier;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;
import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.MEGA_4G_CAPACITY;

public final class NEMegaItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
        REGISTRATE.addLang("tooltip", NeoECOAE.id("megacells.storage_type"), "MEGA storage type: %s");
        REGISTRATE.addLang("tooltip", NeoECOAE.id("megacells.4g"), "4 GiB storage capacity");
        REGISTRATE.addLang("tooltip", NeoECOAE.id("megacells.configure_item"),
            "Configure up to 25 compression chains; the first variant selects the storage form");
        REGISTRATE.addLang("tooltip", NeoECOAE.id("megacells.compression_builtin"),
            "Compression variants are enabled by default; no upgrade card required");
        REGISTRATE.addLang("tooltip", NeoECOAE.id("megacells.empty_only"),
            "Only an empty storage matrix can be disassembled");
        REGISTRATE.addLang("tooltip", NeoECOAE.id("megacells.housing"),
            "MEGA Cells housing for %s storage matrices");
    }

    public static final ItemEntry<Item> MEGA_ITEM_CELL_HOUSING = housing("mega_item", "Mega Item");
    public static final ItemEntry<Item> MEGA_FLUID_CELL_HOUSING = housing("mega_fluid", "Mega Fluid");

    public static final ItemEntry<ECOMegaItemStorageCellItem> ECO_MEGA_ITEM_CELL_4G =
        itemCell("4g", MEGA_4G_CAPACITY, Rarity.EPIC);
    public static final ItemEntry<ECOMegaFluidStorageCellItem> ECO_MEGA_FLUID_CELL_4G =
        fluidCell("4g", MEGA_4G_CAPACITY, Rarity.EPIC);
    public static final ItemEntry<ECOMegaLongBulkStorageCellItem> ECO_MEGA_LONG_BULK_CELL =
        REGISTRATE.item("eco_mega_long_bulk_cell",
                p -> new ECOMegaLongBulkStorageCellItem(p.stacksTo(1).rarity(Rarity.EPIC), ECOTier.L9,
                    NEMegaCellTypes.MEGA_ITEM))
            .lang("ECO MEGA Bulk Storage Matrix")
            .model(ItemModelUtil.compatCellModel("mega_item_cell_housing", "256m"))
            .register();

    static ItemEntry<Item> housing(String family, String displayFamily) {
        return REGISTRATE.<Item>item(family + "_cell_housing",
                p -> new MegaCellHousingItem(p, "cell_type.neoecoae." + family))
            .lang("ECO MEGA Storage Matrix Housing (" + displayFamily + ")")
            .model(ItemModelUtil.compatHousingModel(family + "_cell_housing"))
            .register();
    }

    private static ItemEntry<ECOMegaItemStorageCellItem> itemCell(String size, long capacity, Rarity rarity) {
        return REGISTRATE.item("eco_mega_item_cell_" + size,
                p -> new ECOMegaItemStorageCellItem(p.stacksTo(1).rarity(rarity), ECOTier.L9,
                    NEMegaCellTypes.MEGA_ITEM, capacity))
            .lang(cellName("Mega Item", capacity))
            .model(ItemModelUtil.compatCellModel("mega_item_cell_housing", "256m"))
            .register();
    }

    private static ItemEntry<ECOMegaFluidStorageCellItem> fluidCell(String size, long capacity, Rarity rarity) {
        return REGISTRATE.item("eco_mega_fluid_cell_" + size,
                p -> new ECOMegaFluidStorageCellItem(p.stacksTo(1).rarity(rarity), ECOTier.L9,
                    NEMegaCellTypes.MEGA_FLUID, capacity))
            .lang(cellName("Mega Fluid", capacity))
            .model(ItemModelUtil.compatCellModel("mega_fluid_cell_housing", "256m"))
            .register();
    }

    static <T extends ECOStorageCellItem> ItemEntry<T> optionalCell(
        String family, String displayFamily, String size, ECOTier tier, long capacity, Rarity rarity,
        CellFactory<T> factory, Supplier<ECOCellType> type
    ) {
        return REGISTRATE.item("eco_" + family + "_cell_" + size,
                p -> factory.create(p.stacksTo(1).rarity(rarity), tier, type, capacity))
            .lang(cellName(displayFamily, capacity))
            .model(ItemModelUtil.compatCellModel(family + "_cell_housing", "256m"))
            .register();
    }

    private static String cellName(String family, long capacity) {
        return "ECO - LE9 Storage Matrix (" + family + ", " + (capacity >> 30) + " GiB)";
    }

    @FunctionalInterface
    interface CellFactory<T extends ECOStorageCellItem> {
        T create(Item.Properties properties, ECOTier tier, Supplier<ECOCellType> type, long capacity);
    }

    private NEMegaItems() {
    }

    public static void register() {
        // Intentional class-initialization barrier: registration order must remain explicit.
    }
}
