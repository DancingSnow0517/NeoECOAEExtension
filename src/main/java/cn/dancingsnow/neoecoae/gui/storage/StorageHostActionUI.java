package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.multiblock.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.common.GuideButton;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;

import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.world.entity.player.Player;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.List;
import java.util.function.Supplier;

public final class StorageHostActionUI {
    private StorageHostActionUI() {
    }

    public record Config(
        Player player,
        IntSupplier selectedLength,
        BooleanSupplier mirrored,
        Consumer<Boolean> setMirrored,
        Runnable decreaseLength,
        Runnable increaseLength,
        Runnable build,
        BooleanSupplier formed,
        BooleanSupplier buildInProgress,
        Supplier<MultiBlockPlacementPlan> previewPlan,
        IntSupplier priority,
        IntConsumer setPriority,
        IntConsumer changePriority,
        Supplier<List<StorageItemDetailsUI.StoredItem>> items,
        StorageItemDetailsUI.InteractionHandler interactionHandler
    ) {
    }

    public record Elements(
        Player player,
        UIElement buildWindow,
        UIElement priorityWindow,
        UIElement itemDetailsWindow
    ) {
        public void addTo(UIElement root) {
            root.addChild(HostSideButtonBar.left(
                GuideButton.create(player, "neoecoae:neoecoae_intro/storage_system.md"),
                MultiblockBuilderUI.createInlineOpenButton(buildWindow),
                StoragePriorityUI.createInlineOpenButton(priorityWindow),
                StorageItemDetailsUI.createInlineOpenButton(itemDetailsWindow)
            ));
            root.addChild(buildWindow);
            root.addChild(priorityWindow);
            root.addChild(itemDetailsWindow);
        }
    }

    public static Elements create(Config config) {
        UIElement buildWindow = MultiblockBuilderUI.createFloatingPanel(new MultiblockBuilderUI.Config(
            config.player(),
            config.selectedLength(),
            config.mirrored(),
            config.setMirrored(),
            config.decreaseLength(),
            config.increaseLength(),
            config.build(),
            config.formed(),
            config.buildInProgress(),
            config.previewPlan()
        ));
        UIElement priorityWindow = StoragePriorityUI.createFloatingPanel(new StoragePriorityUI.Config(
            config.priority(),
            config.setPriority(),
            config.changePriority()
        ));
        UIElement itemDetailsWindow = StorageItemDetailsUI.createFloatingPanel(new StorageItemDetailsUI.Config(
            config.player(),
            config.items(),
            config.interactionHandler()
        ));
        return new Elements(config.player(), buildWindow, priorityWindow, itemDetailsWindow);
    }
}
