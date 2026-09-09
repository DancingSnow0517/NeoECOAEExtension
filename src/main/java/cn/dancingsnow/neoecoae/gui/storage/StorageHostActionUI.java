package cn.dancingsnow.neoecoae.gui.storage;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.gui.multiblock.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.common.GuideButton;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;

import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
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
        BooleanSupplier bulkMarkingAvailable,
        LongSupplier bulkMarkingThreshold,
        Runnable autoMarkBulkCells
    ) {
    }

    public record Elements(
        Config config,
        Player player,
        UIElement buildWindow,
        UIElement priorityWindow
    ) {
        public void addTo(UIElement root) {
            List<UIElement> buttons = new ArrayList<>(List.of(
                GuideButton.create(player, "neoecoae:neoecoae_intro/storage_system.md"),
                MultiblockBuilderUI.createInlineOpenButton(buildWindow),
                StoragePriorityUI.createInlineOpenButton(priorityWindow)
            ));
            if (config.bulkMarkingAvailable().getAsBoolean()) {
                buttons.add(createBulkMarkingButton(config));
            }
            root.addChild(HostSideButtonBar.left(buttons));
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
        return new Elements(config, config.player(), buildWindow, priorityWindow);
    }

    private static Button createBulkMarkingButton(Config config) {
        Button button = HostSideButtonBar.createButton()
            .noText()
            .addPostIcon(AETextures.icon(Icon.TYPE_FILTER_ALL))
            .setOnServerClick(event -> config.autoMarkBulkCells().run());
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
            List.of(Component.translatable(
                "gui.neoecoae.storage.bulk_mark", config.bulkMarkingThreshold().getAsLong())),
            null,
            null,
            null));
        return button;
    }
}
