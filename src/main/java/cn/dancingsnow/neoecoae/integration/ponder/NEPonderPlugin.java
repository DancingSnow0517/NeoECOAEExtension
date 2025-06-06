package cn.dancingsnow.neoecoae.integration.ponder;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.integration.ponder.scenes.ComputationSystemScene;
import cn.dancingsnow.neoecoae.integration.ponder.scenes.StorageSystemScene;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class NEPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return NeoECOAE.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> h) {
        PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> helper = h.withKeyFunction(RegistryEntry::getId);

        helper.forComponents(NEBlocks.STORAGE_SYSTEM_L4, NEBlocks.STORAGE_SYSTEM_L6, NEBlocks.STORAGE_SYSTEM_L9)
            .addStoryBoard("storage_system/creating", StorageSystemScene::creating, NEPonderTags.STORAGE_SYSTEM_COMPONENTS);

        helper.forComponents(NEBlocks.ENERGY_CELL_L4, NEBlocks.ENERGY_CELL_L6, NEBlocks.ENERGY_CELL_L9)
            .addStoryBoard("storage_system/energy", StorageSystemScene::energy, NEPonderTags.STORAGE_SYSTEM_COMPONENTS);

        helper.forComponents(NEBlocks.STORAGE_INTERFACE)
            .addStoryBoard("storage_system/interface", StorageSystemScene::interface_, NEPonderTags.STORAGE_SYSTEM_COMPONENTS);

        helper.forComponents(NEBlocks.ECO_DRIVE)
            .addStoryBoard("storage_system/eco_drive", StorageSystemScene::drive, NEPonderTags.STORAGE_SYSTEM_COMPONENTS);

        helper.forComponents(NEBlocks.COMPUTATION_SYSTEM_L4, NEBlocks.COMPUTATION_SYSTEM_L6, NEBlocks.COMPUTATION_SYSTEM_L9)
            .addStoryBoard("computation_system/creating", ComputationSystemScene::creating);


    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        NEPonderTags.register(helper);
    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
    }
}
