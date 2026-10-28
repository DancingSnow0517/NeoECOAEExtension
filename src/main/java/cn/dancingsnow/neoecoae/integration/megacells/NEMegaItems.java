package cn.dancingsnow.neoecoae.integration.megacells;

import appeng.api.upgrades.Upgrades;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaFluidStorageCellItem;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaItemStorageCellItem;
import cn.dancingsnow.neoecoae.integration.megacells.item.MegaCellHousingItem;
import cn.dancingsnow.neoecoae.all.NECellTypes;
import cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;
import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.MEGA_4G_CAPACITY;

public final class NEMegaItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
        REGISTRATE.addLang("tooltip", NeoECOAE.id("megacells.housing"),
            "MEGA Cells housing for %s storage matrices");
    }

    public static final ItemEntry<Item> MEGA_ITEM_CELL_HOUSING = housing("mega_item", "Mega Item");
    public static final ItemEntry<Item> MEGA_FLUID_CELL_HOUSING = housing("mega_fluid", "Mega Fluid");
    public static final ItemEntry<Item> ECO_MEGA_UPGRADE_CARD =
        REGISTRATE.item("eco_mega_upgrade_card", Upgrades::createUpgradeCardItem)
            .lang("ECO Bulk Expansion Card")
            .model((ctx, prov) -> {})
            .register();

    public static final ItemEntry<ECOMegaItemStorageCellItem> ECO_MEGA_ITEM_CELL_4G =
        itemCell("4g", MEGA_4G_CAPACITY, Rarity.EPIC);
    public static final ItemEntry<ECOMegaFluidStorageCellItem> ECO_MEGA_FLUID_CELL_4G =
        fluidCell("4g", MEGA_4G_CAPACITY, Rarity.EPIC);
    public static final ItemEntry<ECOBulkStorageCellItem> ECO_MEGA_LONG_BULK_CELL =
        REGISTRATE.<ECOBulkStorageCellItem>item("eco_mega_long_bulk_cell", p -> new ECOBulkStorageCellItem(
                p.stacksTo(1).rarity(Rarity.EPIC), ECOTier.L9, NECellTypes.BULK_ITEM) {
                @Override
                public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
                    lines.add(Component.translatable("tooltip.neoecoae.bulk.deprecated").withStyle(ChatFormatting.RED));
                    lines.add(Component.translatable("tooltip.neoecoae.bulk.moved").withStyle(ChatFormatting.GRAY));
                }

                @Override
                public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
                    return Optional.empty();
                }
            })
            .lang("ECO Bulk Storage Matrix")
            .model((ctx, prov) -> {})
            .register();

    static ItemEntry<Item> housing(String family, String displayFamily) {
        String housingFamily = displayFamily.startsWith("Mega ") ? displayFamily.substring("Mega ".length()) : displayFamily;
        return REGISTRATE.<Item>item(family + "_cell_housing",
                MegaCellHousingItem::new)
            .lang("ECO MEGA Storage Matrix Housing (" + housingFamily + ")")
            .model((ctx, prov) -> {})
            .register();
    }

    private static ItemEntry<ECOMegaItemStorageCellItem> itemCell(String size, long capacity, Rarity rarity) {
        return REGISTRATE.item("eco_mega_item_cell_" + size,
                p -> new ECOMegaItemStorageCellItem(p.stacksTo(1).rarity(rarity), ECOTier.L9,
                    NEMegaCellTypes.MEGA_ITEM, capacity))
            .lang(cellName("Mega Item", ECOTier.L9))
            .model((ctx, prov) -> {})
            .register();
    }

    private static ItemEntry<ECOMegaFluidStorageCellItem> fluidCell(String size, long capacity, Rarity rarity) {
        return REGISTRATE.item("eco_mega_fluid_cell_" + size,
                p -> new ECOMegaFluidStorageCellItem(p.stacksTo(1).rarity(rarity), ECOTier.L9,
                    NEMegaCellTypes.MEGA_FLUID, capacity))
            .lang(cellName("Mega Fluid", ECOTier.L9))
            .model((ctx, prov) -> {})
            .register();
    }

    static <T extends ECOStorageCellItem> ItemEntry<T> optionalCell(
        String family, String displayFamily, String size, ECOTier tier, long capacity, Rarity rarity,
        CellFactory<T> factory, Supplier<ECOCellType> type
    ) {
        return REGISTRATE.item("eco_" + family + "_cell_" + size,
                p -> factory.create(p.stacksTo(1).rarity(rarity), tier, type, capacity))
            .lang(cellName(displayFamily, tier))
            .model((ctx, prov) -> {})
            .register();
    }

    private static String cellName(String family, ECOTier tier) {
        String level = switch (tier) {
            case L4 -> "4";
            case L6 -> "6";
            case L9 -> "9";
        };
        return "ECO - LE" + level + " Storage Matrix (" + family + ")";
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
