package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.world.item.CreativeModeTab;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

@SuppressWarnings("Convert2MethodRef")
public class NECreativeTabs {
    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> ECO = REGISTRATE
        .defaultCreativeTab(
            "main",
            CreativeModeTab.builder()
                .icon(() -> NEBlocks.STORAGE_SYSTEM_L9.asStack())
                .title(REGISTRATE.addLang("itemGroup", NeoECOAE.id("main"), "Neo ECO AE Extension"))
        )
        .register();

    public static void register() {}
}
