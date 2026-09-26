package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** NeoECOAE-owned registration for the copied AE2 pattern-provider menu. */
public final class NEMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, NeoECOAE.MOD_ID);

    public static final Supplier<MenuType<LargeIntegratedWorkingStationPatternProviderMenu>>
        LARGE_INTEGRATED_WORKING_STATION_INTERFACE = MENU_TYPES.register(
            "large_integrated_working_station_interface",
            () -> LargeIntegratedWorkingStationPatternProviderMenu.TYPE
        );

    private NEMenus() {
    }

    public static void register(IEventBus modBus) {
        MENU_TYPES.register(modBus);
    }
}
