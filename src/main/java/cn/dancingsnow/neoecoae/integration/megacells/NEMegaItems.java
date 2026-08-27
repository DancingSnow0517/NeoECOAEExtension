package cn.dancingsnow.neoecoae.integration.megacells;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaFluidStorageCellItem;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaItemStorageCellItem;
import cn.dancingsnow.neoecoae.integration.megacells.item.MegaCellHousingItem;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.function.Supplier;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;
import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.BASE_16M_CAPACITY;
import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.BASE_256M_CAPACITY;
import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.BASE_64M_CAPACITY;
import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.COMPRESSED_4G_CAPACITY;

public final class NEMegaItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
        REGISTRATE.addLang("tooltip", NeoECOAE.id("megacells.storage_type"), "MEGA storage type: %s");
        REGISTRATE.addLang("tooltip", NeoECOAE.id("megacells.compressible"),
            "Compression: combine exactly %s empty matching 256 MiB matrices");
        REGISTRATE.addLang("tooltip", NeoECOAE.id("megacells.compressed"),
            "Compressed tier: explicit 4 GiB capacity");
        REGISTRATE.addLang("tooltip", NeoECOAE.id("megacells.empty_only"),
            "Compression and disassembly require an empty, unconfigured matrix");
        REGISTRATE.addLang("tooltip", NeoECOAE.id("megacells.housing"),
            "MEGA Cells housing for %s storage matrices");
    }

    public static final ItemEntry<Item> MEGA_ITEM_CELL_HOUSING = housing("mega_item");
    public static final ItemEntry<Item> MEGA_FLUID_CELL_HOUSING = housing("mega_fluid");

    public static final ItemEntry<ECOMegaItemStorageCellItem> ECO_MEGA_ITEM_CELL_16M = itemCell("16m", ECOTier.L4, BASE_16M_CAPACITY, Rarity.UNCOMMON);
    public static final ItemEntry<ECOMegaItemStorageCellItem> ECO_MEGA_ITEM_CELL_64M = itemCell("64m", ECOTier.L6, BASE_64M_CAPACITY, Rarity.RARE);
    public static final ItemEntry<ECOMegaItemStorageCellItem> ECO_MEGA_ITEM_CELL_256M = itemCell("256m", ECOTier.L9, BASE_256M_CAPACITY, Rarity.EPIC);
    public static final ItemEntry<ECOMegaItemStorageCellItem> ECO_MEGA_ITEM_CELL_4G = itemCell("4g", ECOTier.L9, COMPRESSED_4G_CAPACITY, Rarity.EPIC);

    public static final ItemEntry<ECOMegaFluidStorageCellItem> ECO_MEGA_FLUID_CELL_16M = fluidCell("16m", ECOTier.L4, BASE_16M_CAPACITY, Rarity.UNCOMMON);
    public static final ItemEntry<ECOMegaFluidStorageCellItem> ECO_MEGA_FLUID_CELL_64M = fluidCell("64m", ECOTier.L6, BASE_64M_CAPACITY, Rarity.RARE);
    public static final ItemEntry<ECOMegaFluidStorageCellItem> ECO_MEGA_FLUID_CELL_256M = fluidCell("256m", ECOTier.L9, BASE_256M_CAPACITY, Rarity.EPIC);
    public static final ItemEntry<ECOMegaFluidStorageCellItem> ECO_MEGA_FLUID_CELL_4G = fluidCell("4g", ECOTier.L9, COMPRESSED_4G_CAPACITY, Rarity.EPIC);

    private static ItemEntry<Item> housing(String family) {
        return REGISTRATE.<Item>item(family + "_cell_housing",
                p -> new MegaCellHousingItem(p, "cell_type.neoecoae." + family))
            .lang("ECO MEGA Storage Matrix Housing (" + displayFamily(family) + ")")
            .model(ItemModelUtil.compatHousingModel(family + "_cell_housing"))
            .register();
    }

    private static ItemEntry<ECOMegaItemStorageCellItem> itemCell(String size, ECOTier tier, long capacity, Rarity rarity) {
        return REGISTRATE.item("eco_mega_item_cell_" + size,
                p -> new ECOMegaItemStorageCellItem(p.stacksTo(1).rarity(rarity), tier, NEMegaCellTypes.MEGA_ITEM, capacity))
            .lang(cellName("Mega Item", tier, capacity, size))
            .model(ItemModelUtil.compatCellModel("mega_item_cell_housing", modelSize(size)))
            .register();
    }

    private static ItemEntry<ECOMegaFluidStorageCellItem> fluidCell(String size, ECOTier tier, long capacity, Rarity rarity) {
        return REGISTRATE.item("eco_mega_fluid_cell_" + size,
                p -> new ECOMegaFluidStorageCellItem(p.stacksTo(1).rarity(rarity), tier, NEMegaCellTypes.MEGA_FLUID, capacity))
            .lang(cellName("Mega Fluid", tier, capacity, size))
            .model(ItemModelUtil.compatCellModel("mega_fluid_cell_housing", modelSize(size)))
            .register();
    }

    static <T extends ECOStorageCellItem> ItemEntry<T> optionalCell(
        String family, String displayFamily, String size, ECOTier tier, long capacity, Rarity rarity,
        CellFactory<T> factory, Supplier<ECOCellType> type
    ) {
        return REGISTRATE.item("eco_" + family + "_cell_" + size,
                p -> factory.create(p.stacksTo(1).rarity(rarity), tier, type, capacity))
            .lang(cellName(displayFamily, tier, capacity, size))
            .model(ItemModelUtil.compatCellModel(family + "_cell_housing", modelSize(size)))
            .register();
    }

    static ItemEntry<Item> optionalHousing(String family, String displayFamily) {
        return REGISTRATE.<Item>item(family + "_cell_housing",
                p -> new MegaCellHousingItem(p, "cell_type.neoecoae." + family))
            .lang("ECO MEGA Storage Matrix Housing (" + displayFamily + ")")
            .model(ItemModelUtil.compatHousingModel(family + "_cell_housing"))
            .register();
    }

    private static String modelSize(String size) {
        return size.equals("4g") ? "256m" : size;
    }

    private static String cellName(String family, ECOTier tier, long capacity, String size) {
        String level = tier == ECOTier.L4 ? "4" : tier == ECOTier.L6 ? "6" : "9";
        String compressed = size.equals("4g") ? " Compressed" : "";
        return "ECO - LE" + level + compressed + " Storage Matrix (" + family + ", "
            + (capacity >> 20) + " MiB)";
    }

    private static String displayFamily(String family) {
        return family.equals("mega_item") ? "Mega Item" : "Mega Fluid";
    }

    @FunctionalInterface
    interface CellFactory<T extends ECOStorageCellItem> {
        T create(Item.Properties properties, ECOTier tier, Supplier<ECOCellType> type, long capacity);
    }

    private NEMegaItems() {
    }

    public static void register() {
    }
}
