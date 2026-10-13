package cn.dancingsnow.neoecoae.integration.ae2lt;

import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.IntegrationCells.Size;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.moakiee.ae2lt.me.key.LightningKeyType;
import com.tterrag.registrate.util.entry.ItemEntry;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public final class NELightningItems {
    public static final ItemEntry<MaterialItem> ECO_LIGHTNING_CELL_HOUSING = REGISTRATE
        .item("eco_lightning_cell_housing", MaterialItem::new)
        .lang("ECO Lightning Storage Matrix Housing")
        .model((ctx, prov) -> {})
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_LIGHTNING_CELL_16M = registerCell(
        "eco_lightning_cell_16m", Size.L4, 1_048_576L, 32_768
    );
    public static final ItemEntry<ECOStorageCellItem> ECO_LIGHTNING_CELL_64M = registerCell(
        "eco_lightning_cell_64m", Size.L6, 4_194_304L, 131_072
    );
    public static final ItemEntry<ECOStorageCellItem> ECO_LIGHTNING_CELL_256M = registerCell(
        "eco_lightning_cell_256m", Size.L9, 16_777_216L, 524_288
    );

    private static ItemEntry<ECOStorageCellItem> registerCell(
        String name,
        Size size,
        long usableCapacity,
        double idleDrain
    ) {
        String level = size.tier() == ECOTier.L4 ? "4" : size.tier() == ECOTier.L6 ? "6" : "9";
        return IntegrationCells.cell(name, size, (properties, tier) -> new ECOStorageCellItem(
                properties, tier, LightningKeyType.INSTANCE,
                NELightningCellTypes.LIGHTNING, usableCapacity + 16, 8, idleDrain
            ))
            .lang("ECO - LE" + level + " Storage Matrix (Lightning)")
            .model((ctx, prov) -> {})
            .register();
    }

    private NELightningItems() {
    }

    public static void register() {
    }
}
