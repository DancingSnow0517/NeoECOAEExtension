package cn.dancingsnow.neoecoae.integration;

import appeng.api.stacks.AEKeyType;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOAETypeCounts;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.client.NEItemColors;
import com.tterrag.registrate.util.entry.ItemEntry;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

public abstract class AbstractCellIntegration {
    private final Supplier<? extends AEKeyType> keyType;
    private final int typeCount;
    private final Runnable registerCellTypes;
    private final Runnable registerItems;
    private final IECOCellHandler handler;
    private final List<ItemEntry<? extends Item>> cells;
    private final List<ResourceLocation> models;

    protected AbstractCellIntegration(
            Supplier<? extends AEKeyType> keyType,
            int typeCount,
            Runnable registerCellTypes,
            Runnable registerItems,
            IECOCellHandler handler,
            List<ItemEntry<? extends Item>> cells,
            List<ResourceLocation> models) {
        if (cells.size() != models.size()) {
            throw new IllegalArgumentException("Cell and model lists must have the same size");
        }
        this.keyType = keyType;
        this.typeCount = typeCount;
        this.registerCellTypes = registerCellTypes;
        this.registerItems = registerItems;
        this.handler = handler;
        this.cells = List.copyOf(cells);
        this.models = List.copyOf(models);
    }

    public void apply() {
        ECOAETypeCounts.register(keyType.get(), typeCount);
        registerCellTypes.run();
        registerItems.run();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoECOAE.MOD_BUS.addListener(this::initModels);
            NeoECOAE.MOD_BUS.addListener(this::initItemColors);
        }
        NeoECOAE.MOD_BUS.addListener(this::initUpgrades);
        NeoECOAE.MOD_BUS.addListener(this::initHandler);
        afterApply();
    }

    protected void afterApply() {}

    protected void afterRegisterHandler() {}

    private void initModels(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (int i = 0; i < cells.size(); i++) {
                ECOCellModels.register(cells.get(i).get(), models.get(i));
            }
        });
    }

    private void initUpgrades(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            String storageCellGroup = GuiText.StorageCells.getTranslationKey();
            for (ItemEntry<? extends Item> cell : cells) {
                Upgrades.add(AEItems.FUZZY_CARD, cell.get(), 1, storageCellGroup);
                Upgrades.add(AEItems.INVERTER_CARD, cell.get(), 1, storageCellGroup);
                Upgrades.add(AEItems.VOID_CARD, cell.get(), 1, storageCellGroup);
            }
        });
    }

    private void initItemColors(RegisterColorHandlersEvent.Item event) {
        NEItemColors.registerEcoCellStatusLights(
                event, cells.stream().map(ItemEntry::get).toArray(Item[]::new));
    }

    private void initHandler(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ECOStorageCells.register(handler);
            afterRegisterHandler();
        });
    }
}
