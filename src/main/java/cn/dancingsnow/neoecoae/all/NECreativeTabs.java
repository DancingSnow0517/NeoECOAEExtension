package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings("Convert2MethodRef")
public class NECreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NeoECOAE.MOD_ID);

    public static final RegistryObject<CreativeModeTab> ECO = TABS.register("main", () -> CreativeModeTab.builder()
            .icon(() -> NEBlocks.STORAGE_SYSTEM_L9.asStack())
            .title(Component.translatable("itemGroup.neoecoae.main"))
            .displayItems(NECreativeTabOrder::acceptAll)
            .build());

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
        NeoECOAE.REGISTRATE.clearDefaultCreativeTab();
    }
}
