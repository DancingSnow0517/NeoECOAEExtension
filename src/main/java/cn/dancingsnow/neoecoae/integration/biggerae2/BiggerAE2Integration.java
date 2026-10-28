package cn.dancingsnow.neoecoae.integration.biggerae2;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import appeng.api.upgrades.Upgrades;
import appeng.core.localization.GuiText;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

/** Optional ECO versions of Bigger AE2's six Quantum and six Digital Singularity cells. */
@Integration("bigger_ae2")
public final class BiggerAE2Integration {
    public void apply() {
        BiggerAE2SingularityContents.register(NeoECOAE.MOD_BUS);
        List<ItemEntry<? extends ECOStorageCellItem>> cells = new ArrayList<>();
        List<ItemEntry<? extends ECOStorageCellItem>> bulkCells = new ArrayList<>();
        for (BiggerAE2Channel channel : BiggerAE2Channel.values()) {
            if (channel.requiredMod != null && !ModList.get().isLoaded(channel.requiredMod)) continue;
            String typeId = "bigger_ae2_" + channel.id;
            REGISTRATE.addLang("cell_type", NeoECOAE.id(typeId), "Bigger AE2 " + channel.displayName);
            var type = REGISTRATE.cellType(typeId)
                .desc(Component.translatable("cell_type.neoecoae." + typeId).withColor(channel.color))
                .typeCount(channel.typeLimit)
                .register();
            for (boolean singularity : new boolean[]{false, true}) {
                ECOTier tier = singularity ? ECOTier.L9 : ECOTier.L6;
                String family = singularity ? "digital_singularity" : "quantum";
                String housing = family + "_" + channel.id + "_cell_housing";
                String[] overlays = singularity ? new String[]{"digital_singularity_cell_layer"} : new String[0];
                boolean bulk = singularity && channel == BiggerAE2Channel.ITEM;
                var cell = REGISTRATE.<ECOStorageCellItem>item(channel.cellId(singularity), properties -> bulk
                        ? new BiggerAE2BulkStorageCellItem(properties.stacksTo(1), type)
                        : new BiggerAE2StorageCellItem(properties.stacksTo(1), channel, singularity, type))
                    .lang("ECO - " + tier.name() + " " + (singularity ? "Digital Singularity" : "Quantum")
                        + " Storage Matrix (" + channel.displayName + ")")
                    .model(ItemModelUtil.compatCellModel("bigger_ae2", housing,
                        singularity ? "l9" : "l6", overlays))
                    .register();
                ECOCellModels.register(cell, NeoECOAE.id(
                    "block/cell/storage_cell_" + family + "_" + tier.name().toLowerCase(java.util.Locale.ROOT)
                        + "_" + channel.id));
                if (bulk) bulkCells.add(cell);
                else cells.add(cell);
            }
        }
        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, cells);
        NeoECOAE.MOD_BUS.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() ->
            BuiltInRegistries.ITEM.getOptional(NeoECOAE.id("eco_mega_upgrade_card")).ifPresent(card -> {
                for (var cell : bulkCells) Upgrades.add(card, cell, 1, GuiText.StorageCells.getTranslationKey());
            })));
    }
}
