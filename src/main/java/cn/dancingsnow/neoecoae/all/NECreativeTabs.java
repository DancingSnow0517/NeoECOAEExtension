package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.world.item.CreativeModeTab;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

@SuppressWarnings("Convert2MethodRef")
public class NECreativeTabs {
    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> STORAGE = REGISTRATE
        .defaultCreativeTab(
            "storage",
            CreativeModeTab.builder()
                .icon(() -> NEBlocks.STORAGE_SYSTEM_L9.asStack())
                .title(REGISTRATE.addLang("itemGroup", NeoECOAE.id("storage"), "ECO Storage System"))
        )
        .register();

    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> CRAFTING = REGISTRATE
        .defaultCreativeTab(
            "crafting",
            CreativeModeTab.builder()
                .icon(() -> NEBlocks.CRAFTING_SYSTEM_L9.asStack())
                .title(REGISTRATE.addLang("itemGroup", NeoECOAE.id("crafting"), "ECO Crafting System"))
                .withTabsBefore(STORAGE.getKey())
        )
        .register();

    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> COMPUTATION = REGISTRATE
        .defaultCreativeTab(
            "computation",
            CreativeModeTab.builder()
                .icon(() -> NEBlocks.COMPUTATION_INTERFACE.asStack())
                .title(REGISTRATE.addLang("itemGroup", NeoECOAE.id("computation"), "ECO Computation System"))
                .withTabsBefore(STORAGE.getKey(), CRAFTING.getKey())
        )
        .register();

    public static void register() {}
}
