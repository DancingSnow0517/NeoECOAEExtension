package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.world.item.CreativeModeTab;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NECreativeTabs {
    @SuppressWarnings("Convert2MethodRef")
    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> NEO_ECO_AE = REGISTRATE
        .defaultCreativeTab(
            "neoecoae",
            CreativeModeTab.builder()
                .icon(() -> NEItems.ECO_ITEM_CELL_16M.asStack())
                .title(REGISTRATE.addLang("itemGroup", NeoECOAE.id("neoecoae"), "Neo ECO AE Extension"))
            )
        .register();

    public static void register() {}
}
