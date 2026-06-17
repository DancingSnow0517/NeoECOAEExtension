package cn.dancingsnow.neoecoae.gui.host;

import appeng.api.config.CpuSelectionMode;
import appeng.core.definitions.AEParts;
import appeng.core.localization.ButtonToolTips;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

public final class NEComputationHostUI {
    private NEComputationHostUI() {
    }

    public static ModularUI create(
        ECOComputationSystemBlockEntity computation,
        BlockUIMenuType.BlockUIHolder holder,
        UIElement buildWindow
    ) {
        CpuModeState cpuModeState = new CpuModeState(computation.getCpuSelectionMode());
        UIElement root = new UIElement().layout(layout -> {
            layout.width(NEComputationAeCanvas.UI_WIDTH);
            layout.height(NEComputationAeCanvas.UI_HEIGHT);
        });
        root.addChild(new NEComputationAeCanvas(computation, cpuModeState::set));
        root.addChild(cpuModeButton(computation, cpuModeState));
        root.addChild(NEAeInventorySlots.create(
            NEComputationAeCanvas.PLAYER_INV_X,
            NEComputationAeCanvas.PLAYER_INV_Y,
            NEComputationAeCanvas.PLAYER_HOTBAR_Y
        ));
        root.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
        root.addChild(buildWindow);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static Button cpuModeButton(ECOComputationSystemBlockEntity computation, Supplier<CpuSelectionMode> cpuMode) {
        Button button = NEAeButtons.aeToolbarContent(
            () -> cpuModeIcon(cpuMode.get()),
            () -> cpuModeItem(cpuMode.get())
        );
        button.setOnServerClick(event -> computation.cycleCpuSelectionMode());
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            event.hoverTooltips = new HoverTooltips(List.of(
                ButtonToolTips.CpuSelectionMode.text(),
                cpuModeTooltip(cpuMode.get()),
                Component.translatable("gui.neoecoae.computation.cpu_selection_mode.click")
            ), null, null, null);
            event.stopPropagation();
        });
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(NEComputationAeCanvas.TOOLBAR_BUTTON_X);
            layout.top(NEComputationAeCanvas.TOOLBAR_BUTTON_Y);
            layout.width(NEComputationAeCanvas.TOOLBAR_BUTTON_W);
            layout.height(NEComputationAeCanvas.TOOLBAR_BUTTON_H);
        });
        return button;
    }

    private static NEAeSprite cpuModeIcon(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> NEAeSprite.CRAFT_HAMMER;
            case PLAYER_ONLY, MACHINE_ONLY -> null;
        };
    }

    private static ItemStack cpuModeItem(CpuSelectionMode mode) {
        return switch (mode) {
            case PLAYER_ONLY -> AEParts.TERMINAL.stack();
            case MACHINE_ONLY -> AEParts.EXPORT_BUS.stack();
            case ANY -> ItemStack.EMPTY;
        };
    }

    private static Component cpuModeTooltip(CpuSelectionMode mode) {
        return switch (mode) {
            case PLAYER_ONLY -> ButtonToolTips.CpuSelectionModePlayersOnly.text();
            case MACHINE_ONLY -> ButtonToolTips.CpuSelectionModeAutomationOnly.text();
            case ANY -> ButtonToolTips.CpuSelectionModeAny.text();
        };
    }

    private static final class CpuModeState implements Supplier<CpuSelectionMode> {
        private CpuSelectionMode mode;

        private CpuModeState(CpuSelectionMode mode) {
            this.mode = mode == null ? CpuSelectionMode.ANY : mode;
        }

        private void set(CpuSelectionMode mode) {
            this.mode = mode == null ? CpuSelectionMode.ANY : mode;
        }

        @Override
        public CpuSelectionMode get() {
            return mode;
        }
    }
}
