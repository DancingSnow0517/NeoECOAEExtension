package cn.dancingsnow.neoecoae.gui.multiblock;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.multiblock.placement.RequiredItem;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import cn.dancingsnow.neoecoae.gui.NEGuiColors;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
import com.google.gson.JsonObject;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
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
    private static final int MATERIAL_COUNT_ENOUGH_COLOR = NEGuiColors.textColor(0x55ff55);
    private static final int MATERIAL_COUNT_MISSING_COLOR = NEGuiColors.textColor(0xff5555);
    private static final int MATERIAL_COLUMNS = 5;
    private static final int MATERIAL_SLOT_COUNT = 24;
    private static final IGuiTexture BACKGROUND = SpriteTexture.of(
        NeoECOAE.id("textures/guis/multiblock_builder.png")).setSprite(0, 0, 176, 177);

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
        Supplier<MultiBlockPlacementPlan> previewPlan,
        Supplier<MultiBlockDefinition> definition
    ) {
    }

    public static UIElement createFloatingPanel(Config config) {
        UIElement[] windowRef = new UIElement[1];
        UIElement window = NeoUiJsonLoader.load(NeoECOAE.id("screens/multiblock_builder"),
            new NeoUiJsonContext() {
                @Override
                public UIElement createCustom(JsonObject node) {
                    return switch (node.has("name") ? node.get("name").getAsString() : "") {
                        case "multiblock_window" -> new UIElement();
                        case "multiblock_preview" -> createPreview(config);
                        case "multiblock_controls" -> createControlPanel(config);
                        case "multiblock_materials" -> createMaterialPanel(config);
                        case "multiblock_footer" -> createFooter(config);
                        default -> null;
                    };
                }

                @Override
                public void configure(JsonObject node, UIElement element) {
                    String name = node.has("name") ? node.get("name").getAsString() : "";
                    if ("multiblock_window".equals(name)) {
                        element.layout(layout -> layout.display(TaffyDisplay.NONE)
                            .paddingAll(0).gapAll(0));
                        element.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
                    } else if ("multiblock_artwork".equals(name)) {
                        element.style(style -> style.backgroundTexture(BACKGROUND));
                    } else if ("multiblock_title_bar".equals(name)) {
                        element.layout(layout -> layout.paddingAll(0).gapAll(0));
                    } else if ("multiblock_title".equals(name) && element instanceof TextElement text) {
                        text.textStyle(MultiblockBuilderUI::darkTextStyle);
                    } else if ("multiblock_close".equals(name) && element instanceof Button close) {
                        close.layout(layout -> layout.paddingAll(0));
                        centerButtonText(close);
                        close.setOnClick(event -> windowRef[0].layout(layout -> layout.display(TaffyDisplay.NONE)));
                        close.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                            List.of(Component.translatable("gui.neoecoae.multiblock.close_builder")),
                            null, null, null, null));
                    }
                }
            });
        windowRef[0] = window;
        window.selectId("multiblock-title-bar").findFirst()
            .ifPresent(titleBar -> WindowDragHelper.setDragMove(titleBar, window, null, null));
        return window;
    }

    public static UIElement createOpenButton(UIElement window) {
        return HostSideButtonBar.left(createInlineOpenButton(window));
    }

    public static Button createInlineOpenButton(UIElement window) {
        Button button = HostSideButtonBar.createButton();
        button.noText().addPostIcon(NETextures.MULTIBLOCK);
        button.setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.FLEX)));
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.translatable("gui.neoecoae.multiblock.builder")),
                null,
                null,
                null
            , null));
        button.layout(layout -> layout.width(18).height(20));
        return button;
    }

    private static UIElement createPreview(Config config) {
        UIElement preview = new UIElement().style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        // The server constructs the same menu bindings without linking a client renderer.
        if (config.player().level().isClientSide()) {
            MultiblockPreviewScene.attach(preview, config.definition(), config.selectedLength(), config.mirrored());
        }
        return preview;
    }

    private static UIElement createControlPanel(Config config) {
        UIElement panel = new UIElement().layout(layout -> layout.paddingAll(2).gapAll(2));
        UIElement length = new UIElement().setId("multiblock-length-row")
            .layout(layout -> layout.height(14).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(1));
        length.addChildren(
            lengthButton("-", "gui.neoecoae.multiblock.decrease_length", config.decreaseLength()),
            syncedLabel(() -> Component.translatable("gui.neoecoae.multiblock.length",
                config.selectedLength().getAsInt()))
                .textStyle(MultiblockBuilderUI::centeredTextStyle)
                .layout(layout -> layout.width(32).height(14)),
            lengthButton("+", "gui.neoecoae.multiblock.increase_length", config.increaseLength())
        );
        UIElement mirror = new UIElement().setId("multiblock-mirror-row")
            .layout(layout -> layout.height(14).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(1));
        mirror.addChildren(
            new TextElement().setText(Component.translatable("gui.neoecoae.multiblock.mirror"))
                .textStyle(MultiblockBuilderUI::darkTextStyle)
                .layout(layout -> layout.width(20).height(14)),
            new ToggleSwitch(config.mirrored(), config.setMirrored(),
                "gui.neoecoae.multiblock.mirror.off.tooltip", "gui.neoecoae.multiblock.mirror.on.tooltip")
        );
        return panel.addChildren(length, mirror);
    }

    private static Button lengthButton(String text, String tooltip, Runnable action) {
        Button button = new Button().setText(text).setOnServerClick(event -> action.run());
        centerButtonText(button);
        button.layout(layout -> layout.width(12).height(14).paddingAll(0).flexShrink(0));
        return HostElements.tooltips(button, Component.translatable(tooltip));
    }

    private static UIElement createMaterialPanel(Config config) {
        ScrollerView materials = new ScrollerView().scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL)
            .horizontalScrollDisplay(ScrollDisplay.NEVER).verticalScrollDisplay(ScrollDisplay.NEVER));
        materials.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        materials.viewPort(view -> view.layout(layout -> layout.paddingAll(0))
            .style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        int[] count = {Math.min(MATERIAL_SLOT_COUNT, getRequiredItems(config).size())};
        int initialRows = Math.max(2, (count[0] + MATERIAL_COLUMNS - 1) / MATERIAL_COLUMNS);
        materials.viewContainer(view -> view.layout(layout -> layout.width(90).height(initialRows * 18).paddingAll(0))
            .style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        int[] offset = {0};
        materials.addChild(new SyncReceiver<>(count[0], value -> {
            count[0] = value == null ? 0 : Math.min(MATERIAL_SLOT_COUNT, value);
            int rows = Math.max(2, (count[0] + MATERIAL_COLUMNS - 1) / MATERIAL_COLUMNS);
            materials.viewContainer(view -> view.layout(layout -> layout.height(rows * 18)));
            offset[0] = 0;
            materials.verticalScroller(scroller -> scroller.setValue(0f));
        }).bind(DataBindingBuilder.intValS2C(() -> getRequiredItems(config).size()).build()));
        materials.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            int lastRow = Math.max(0, (count[0] + MATERIAL_COLUMNS - 1) / MATERIAL_COLUMNS - 2);
            if (event.deltaY == 0 || lastRow == 0) return;
            offset[0] = Math.clamp(offset[0] + (event.deltaY > 0 ? -1 : 1), 0, lastRow);
            materials.verticalScroller(scroller -> scroller.setValue(offset[0] / (float) lastRow));
            event.hasHandler = true;
            event.stopImmediatePropagation();
        }, true);
        HostElements.tooltips(materials,
            Component.translatable("gui.neoecoae.multiblock.preview.info.materials_scroll"));
        for (int i = 0; i < MATERIAL_SLOT_COUNT; i++) {
            int index = i;
            RequiredItemSlot slot = new RequiredItemSlot(() -> hasRequiredItem(config, index),
                () -> getRequiredItem(config, index).count());
            slot.setValue(getRequiredItemStack(config, index));
            slot.bind(DataBindingBuilder.itemStackS2C(() -> getRequiredItemStack(config, index)).build());
            materials.addScrollViewChild(HostElements.absolute(slot,
                i % MATERIAL_COLUMNS * 18, i / MATERIAL_COLUMNS * 18, 18, 18));
        }
        return materials;
    }

    private static UIElement createFooter(Config config) {
        UIElement footer = new UIElement().style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        Component[] tooltip = {buildResultTooltip(config)};
        footer.addChild(new SyncReceiver<>(tooltip[0], value ->
            tooltip[0] = value == null ? Component.empty() : value)
            .bind(DataBindingBuilder.componentS2C(() -> buildResultTooltip(config)).build()));
        Label status = syncedLabel(() -> buildStatusComponent(config));
        status.textStyle(MultiblockBuilderUI::darkTextStyle);
        status.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = tooltipLines(tooltip[0]));
        Button build = new Button().setText("gui.neoecoae.multiblock.build", true)
            .setOnServerClick(event -> config.build().run());
        build.layout(layout -> layout.paddingAll(0));
        centerButtonText(build);
        build.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = tooltipLines(tooltip[0]));
        footer.addChildren(HostElements.absolute(status, 11, 1, 94, 16),
            HostElements.absolute(build, 111, 0, 58, 18));
        return footer;
    }

    private static Component buildResultTooltip(Config config) {
        return buildStatusComponent(config).copy()
            .append("\n").append(Component.translatable("gui.neoecoae.multiblock.missing", getMissingBlockCount(config)))
            .append("\n").append(Component.translatable("gui.neoecoae.multiblock.reused", getReusedBlockCount(config)))
            .append("\n").append(Component.translatable("gui.neoecoae.multiblock.required_items", getRequiredItemCount(config)))
            .append("\n").append(Component.translatable("gui.neoecoae.multiblock.conflicts", getConflictBlockCount(config)))
            .append("\n").append(buildConflictTooltipComponent(getConflictPositions(config)));
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

    private static Label syncedLabel(Supplier<Component> text) {
        Label label = new Label();
        label.setText(text.get());
        label.bind(DataBindingBuilder.componentS2C(text).build());
        return label;
    }

    private static void darkTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(false).adaptiveWidth(false).fontSize(7)
            .textWrap(TextWrap.HOVER_ROLL).textAlignVertical(Vertical.CENTER)
            .textColor(NEGuiColors.textColor(0x3f3d52)).textShadow(false);
    }

    private static void centeredTextStyle(TextElement.TextStyle style) {
        darkTextStyle(style);
        style.textAlignHorizontal(Horizontal.CENTER);
    }

    /** Button text is a non-adaptive child of a row; stretch it so CENTER aligns against the full button. */
    private static void centerButtonText(Button button) {
        button.textStyle(MultiblockBuilderUI::centeredTextStyle);
        button.text.layout(layout -> layout.flexGrow(1).marginHorizontal(0));
    }

    /** The synced tooltip uses '\n' separators; a single tooltip line would render them as glyphs. */
    private static HoverTooltips tooltipLines(Component tooltip) {
        List<Object> lines = new ArrayList<>();
        for (String line : tooltip.getString().split("\n")) {
            lines.add(Component.literal(line));
        }
        return new HoverTooltips(lines, null, null, null, null);
    }

    /** AE2 terminal-settings style on/off switch (guis/checkbox.png, 22x12). */
    private static final class ToggleSwitch extends Button implements IBindable<Boolean> {
        private boolean syncedSelected;

        private ToggleSwitch(
            BooleanSupplier selected,
            Consumer<Boolean> setSelected,
            String offTooltipKey,
            String onTooltipKey
        ) {
            this.syncedSelected = selected.getAsBoolean();
            noText();
            layout(layout -> layout.width(22).height(12).paddingAll(0).flexShrink(0));
            setOnServerClick(event -> setSelected.accept(!selected.getAsBoolean()));
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.translatable(syncedSelected ? onTooltipKey : offTooltipKey)),
                null, null, null, null));
            bind(DataBindingBuilder.boolS2C(selected::getAsBoolean).build());
            refreshTextures();
        }

        private void refreshTextures() {
            buttonStyle(style -> style
                .baseTexture(syncedSelected ? NETextures.SWITCH_ON : NETextures.SWITCH_OFF)
                .hoverTexture(syncedSelected ? NETextures.SWITCH_ON_HOVER : NETextures.SWITCH_OFF_HOVER)
                .pressedTexture(syncedSelected ? NETextures.SWITCH_ON_HOVER : NETextures.SWITCH_OFF_HOVER));
        }

        @Override
        public Boolean getValue() {
            return syncedSelected;
        }

        @Override
        public IDataSource<Boolean> setValue(@Nullable Boolean value) {
            syncedSelected = Boolean.TRUE.equals(value);
            refreshTextures();
            return this;
        }
    }

    private static final class RequiredItemSlot extends ItemSlot {
        private boolean syncedHasRequiredItem;
        private int syncedCount;

        private RequiredItemSlot(BooleanSupplier hasRequiredItem, IntSupplier count) {
            syncedHasRequiredItem = hasRequiredItem.getAsBoolean();
            syncedCount = count.getAsInt();
            getStyle().backgroundTexture(IGuiTexture.EMPTY);
            layout(layout -> layout.paddingAll(1).flexShrink(0));
            slotStyle(style -> style.slotOverlay(IGuiTexture.EMPTY));
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
                , null);
            });
        }

        @Override
        protected void drawItemStack(IGUIContext ctx, ItemStack itemStack) {
            if (itemStack.isEmpty() || !(ctx instanceof GUIContext guiContext)) {
                return;
            }
            DrawerHelperClient.drawItemStack(guiContext, itemStack.copyWithCount(1), 0, 0, -1);
            int color = syncedHasRequiredItem ? MATERIAL_COUNT_ENOUGH_COLOR : MATERIAL_COUNT_MISSING_COLOR;
            // 26.1 GUI render state is layered by submission order; no z translate needed.
            DrawerHelperClient.drawStringFixedCorner(guiContext, String.valueOf(syncedCount), 17, 17, color, true, 0.8f);
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
