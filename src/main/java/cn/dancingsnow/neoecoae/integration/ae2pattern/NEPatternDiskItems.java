package cn.dancingsnow.neoecoae.integration.ae2pattern;

import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import io.github.lounode.ae2pattern.common.pattern.PatternDiskTier;
import net.minecraft.world.item.Rarity;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public final class NEPatternDiskItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }

    /**
     * The group a storage host lists pattern matrices under. Count-only: a matrix holds so many patterns out of so
     * many, and has no bytes for the host to show.
     */
    public static final NECellTypeEntry CELL_TYPE = REGISTRATE.cellType("pattern_disk")
        .desc(Component.translatable("cell_type.neoecoae.pattern_disk").withColor(0x9b7bd6))
        .typeCount(1)
        .countOnly(true)
        .register();

    public static final ItemEntry<ECOPatternDiskMatrixItem> L4 =
        disk("l4", "16m", PatternDiskTier.SIZE_16K, ECOTier.L4, Rarity.UNCOMMON);
    public static final ItemEntry<ECOPatternDiskMatrixItem> L6 =
        disk("l6", "64m", PatternDiskTier.SIZE_64K, ECOTier.L6, Rarity.RARE);
    public static final ItemEntry<ECOPatternDiskMatrixItem> L9 =
        disk("l9", "256m", PatternDiskTier.SIZE_256K, ECOTier.L9, Rarity.EPIC);

    private NEPatternDiskItems() {
    }

    static void register() {
    }

    private static ItemEntry<ECOPatternDiskMatrixItem> disk(String level, String modelSize, PatternDiskTier tier,
            ECOTier ecoTier, Rarity rarity) {
        return REGISTRATE.item("eco_pattern_disk_cell_" + level,
                properties -> new ECOPatternDiskMatrixItem(properties, tier, ecoTier))
            .properties(properties -> properties.rarity(rarity))
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .model(ItemModelUtil.cellModel("pattern_disk", modelSize))
            .register();
    }
}
