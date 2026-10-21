package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.common.GuideButton;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;

import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class StorageHostActionUI {
    private StorageHostActionUI() {
    }

    public record Config(
        Player player,
        IntSupplier priority,
        IntConsumer setPriority,
        IntConsumer changePriority
    ) {
    }

    public record Elements(
        Config config,
        Player player,
        UIElement priorityWindow
    ) {
        public void addTo(UIElement root) {
            List<UIElement> buttons = List.of(
                GuideButton.create(player, "neoecoae:neoecoae_intro/storage_system.md"),
                StoragePriorityUI.createInlineOpenButton(priorityWindow)
            );
            root.addChild(HostSideButtonBar.left(buttons.toArray(UIElement[]::new)));
            root.addChild(priorityWindow);
        }
    }

    public static Elements create(Config config) {
        UIElement priorityWindow = StoragePriorityUI.createFloatingPanel(new StoragePriorityUI.Config(
            config.priority(),
            config.setPriority(),
            config.changePriority()
        ));
        return new Elements(config, config.player(), priorityWindow);
    }
}
