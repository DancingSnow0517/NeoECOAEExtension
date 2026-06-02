package cn.dancingsnow.neoecoae.compat.jade;

import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingWorker;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.compat.jade.provider.ECOComputationSystemProvider;
import cn.dancingsnow.neoecoae.compat.jade.provider.ECOCraftingSystemProvider;
import cn.dancingsnow.neoecoae.compat.jade.provider.ECOCraftingWorkerProvider;
import cn.dancingsnow.neoecoae.compat.jade.provider.ECODriveProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class NEJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ECODriveProvider.INSTANCE, ECODriveBlockEntity.class);
        registration.registerBlockDataProvider(ECOCraftingWorkerProvider.INSTANCE, ECOCraftingWorkerBlockEntity.class);
        registration.registerBlockDataProvider(ECOCraftingSystemProvider.INSTANCE, ECOCraftingSystemBlockEntity.class);
        registration.registerBlockDataProvider(ECOComputationSystemProvider.INSTANCE, ECOComputationSystemBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ECODriveProvider.INSTANCE, ECODriveBlock.class);
        registration.registerBlockComponent(ECOCraftingWorkerProvider.INSTANCE, ECOCraftingWorker.class);
        registration.registerBlockComponent(ECOCraftingSystemProvider.INSTANCE, ECOCraftingSystem.class);
        registration.registerBlockComponent(ECOComputationSystemProvider.INSTANCE, ECOComputationSystem.class);
    }
}
