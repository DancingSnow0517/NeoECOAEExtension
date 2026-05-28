package cn.dancingsnow.neoecoae.gui.nativeui;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEComputationControllerMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NECraftingControllerMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NECraftingPatternBusMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEFluidHatchMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEIntegratedWorkingStationMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStorageControllerMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for native Minecraft/Forge MenuTypes.
 * Replaces LDLib's {@code BlockEntityUIFactory} UI opening mechanism.
 */
public final class NENativeMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, NeoECOAE.MOD_ID);

    /**
     * Storage Controller menu — Phase 1 proof of concept.
     */
    public static final RegistryObject<MenuType<NEStorageControllerMenu>> STORAGE_CONTROLLER =
        MENUS.register("storage_controller",
            () -> IForgeMenuType.create(
                (windowId, inv, data) -> {
                    BlockPos pos = data.readBlockPos();
                    return new NEStorageControllerMenu(windowId, inv, pos);
                }));

    /**
     * Computation Controller menu — Phase 2 proof of concept.
     */
    public static final RegistryObject<MenuType<NEComputationControllerMenu>> COMPUTATION_CONTROLLER =
        MENUS.register("computation_controller",
            () -> IForgeMenuType.create(
                (windowId, inv, data) -> {
                    BlockPos pos = data.readBlockPos();
                    return new NEComputationControllerMenu(windowId, inv, pos);
                }));

    /**
     * Crafting Controller menu — Phase 3 proof of concept.
     */
    public static final RegistryObject<MenuType<NECraftingControllerMenu>> CRAFTING_CONTROLLER =
        MENUS.register("crafting_controller",
            () -> IForgeMenuType.create(
                (windowId, inv, data) -> {
                    BlockPos pos = data.readBlockPos();
                    return new NECraftingControllerMenu(windowId, inv, pos);
                }));

    /**
     * Integrated Working Station menu — Phase 4 proof of concept.
     */
    public static final RegistryObject<MenuType<NEIntegratedWorkingStationMenu>> INTEGRATED_WORKING_STATION =
        MENUS.register("integrated_working_station",
            () -> IForgeMenuType.create(
                (windowId, inv, data) -> {
                    BlockPos pos = data.readBlockPos();
                    return new NEIntegratedWorkingStationMenu(windowId, inv, pos);
                }));

    /**
     * Crafting Pattern Bus menu — Phase 5 proof of concept.
     */
    public static final RegistryObject<MenuType<NECraftingPatternBusMenu>> CRAFTING_PATTERN_BUS =
        MENUS.register("crafting_pattern_bus",
            () -> IForgeMenuType.create(
                (windowId, inv, data) -> {
                    BlockPos pos = data.readBlockPos();
                    return new NECraftingPatternBusMenu(windowId, inv, pos);
                }));

    /**
     * Fluid Hatch menu (Input / Output) — Phase 6 proof of concept.
     * A single generic MenuType serves both input and output hatches.
     */
    public static final RegistryObject<MenuType<NEFluidHatchMenu>> FLUID_HATCH =
        MENUS.register("fluid_hatch",
            () -> IForgeMenuType.create(
                (windowId, inv, data) -> {
                    BlockPos pos = data.readBlockPos();
                    return new NEFluidHatchMenu(windowId, inv, pos);
                }));

    /**
     * Structure Terminal menu for multiblock build operations.
     */
    public static final RegistryObject<MenuType<NEStructureTerminalMenu>> STRUCTURE_TERMINAL =
        MENUS.register("structure_terminal",
            () -> IForgeMenuType.create(
                (windowId, inv, data) -> {
                    BlockPos pos = data.readBlockPos();
                    return new NEStructureTerminalMenu(windowId, inv, pos);
                }));

    private NENativeMenus() {
    }
}
