package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.common.GuideButton;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.common.MultiblockBuildUI;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyDisplay;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

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
            addTo(root, null);
        }

        public void addTo(UIElement root, MultiblockBuildUI.Elements builder) {
            Button priorityButton = StoragePriorityUI.createInlineOpenButton(priorityWindow);
            List<UIElement> buttons = new ArrayList<>(List.of(
                GuideButton.create(player, "neoecoae:neoecoae_intro/storage_system.md"),
                priorityButton
            ));
            if (builder != null) {
                priorityButton.setOnClick(event -> {
                    builder.window().setDisplay(false);
                    priorityWindow.layout(layout -> layout.display(TaffyDisplay.FLEX));
                });
                builder.openButton().setOnClick(event -> {
                    priorityWindow.layout(layout -> layout.display(TaffyDisplay.NONE));
                    builder.window().setDisplay(true);
                });
                buttons.add(builder.openButton());
            }
            root.addChild(HostSideButtonBar.left(buttons.toArray(UIElement[]::new)));
            root.addChild(priorityWindow);
            if (builder != null) root.addChild(builder.window());
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
