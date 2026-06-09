package cn.dancingsnow.neoecoae.gui.ldlib;

import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEComputationControllerWidget;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NECraftingControllerWidget;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NECraftingPatternBusWidget;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEFluidHatchWidget;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEIntegratedWorkingStationWidget;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEStorageControllerWidget;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEStructureTerminalWidget;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.world.entity.player.Player;

public final class NELDLibUis {

    public static ModularUI createStorageController(ECOStorageSystemBlockEntity storage, Player player) {
        return new ModularUI(358, 220, storage, player).widget(new NEStorageControllerWidget(storage, player));
    }

    public static ModularUI createComputationController(ECOComputationSystemBlockEntity computation, Player player) {
        return new ModularUI(300, 170, computation, player).widget(new NEComputationControllerWidget(computation));
    }

    public static ModularUI createCraftingController(ECOCraftingSystemBlockEntity crafting, Player player) {
        return new ModularUI(372, 240, crafting, player).widget(new NECraftingControllerWidget(crafting));
    }

    public static ModularUI createPatternBus(ECOCraftingPatternBusBlockEntity bus, Player player) {
        return new ModularUI(176, 246, bus, player).widget(new NECraftingPatternBusWidget(bus, player.getInventory()));
    }

    public static ModularUI createIntegratedWorkingStation(
            ECOIntegratedWorkingStationBlockEntity station, Player player) {
        return new ModularUI(
                        NEIntegratedWorkingStationWidget.UI_WIDTH,
                        NEIntegratedWorkingStationWidget.UI_HEIGHT,
                        station,
                        player)
                .widget(new NEIntegratedWorkingStationWidget(station, player.getInventory()));
    }

    public static ModularUI createFluidInputHatch(ECOFluidInputHatchBlockEntity hatch, Player player) {
        return new ModularUI(220, 110, hatch, player)
                .widget(new NEFluidHatchWidget(hatch.getBlockState().getBlock().getName(), hatch.tank));
    }

    public static ModularUI createFluidOutputHatch(ECOFluidOutputHatchBlockEntity hatch, Player player) {
        return new ModularUI(220, 110, hatch, player)
                .widget(new NEFluidHatchWidget(hatch.getBlockState().getBlock().getName(), hatch.tank));
    }

    public static ModularUI createStructureTerminal(Player player, HeldItemUIFactory.HeldItemHolder holder) {
        return new ModularUI(NEStructureTerminalWidget.WIDTH, NEStructureTerminalWidget.HEIGHT, holder, player)
                .widget(new NEStructureTerminalWidget(holder));
    }

    private NELDLibUis() {}
}
