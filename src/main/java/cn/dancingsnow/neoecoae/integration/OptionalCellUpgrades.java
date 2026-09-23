package cn.dancingsnow.neoecoae.integration;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.List;

public final class OptionalCellUpgrades {
    private OptionalCellUpgrades() {
    }

    public static void register(IEventBus modBus, List<? extends ItemEntry<? extends ECOStorageCellItem>> cells) {
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            String group = GuiText.StorageCells.getTranslationKey();
            for (ItemEntry<? extends ECOStorageCellItem> cell : cells) {
                Upgrades.add(AEItems.FUZZY_CARD.get(), cell, 1, group);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, group);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, group);
            }
        }));
    }
}
