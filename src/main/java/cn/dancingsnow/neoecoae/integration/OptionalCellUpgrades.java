package cn.dancingsnow.neoecoae.integration;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.List;

public final class OptionalCellUpgrades {
    private OptionalCellUpgrades() {
    }

    /**
     * Makes {@code cells} accept the standard fuzzy, inverter and void cards in the storage-cell group.
     * The upgrades are added on {@link FMLCommonSetupEvent}, inside {@code enqueueWork}.
     */
    public static void register(IEventBus modBus, List<? extends ItemEntry<? extends Item>> cells) {
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() ->
            addStandard(cells, GuiText.StorageCells.getTranslationKey())));
    }

    /**
     * Synchronously adds the fuzzy, inverter and void cards (one each) to every cell under {@code group}.
     * Call from common setup work.
     */
    public static void addStandard(Iterable<? extends ItemEntry<? extends Item>> cells, String group) {
        for (ItemEntry<? extends Item> cell : cells) {
            Upgrades.add(AEItems.FUZZY_CARD.get(), cell, 1, group);
            Upgrades.add(AEItems.INVERTER_CARD, cell, 1, group);
            Upgrades.add(AEItems.VOID_CARD, cell, 1, group);
        }
    }
}
