package cn.dancingsnow.neoecoae.integration.ae2lt;

import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.moakiee.ae2lt.me.key.LightningKeyType;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Rarity;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public final class NELightningItems {
    public static final ItemEntry<MaterialItem> ECO_LIGHTNING_CELL_HOUSING = REGISTRATE
        .item("eco_lightning_cell_housing", MaterialItem::new)
        .lang("ECO Lightning Storage Matrix Housing")
        .model(ItemModelUtil.compatHousingModel("lightning_cell_housing"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_LIGHTNING_CELL_16M = registerCell(
        "eco_lightning_cell_16m", ECOTier.L4, "16m", Rarity.UNCOMMON,
        1_048_576L, 32_768
    );
    public static final ItemEntry<ECOStorageCellItem> ECO_LIGHTNING_CELL_64M = registerCell(
        "eco_lightning_cell_64m", ECOTier.L6, "64m", Rarity.RARE,
        4_194_304L, 131_072
    );
    public static final ItemEntry<ECOStorageCellItem> ECO_LIGHTNING_CELL_256M = registerCell(
        "eco_lightning_cell_256m", ECOTier.L9, "256m", Rarity.EPIC,
        16_777_216L, 524_288
    );

    private static ItemEntry<ECOStorageCellItem> registerCell(
        String name,
        ECOTier tier,
        String size,
        Rarity rarity,
        long usableCapacity,
        double idleDrain
    ) {
        String level = tier == ECOTier.L4 ? "4" : tier == ECOTier.L6 ? "6" : "9";
        return REGISTRATE.item(name, properties -> new ECOStorageCellItem(
                properties.stacksTo(1).rarity(rarity), tier, LightningKeyType.INSTANCE,
                NELightningCellTypes.LIGHTNING, usableCapacity + 16, 8, idleDrain
            ))
            .lang("ECO - LE" + level + " Storage Matrix (Lightning)")
            .model(ItemModelUtil.compatCellModel("lightning_cell_housing", size))
            .register();
    }

    private NELightningItems() {
    }

    public static void register() {
    }
}
