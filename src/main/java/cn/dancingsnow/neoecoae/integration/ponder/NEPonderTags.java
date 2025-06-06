package cn.dancingsnow.neoecoae.integration.ponder;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEItems;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

public class NEPonderTags {
    public static final ResourceLocation STORAGE_SYSTEM_COMPONENTS = id("storage_system_components");
    public static final ResourceLocation COMPUTATION_SYSTEM_COMPONENTS = id("computation_system_compoents");
    public static final ResourceLocation CRAFTING_SYSTEM_COMPONENTS = id("crafting_system_components");

    private static ResourceLocation id(String path) {
        return NeoECOAE.id(path);
    }

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderTagRegistrationHelper<RegistryEntry<?, ?>> helperEntry = helper.withKeyFunction(RegistryEntry::getId);

        PonderTagRegistrationHelper<ItemLike> itemHelper = helper.withKeyFunction(
            RegisteredObjectsHelper::getKeyOrThrow);

        helper.registerTag(STORAGE_SYSTEM_COMPONENTS)
            .addToIndex()
            .item(NEBlocks.STORAGE_SYSTEM_L4, true, false)
            .title("ECO Storage System")
            .description("")
            .register();

        helperEntry.addToTag(STORAGE_SYSTEM_COMPONENTS)
            .add(NEBlocks.STORAGE_SYSTEM_L4)
            .add(NEBlocks.STORAGE_SYSTEM_L6)
            .add(NEBlocks.STORAGE_SYSTEM_L9)
            .add(NEBlocks.STORAGE_CASING)
            .add(NEBlocks.STORAGE_VENT)
            .add(NEBlocks.ENERGY_CELL_L4)
            .add(NEBlocks.ENERGY_CELL_L6)
            .add(NEBlocks.ENERGY_CELL_L9)
            .add(NEBlocks.ECO_DRIVE)
            .add(NEItems.ECO_ITEM_CELL_16M)
            .add(NEItems.ECO_ITEM_CELL_64M)
            .add(NEItems.ECO_ITEM_CELL_256M)
            .add(NEItems.ECO_FLUID_CELL_16M)
            .add(NEItems.ECO_FLUID_CELL_64M)
            .add(NEItems.ECO_FLUID_CELL_256M);

    }
}
