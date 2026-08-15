package cn.dancingsnow.neoecoae.gui.multiblock;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
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
        MultiblockPreviewSync previewSync = new MultiblockPreviewSync(config.player(), () ->
            MultiblockPreviewSnapshot.capture(config.player(), config.formed(), config.buildInProgress(), config.previewPlan()));
        window.addChild(previewSync);
        PreviewElements previewElements = new PreviewElements();
        body.addChild(createControlPanel(config, previewElements));
        body.addChild(createMaterialPanel(previewElements));
        previewSync.subscribe(previewElements::update);
        window.addChild(body);
        return window;
    }

    public static UIElement createOpenButton(UIElement window) {
        return HostSideButtonBar.left(createInlineOpenButton(window));
    }

    public static Button createInlineOpenButton(UIElement window) {
        Button button = new Button()
            .noText()
            .addPostIcon(AETextures.icon(Icon.VIEW_MODE_ALL))
            .setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.FLEX)));
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.translatable("gui.neoecoae.multiblock.builder")),
                null,
                null,
                null
            ));
        button.layout(layout -> {
            layout.width(18);
            layout.height(20);
        });
        return button;
    }

    private static UIElement createControlPanel(Config config, PreviewElements previewElements) {
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
        panel.addChild(previewElements.missingLabel);
        panel.addChild(previewElements.conflictsLabel);
        panel.addChild(previewElements.reusedLabel);
        panel.addChild(previewElements.requiredItemsLabel);
        panel.addChild(previewElements.statusLabel);

        panel.addChild(sectionTitle("gui.neoecoae.multiblock.actions"));
        panel.addChild(new Button()
            .setText("gui.neoecoae.multiblock.build", true)
            .setOnServerClick(event -> config.build().run())
            .layout(layout -> layout.width(104).height(20)));
        panel.addChild(syncedLabel(() -> Component.translatable("gui.neoecoae.multiblock.auto_preview_hint"))
            .textStyle(MultiblockBuilderUI::hintTextStyle));

        return panel;
    }

    private static UIElement createMaterialPanel(PreviewElements previewElements) {
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
                RequiredItemSlot slot = new RequiredItemSlot();
                previewElements.materialSlots[index] = slot;
                row.addChild(slot.layout(layout -> layout.width(18).height(18)));
            }
            grid.addChild(row);
        }
        materialBox.addChild(grid);
        panel.addChild(materialBox);

        panel.addChild(sectionTitle("gui.neoecoae.multiblock.conflict_preview"));
        panel.addChild(previewElements.conflictLabel);

        return panel;
    }

    private static Component buildStatusComponent(MultiblockPreviewSnapshot snapshot) {
        if (snapshot.status() == MultiblockPreviewSnapshot.Status.CONTROLLER_FORMED) {
            return Component.translatable("gui.neoecoae.multiblock.status.controller_formed");
        }
        if (snapshot.status() == MultiblockPreviewSnapshot.Status.BUILD_IN_PROGRESS) {
            return Component.translatable("gui.neoecoae.multiblock.status.build_in_progress");
        }
        if (snapshot.status() == MultiblockPreviewSnapshot.Status.NO_DEFINITION) {
            return Component.translatable("gui.neoecoae.multiblock.status.no_definition");
        }
        if (snapshot.status() == MultiblockPreviewSnapshot.Status.CONFLICTS_DETECTED) {
            return Component.translatable("gui.neoecoae.multiblock.status.conflicts_detected");
        }
        if (snapshot.status() == MultiblockPreviewSnapshot.Status.STRUCTURE_READY) {
            return Component.translatable("gui.neoecoae.multiblock.status.structure_ready");
        }
        return snapshot.status() == MultiblockPreviewSnapshot.Status.READY_TO_BUILD
            ? Component.translatable("gui.neoecoae.multiblock.status.ready_to_build")
            : Component.translatable("gui.neoecoae.multiblock.status.not_enough_items");
    }

    private static List<Component> buildConflictTooltip(List<BlockPos> positions, int totalCount) {
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
        if (totalCount > limit) {
            lines.add(Component.translatable("gui.neoecoae.multiblock.more_conflicts", totalCount - limit));
        }
        return lines;
    }

    private static Component buildConflictTooltipComponent(List<BlockPos> positions, int totalCount) {
        List<Component> lines = buildConflictTooltip(positions, totalCount);
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

    private static final class PreviewElements {
        private final Label missingLabel = previewLabel();
        private final Label conflictsLabel = previewLabel();
        private final Label reusedLabel = previewLabel();
        private final Label requiredItemsLabel = previewLabel();
        private final Label statusLabel = previewLabel();
        private final RequiredItemSlot[] materialSlots = new RequiredItemSlot[MultiblockPreviewSnapshot.MAX_MATERIALS];
        private final Label conflictLabel = previewLabel();
        private Component conflictTooltip = buildConflictTooltipComponent(List.of(), 0);

        private PreviewElements() {
            conflictLabel.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(conflictTooltip), null, null, null));
        }

        private void update(MultiblockPreviewSnapshot snapshot) {
            missingLabel.setText(Component.translatable("gui.neoecoae.multiblock.missing", snapshot.missing()));
            conflictsLabel.setText(Component.translatable("gui.neoecoae.multiblock.conflicts", snapshot.conflictCount()));
            reusedLabel.setText(Component.translatable("gui.neoecoae.multiblock.reused", snapshot.reused()));
            requiredItemsLabel.setText(Component.translatable("gui.neoecoae.multiblock.required_items", snapshot.requiredCount()));
            statusLabel.setText(buildStatusComponent(snapshot));
            conflictLabel.setText(Component.translatable("gui.neoecoae.multiblock.conflicts", snapshot.conflictCount()));
            conflictTooltip = buildConflictTooltipComponent(snapshot.conflicts(), snapshot.conflictCount());
            for (int index = 0; index < materialSlots.length; index++) {
                RequiredItemSlot slot = materialSlots[index];
                if (slot != null) {
                    slot.update(index < snapshot.materials().size() ? snapshot.materials().get(index) : null);
                }
            }
        }

        private static Label previewLabel() {
            Label label = new Label();
            label.textStyle(MultiblockBuilderUI::darkTextStyle);
            return label;
        }
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
        private boolean enough = true;
        private int required;

        private RequiredItemSlot() {
            getStyle().backgroundTexture(NETextures.ITEM_SLOT);
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                ItemStack stack = getValue();
                if (stack.isEmpty()) {
                    return;
                }
                Component state = enough
                    ? Component.translatable("gui.neoecoae.multiblock.material_enough")
                    : Component.translatable("gui.neoecoae.multiblock.material_missing");
                event.hoverTooltips = new HoverTooltips(
                    List.of(stack.getHoverName(),
                        Component.translatable("gui.neoecoae.multiblock.item_required", required), state),
                    null, null, null
                );
            });
        }

        private void update(@Nullable MultiblockPreviewSnapshot.Material material) {
            ItemStack stack = material == null ? ItemStack.EMPTY : material.stack();
            setValue(stack);
            required = material == null ? 0 : material.required();
            enough = material == null || material.enough();
        }

        @Override
        protected void drawItemStack(GUIContext guiContext, ItemStack itemStack) {
            if (itemStack.isEmpty()) {
                return;
            }
            DrawerHelper.drawItemStack(guiContext.graphics, itemStack.copyWithCount(1), 0, 0, -1, null);
            int color = enough ? MATERIAL_COUNT_ENOUGH_COLOR : MATERIAL_COUNT_MISSING_COLOR;
            guiContext.graphics.pose().pushPose();
            guiContext.graphics.pose().translate(0, 0, 240);
            DrawerHelper.drawStringFixedCorner(guiContext.graphics, String.valueOf(required), 17, 17, color, true, 0.8f);
            guiContext.graphics.pose().popPose();
        }
    }

}
