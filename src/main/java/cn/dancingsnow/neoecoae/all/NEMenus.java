package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingPatternMenu;
import cn.dancingsnow.neoecoae.menu.HostDiskMenu;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class NEMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, NeoECOAE.MOD_ID);

    public static final Supplier<MenuType<LargeIntegratedWorkingStationPatternProviderMenu>>
        LARGE_INTEGRATED_WORKING_STATION_INTERFACE = MENU_TYPES.register(
            "large_integrated_working_station_interface",
            () -> LargeIntegratedWorkingStationPatternProviderMenu.TYPE
        );

    public static final Supplier<MenuType<ModularUIContainerMenu>> HOST_DISK =
        MENU_TYPES.register("host_disk", () -> HostDiskMenu.TYPE);
    public static final Supplier<MenuType<ModularUIContainerMenu>> CRAFTING_PATTERNS =
        MENU_TYPES.register("crafting_patterns", () -> CraftingPatternMenu.TYPE);

    private NEMenus() {
    }

    public static void register(IEventBus modBus) {
        MENU_TYPES.register(modBus);
    }
}
