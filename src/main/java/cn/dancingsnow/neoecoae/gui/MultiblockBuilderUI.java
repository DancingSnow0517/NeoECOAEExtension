package cn.dancingsnow.neoecoae.gui;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.multiblock.placement.RequiredItem;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class MultiblockBuilderUI {
    private static final int CONFLICT_TOOLTIP_LIMIT = 8;
    private static final int MATERIAL_COUNT_ENOUGH_COLOR = 0x55ff55;
    private static final int MATERIAL_COUNT_MISSING_COLOR = 0xff5555;

    private MultiblockBuilderUI() {
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
        Supplier<MultiBlockPlacementPlan> previewPlan
    ) {
    }

    public static UIElement createFloatingPanel(Config config) {
        UIElement window = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(6);
            layout.top(6);
            layout.display(TaffyDisplay.NONE);
            layout.paddingAll(4);
            layout.gapAll(5);
            layout.width(286);
        }).addClass("panel_bg");

        UIElement titleBar = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(6);
        });
        titleBar.addChild(new TextElement()
            .setText(Component.translatable("gui.neoecoae.multiblock.builder"))
            .textStyle(MultiblockBuilderUI::darkTextStyle));
        titleBar.addChild(new Button()
            .setText("X")
            .setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.NONE)))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.translatable("gui.neoecoae.multiblock.close_builder")),
                null,
                null,
                null
            ))
            .layout(layout -> layout.width(16).height(16)));
        WindowDragHelper.setDragMove(titleBar, window, null, null);
        window.addChild(titleBar);

        UIElement body = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(4);
        });
        body.addChild(createControlPanel(config));
        body.addChild(createMaterialPanel(config));
        window.addChild(body);
        return window;
    }

    public static UIElement createOpenButton(UIElement window) {
        UIElement buildButtonPanel = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(-22);
            layout.top(0);
            layout.paddingAll(2);
            layout.paddingBottom(4);
        }).style(style -> style.background(NETextures.BACKGROUND));
        buildButtonPanel.addChild(new Button()
            .noText()
            .addPostIcon(AETextures.icon(Icon.CRAFT_HAMMER))
            .setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.FLEX)))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.translatable("gui.neoecoae.multiblock.builder")),
                null,
                null,
                null
            ))
            .layout(layout -> {
                layout.width(18);
                layout.height(20);
            }));
        return buildButtonPanel;
    }

    private static UIElement createControlPanel(Config config) {
        UIElement panel = new UIElement().layout(layout -> {
            layout.width(116);
            layout.paddingAll(3);
            layout.gapAll(4);
        }).style(style -> style.background(NETextures.BACKGROUND));

        panel.addChild(sectionTitle("gui.neoecoae.multiblock.parameters"));

        panel.addChild(new UIElement()
            .layout(layout -> layout.flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(2))
            .addChildren(
                new Button()
                    .setText("-")
                    .setOnServerClick(event -> config.decreaseLength().run())
                    .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                        List.of(Component.translatable("gui.neoecoae.multiblock.decrease_length")),
                        null,
                        null,
                        null
                    ))
                    .layout(layout -> layout.width(18).height(18)),
                syncedLabel(() -> Component.translatable(
                        "gui.neoecoae.multiblock.length",
                        config.selectedLength().getAsInt()
                    ))
                    .textStyle(MultiblockBuilderUI::darkTextStyle)
                    .layout(layout -> layout.width(54)),
                new Button()
                    .setText("+")
                    .setOnServerClick(event -> config.increaseLength().run())
                    .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                        List.of(Component.translatable("gui.neoecoae.multiblock.increase_length")),
                        null,
                        null,
                        null
                    ))
                    .layout(layout -> layout.width(18).height(18))
            ));

        panel.addChild(new UIElement()
            .layout(layout -> layout.flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4))
            .addChildren(
                new TextElement()
                    .setText(Component.translatable("gui.neoecoae.multiblock.mirror"))
                    .textStyle(MultiblockBuilderUI::darkTextStyle)
                    .layout(layout -> layout.width(40)),
                new SegmentedBooleanControl(
                    config.mirrored(),
                    config.setMirrored(),
                    "gui.neoecoae.multiblock.mirror.off",
                    "gui.neoecoae.multiblock.mirror.on",
                    "gui.neoecoae.multiblock.mirror.off.tooltip",
                    "gui.neoecoae.multiblock.mirror.on.tooltip"
                )
            ));

        panel.addChild(sectionTitle("gui.neoecoae.multiblock.live_result"));
        panel.addChild(statLine("gui.neoecoae.multiblock.missing", () -> getMissingBlockCount(config)));
        panel.addChild(statLine("gui.neoecoae.multiblock.conflicts", () -> getConflictBlockCount(config)));
        panel.addChild(statLine("gui.neoecoae.multiblock.reused", () -> getReusedBlockCount(config)));
        panel.addChild(statLine("gui.neoecoae.multiblock.required_items", () -> getRequiredItemCount(config)));
        panel.addChild(syncedLabel(() -> buildStatusComponent(config))
            .textStyle(MultiblockBuilderUI::darkTextStyle));

        panel.addChild(sectionTitle("gui.neoecoae.multiblock.actions"));
        panel.addChild(new Button()
            .setText("gui.neoecoae.multiblock.build", true)
            .setOnServerClick(event -> config.build().run())
            .layout(layout -> layout.width(104).height(20)));
        panel.addChild(syncedLabel(() -> Component.translatable("gui.neoecoae.multiblock.auto_preview_hint"))
            .textStyle(MultiblockBuilderUI::hintTextStyle));

        return panel;
    }

    private static UIElement createMaterialPanel(Config config) {
        UIElement panel = new UIElement().layout(layout -> {
            layout.width(152);
            layout.paddingAll(3);
            layout.gapAll(5);
        }).style(style -> style.background(NETextures.BACKGROUND));

        panel.addChild(sectionTitle("gui.neoecoae.multiblock.materials"));

        UIElement materialBox = new UIElement().layout(layout -> {
            layout.paddingAll(1);
            layout.width(146);
            layout.height(56);
        }).style(style -> style.background(NETextures.INVENTORY_BORDER));
        UIElement grid = new UIElement().layout(layout -> {
            layout.gapAll(0);
            layout.height(58);
        });
        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            UIElement row = new UIElement().layout(layout -> {
                layout.flexDirection(FlexDirection.ROW);
                layout.gapAll(0);
            });
            for (int columnIndex = 0; columnIndex < 8; columnIndex++) {
                int index = rowIndex * 8 + columnIndex;
                RequiredItemSlot slot = new RequiredItemSlot(
                        () -> hasRequiredItem(config, index),
                        () -> getRequiredItem(config, index).count()
                    );
                slot.setValue(getRequiredItemStack(config, index));
                slot.bind(DataBindingBuilder.itemStackS2C(() -> getRequiredItemStack(config, index)).build());
                row.addChild(slot.layout(layout -> layout.width(18).height(18)));
            }
            grid.addChild(row);
        }
        materialBox.addChild(grid);
        panel.addChild(materialBox);

        panel.addChild(sectionTitle("gui.neoecoae.multiblock.conflict_preview"));
        Label conflictLabel = new Label();
        Supplier<Component> conflictText = () -> Component.translatable(
                "gui.neoecoae.multiblock.conflicts",
                getConflictBlockCount(config)
            );
        conflictLabel.setText(conflictText.get());
        conflictLabel.bind(DataBindingBuilder.componentS2C(conflictText).build());
        conflictLabel.textStyle(MultiblockBuilderUI::darkTextStyle);
        Component[] syncedConflictTooltip = {buildConflictTooltipComponent(getConflictPositions(config))};
        conflictLabel.addChild(new SyncReceiver<>(syncedConflictTooltip[0], value ->
            syncedConflictTooltip[0] = value == null ? Component.empty() : value)
            .bind(DataBindingBuilder.componentS2C(() ->
                buildConflictTooltipComponent(getConflictPositions(config))).build()));
        conflictLabel
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(syncedConflictTooltip[0]),
                null,
                null,
                null
            ));
        panel.addChild(conflictLabel);

        return panel;
    }

    private static Component buildStatusComponent(Config config) {
        if (config.formed().getAsBoolean()) {
            return Component.translatable("gui.neoecoae.multiblock.status.controller_formed");
        }
        if (config.buildInProgress().getAsBoolean()) {
            return Component.translatable("gui.neoecoae.multiblock.status.build_in_progress");
        }
        MultiBlockPlacementPlan plan = config.previewPlan().get();
        if (plan == null) {
            return Component.translatable("gui.neoecoae.multiblock.status.no_definition");
        }
        if (!plan.getConflictPositions().isEmpty()) {
            return Component.translatable("gui.neoecoae.multiblock.status.conflicts_detected");
        }
        if (plan.getMissingBlocks().isEmpty()) {
            return Component.translatable("gui.neoecoae.multiblock.status.structure_ready");
        }
        return hasRequiredItems(config)
            ? Component.translatable("gui.neoecoae.multiblock.status.ready_to_build")
            : Component.translatable("gui.neoecoae.multiblock.status.not_enough_items");
    }

    private static int getMissingBlockCount(Config config) {
        MultiBlockPlacementPlan plan = config.previewPlan().get();
        return plan == null ? 0 : plan.getMissingBlocks().size();
    }

    private static int getConflictBlockCount(Config config) {
        return getConflictPositions(config).size();
    }

    private static int getReusedBlockCount(Config config) {
        MultiBlockPlacementPlan plan = config.previewPlan().get();
        return plan == null ? 0 : plan.getReusedBlockCount();
    }

    private static int getRequiredItemCount(Config config) {
        MultiBlockPlacementPlan plan = config.previewPlan().get();
        return plan == null ? 0 : plan.getRequiredItemCount();
    }

    private static RequiredItem getRequiredItem(Config config, int index) {
        List<RequiredItem> requiredItems = getRequiredItems(config);
        if (index < 0 || index >= requiredItems.size()) {
            return new RequiredItem(ItemStack.EMPTY, 0);
        }
        return requiredItems.get(index);
    }

    private static ItemStack getRequiredItemStack(Config config, int index) {
        return getRequiredItem(config, index).stack();
    }

    private static boolean hasRequiredItem(Config config, int index) {
        RequiredItem requiredItem = getRequiredItem(config, index);
        if (requiredItem.isEmpty() || config.player().isCreative()) {
            return true;
        }
        return MultiBlockPlacementService.countMatchingItems(config.player(), requiredItem.stack()) >= requiredItem.count();
    }

    private static boolean hasRequiredItems(Config config) {
        if (config.player().isCreative()) {
            return true;
        }
        for (RequiredItem requiredItem : getRequiredItems(config)) {
            if (MultiBlockPlacementService.countMatchingItems(config.player(), requiredItem.stack()) < requiredItem.count()) {
                return false;
            }
        }
        return true;
    }

    private static List<RequiredItem> getRequiredItems(Config config) {
        MultiBlockPlacementPlan plan = config.previewPlan().get();
        return plan == null ? List.of() : plan.getRequiredItems();
    }

    private static List<BlockPos> getConflictPositions(Config config) {
        MultiBlockPlacementPlan plan = config.previewPlan().get();
        return plan == null ? List.of() : plan.getConflictPositions();
    }

    private static List<Component> buildConflictTooltip(List<BlockPos> positions) {
        List<Component> lines = new ArrayList<>();
        if (positions.isEmpty()) {
            lines.add(Component.translatable("gui.neoecoae.multiblock.no_conflicts"));
            return lines;
        }
        lines.add(Component.translatable("gui.neoecoae.multiblock.conflict_positions"));
        int limit = Math.min(CONFLICT_TOOLTIP_LIMIT, positions.size());
        for (int i = 0; i < limit; i++) {
            BlockPos pos = positions.get(i);
            lines.add(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        }
        if (positions.size() > limit) {
            lines.add(Component.translatable("gui.neoecoae.multiblock.more_conflicts", positions.size() - limit));
        }
        return lines;
    }

    private static Component buildConflictTooltipComponent(List<BlockPos> positions) {
        List<Component> lines = buildConflictTooltip(positions);
        var result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result.append("\n");
            }
            result.append(lines.get(i));
        }
        return result;
    }

    private static TextElement sectionTitle(String key) {
        return new TextElement()
            .setText(Component.translatable(key))
            .textStyle(MultiblockBuilderUI::sectionTextStyle);
    }

    private static Label statLine(String key, IntSupplier value) {
        Label label = syncedLabel(() -> Component.translatable(key, value.getAsInt()));
        label.textStyle(MultiblockBuilderUI::darkTextStyle);
        return label;
    }

    private static Label syncedLabel(Supplier<Component> text) {
        Label label = new Label();
        label.setText(text.get());
        label.bind(DataBindingBuilder.componentS2C(text).build());
        return label;
    }

    private static void darkTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0x3f3d52).textShadow(false);
    }

    private static void hintTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0x6d6a82).textShadow(false);
    }

    private static void sectionTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0x236f80).textShadow(false);
    }

    private static final class SegmentedBooleanControl extends UIElement implements IBindable<Boolean> {
        private final BooleanSupplier selected;
        private final Button falseButton;
        private final Button trueButton;
        private boolean syncedSelected;

        private SegmentedBooleanControl(
            BooleanSupplier selected,
            Consumer<Boolean> setSelected,
            String falseKey,
            String trueKey,
            String falseTooltipKey,
            String trueTooltipKey
        ) {
            this.selected = selected;
            this.syncedSelected = selected.getAsBoolean();
            layout(layout -> {
                layout.flexDirection(FlexDirection.ROW);
                layout.gapAll(1);
                layout.width(66);
                layout.height(18);
            });
            falseButton = createSegmentButton(falseKey, falseTooltipKey, false, setSelected);
            trueButton = createSegmentButton(trueKey, trueTooltipKey, true, setSelected);
            addChildren(falseButton, trueButton);
            bind(DataBindingBuilder.boolS2C(selected::getAsBoolean).build());
            addEventListener(UIEvents.TICK, event -> refreshButtonStyles());
            refreshButtonStyles();
        }

        private Button createSegmentButton(String key, String tooltipKey, boolean value, Consumer<Boolean> setSelected) {
            Button button = new Button();
            button.setText(key, true);
            button.textStyle(MultiblockBuilderUI::darkTextStyle);
            button.setOnServerClick(event -> {
                if (selected.getAsBoolean() != value) {
                    setSelected.accept(value);
                }
            });
            button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                    List.of(Component.translatable(tooltipKey)),
                    null,
                    null,
                    null
                )
            );
            button.layout(layout -> layout.width(32).height(18));
            return button;
        }

        private void refreshButtonStyles() {
            applySegmentButtonStyle(falseButton, !syncedSelected);
            applySegmentButtonStyle(trueButton, syncedSelected);
        }

        @Override
        public Boolean getValue() {
            return syncedSelected;
        }

        @Override
        public IDataSource<Boolean> setValue(@Nullable Boolean value) {
            syncedSelected = Boolean.TRUE.equals(value);
            refreshButtonStyles();
            return this;
        }

        private void applySegmentButtonStyle(Button button, boolean active) {
            button.buttonStyle(style -> style
                .baseTexture(active ? NETextures.BUTTON_HIGHLIGHTED : NETextures.BUTTON)
                .hoverTexture(NETextures.BUTTON_HIGHLIGHTED)
                .pressedTexture(NETextures.BUTTON_HIGHLIGHTED));
        }
    }

    private static final class RequiredItemSlot extends ItemSlot {
        private boolean syncedHasRequiredItem;
        private int syncedCount;

        private RequiredItemSlot(BooleanSupplier hasRequiredItem, IntSupplier count) {
            syncedHasRequiredItem = hasRequiredItem.getAsBoolean();
            syncedCount = count.getAsInt();
            getStyle().backgroundTexture(NETextures.ITEM_SLOT);
            addChild(new SyncReceiver<>(syncedHasRequiredItem, value -> syncedHasRequiredItem = Boolean.TRUE.equals(value))
                .bind(DataBindingBuilder.boolS2C(hasRequiredItem::getAsBoolean).build()));
            addChild(new SyncReceiver<>(syncedCount, value -> syncedCount = value == null ? 0 : value)
                .bind(DataBindingBuilder.intValS2C(count::getAsInt).build()));
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                ItemStack stack = getValue();
                if (stack.isEmpty()) {
                    return;
                }
                Component state = syncedHasRequiredItem
                    ? Component.translatable("gui.neoecoae.multiblock.material_enough")
                    : Component.translatable("gui.neoecoae.multiblock.material_missing");
                event.hoverTooltips = new HoverTooltips(
                    List.of(stack.getHoverName(),
                        Component.translatable("gui.neoecoae.multiblock.item_required", syncedCount), state),
                    null, null, null
                );
            });
        }

        @Override
        protected void drawItemStack(GUIContext guiContext, ItemStack itemStack) {
            if (itemStack.isEmpty()) {
                return;
            }
            DrawerHelper.drawItemStack(guiContext.graphics, itemStack.copyWithCount(1), 0, 0, -1, null);
            int color = syncedHasRequiredItem ? MATERIAL_COUNT_ENOUGH_COLOR : MATERIAL_COUNT_MISSING_COLOR;
            guiContext.graphics.pose().pushPose();
            guiContext.graphics.pose().translate(0, 0, 240);
            DrawerHelper.drawStringFixedCorner(guiContext.graphics, String.valueOf(syncedCount), 17, 17, color, true, 0.8f);
            guiContext.graphics.pose().popPose();
        }
    }

    private static final class SyncReceiver<T> extends UIElement implements IBindable<T> {
        private T value;
        private final Consumer<T> consumer;

        private SyncReceiver(T value, Consumer<T> consumer) {
            this.value = value;
            this.consumer = consumer;
            layout(layout -> layout.width(0).height(0));
        }

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public IDataSource<T> setValue(@Nullable T value) {
            this.value = value;
            consumer.accept(value);
            return this;
        }
    }
}
