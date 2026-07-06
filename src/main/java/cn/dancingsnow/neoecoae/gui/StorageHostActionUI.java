package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.world.entity.player.Player;

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
        IntConsumer changePriority
    ) {
    }

    public record Elements(
        UIElement buildWindow,
        UIElement priorityWindow
    ) {
        public void addTo(UIElement root) {
            root.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
            root.addChild(StoragePriorityUI.createOpenButton(priorityWindow));
            root.addChild(buildWindow);
            root.addChild(priorityWindow);
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
        return new Elements(buildWindow, priorityWindow);
    }
}
