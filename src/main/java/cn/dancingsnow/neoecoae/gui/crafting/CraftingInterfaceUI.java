package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.GuiTitleProvider;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Compact pattern-manager view; all operations still address the original physical bus slots. */
public final class CraftingInterfaceUI {
    public static final int PREVIEW_COLUMNS = 9;
    public static final int PREVIEW_ROWS = 6;
    static final int PREVIEW_BUFFER_SLOTS = (PREVIEW_ROWS + 1) * PREVIEW_COLUMNS;
    private static final int PREVIEW_WIDTH = PREVIEW_COLUMNS * 18;
    private static final int PREVIEW_HEIGHT = PREVIEW_ROWS * 18;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_BORDER = 1;
    private static final int SCROLLBAR_THUMB_HEIGHT = 15;
    private static final int SCROLLBAR_TRAVEL = PREVIEW_HEIGHT - SCROLLBAR_BORDER * 2 - SCROLLBAR_THUMB_HEIGHT;
    private static final ResourceLocation SCROLLBAR_ENABLED = new ResourceLocation("ae2", "big_scroller");
    private static final ResourceLocation SCROLLBAR_DISABLED = new ResourceLocation("ae2", "big_scroller_disabled");
    private static final ResourceLocation JSON_LAYOUT =
        new ResourceLocation("neoecoae", "screens/crafting/crafting_interface");
    static final int PREVIEW_QUERY_MAX_LENGTH = 128;

    private CraftingInterfaceUI() { }

    public static ModularUI create(ECOMachineInterfaceBlockEntity<?> craftingInterface, Player player) {
        CraftingPatternPreviewState previewState = new CraftingPatternPreviewState(craftingInterface, player);
        StatusTooltips operationStatus = new StatusTooltips(craftingInterface);
        UIElement root = NeoUiJsonLoader.load(JSON_LAYOUT,
            new JsonContext(craftingInterface, player, previewState, operationStatus));
        root.addEventListener(UIEvents.MOUSE_UP, event -> {
            if (event.button == 0) previewState.endQuickMoveDrag();
        }, true);
        root.addEventListener(UIEvents.TICK, event -> {
            previewState.tick();
            previewState.endQuickMoveDragIfReleased(root.isMouseDown(0));
        });
        return ECOModularUI.create(root, player);
    }

    /** Resource context keeps preview/search state and server actions out of the JSON document. */
    private static final class JsonContext implements NeoUiJsonContext {
        private final ECOMachineInterfaceBlockEntity<?> host;
        private final Player player;
        private final CraftingPatternPreviewState previewState;
        private final StatusTooltips operationStatus;

        private JsonContext(ECOMachineInterfaceBlockEntity<?> host, Player player,
                            CraftingPatternPreviewState previewState, StatusTooltips operationStatus) {
            this.host = host;
            this.player = player;
            this.previewState = previewState;
            this.operationStatus = operationStatus;
        }

        @Override
        public UIElement createCustom(JsonObject node) {
            return switch (string(node, "custom", "")) {
                case "operation_status" -> operationStatus;
                case "title" -> boundLabel(() -> GuiTitleProvider.title(host.getBlockState(),
                    Component.translatable("gui.neoecoae.crafting_interface.title")));
                case "operation_indicator" -> operationIndicator();
                case "input_search" -> patternSearchField(previewState::setInputSearch, "inputs", false)
                    .setSearchValue(previewState.getInputSearch());
                case "output_search" -> patternSearchField(previewState::setOutputSearch, "output", false)
                    .setSearchValue(previewState.getOutputSearch());
                case "pattern_preview" -> patternPreview(previewState);
                case "pattern_scrollbar" -> patternPreviewScrollbar(previewState);
                case "side_controls" -> sideControls(host, player, operationStatus);
                case "status_label" -> statusLabel();
                case "transfer_progress" -> transferProgress();
                case "player_inventory" -> playerInventory(previewState, false);
                default -> throw new IllegalArgumentException("Unknown crafting interface custom element: "
                    + string(node, "custom", ""));
            };
        }

        @Override
        public void configure(JsonObject node, UIElement element) {
            if ("crafting_interface_root".equals(string(node, "id", ""))) {
                element.setOverflowVisible(true);
            }
        }

        private UIElement operationIndicator() {
            PatternOperationIndicator indicator = new PatternOperationIndicator(host);
            operationStatus.attach(indicator);
            return indicator;
        }

        private Label statusLabel() {
            Label status = compactLabel();
            Component[] previous = {previewState.status()};
            status.setText(previous[0]);
            status.addEventListener(UIEvents.TICK, event -> {
                Component next = previewState.status();
                if (!next.equals(previous[0])) status.setText(previous[0] = next);
            });
            operationStatus.attach(status);
            return status;
        }

        private TransferProgress transferProgress() {
            TransferProgress progress = new TransferProgress();
            progress.bind(DataBindingBuilder.floatValS2C(() -> host.isPatternTransferInProgress()
                ? Math.round(host.getPatternTransferProgress() * 100F) / 100F : 0F).build());
            operationStatus.attach(progress);
            return progress;
        }

        private static String string(JsonObject node, String key, String fallback) {
            return node.has(key) && node.get(key).isJsonPrimitive() ? node.get(key).getAsString() : fallback;
        }
    }

    private static UIElement sideControls(ECOMachineInterfaceBlockEntity<?> craftingInterface,
            Player player, StatusTooltips status) {
        Button transfer = iconButton(ECOIcon.ARROW_RIGHT, "gui.neoecoae.host.crafting.pattern_transfer",
                craftingInterface::startNetworkPatternTransfer);
        Button organize = iconButton(ECOIcon.COG, "gui.neoecoae.crafting_interface.preview.organize", () -> {
            if (player instanceof ServerPlayer serverPlayer) craftingInterface.organizePatternBuses(serverPlayer);
        });
        status.attach(transfer);
        status.attach(organize);
        return HostSideButtonBar.grayLeft(transfer, organize);
    }

    private static SearchField patternSearchField(Consumer<String> responder, String direction, boolean withLayout) {
        SearchField field = ClientUIBridge.call("createSearchField", Consumer.class, responder, SearchField.class,
                () -> new SearchField(responder));
        String key = "gui.neoecoae.crafting_interface.preview.search." + direction;
        field.setSearchHint(Component.translatable(key));
        HostElements.tooltips(field, Component.translatable(key + ".tooltip"));
        if (withLayout) return HostElements.absolute(field, "inputs".equals(direction) ? 21 : 103, 18, 66, 12);
        return field;
    }

    /** Client bridge supplies the native EditBox; the shared tree also exists on dedicated servers. */
    public static class SearchField extends UIElement {
        private final Consumer<String> responder;

        public SearchField(Consumer<String> responder) {
            this.responder = responder == null ? value -> { } : responder;
            setFocusable(true);
        }

        public SearchField setSearchHint(Component hint) { return this; }
        public SearchField setSearchValue(String value) { return this; }
        protected final void updateSearch(String value) { responder.accept(value); }
    }

    /** Identifies the insertion surface when the client draws tooltips while carrying an item. */
    public static final class PatternPreview extends UIElement {
        private final CraftingPatternPreviewState state;
        private long lastFrame;

        PatternPreview(CraftingPatternPreviewState state) { this.state = state; }

        public void advanceFrame() {
            long now = System.nanoTime();
            if (lastFrame != 0) state.advanceScroll((now - lastFrame) / 1_000_000_000.0);
            lastFrame = now;
        }

        @Override
        public void drawContents(GUIContext context) {
            // Preserve the texture's fixed outer border, then let UIElement draw and clip the children normally.
            context.graphics.flush();
            context.enableScissor(getPositionX() + 1, getPositionY() + 1,
                getSizeWidth() - 2, getSizeHeight() - 2);
            try {
                super.drawContents(context);
            } finally {
                context.graphics.flush();
                context.disableScissor();
            }
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            float x = getPositionX();
            float y = getPositionY() - (float) state.getRowOffset();
            for (int row = 0; row <= PREVIEW_ROWS; row++) {
                context.drawTexture(NETextures.PATTERN_MANAGER_ROW, x, y + row * 18, PREVIEW_WIDTH, 18);
            }
        }
    }

    static UIElement patternPreview(CraftingPatternPreviewState state) {
        UIElement preview = new PatternPreview(state).setOverflowVisible(false);
        preview.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            if (state.hasCarriedPattern()) {
                event.hoverTooltips = HoverTooltips.empty().append(Component.translatable(
                        "gui.neoecoae.crafting_interface.preview.put_pattern"));
            }
        });
        preview.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            state.stopScrolling();
            if ((event.button == 0 || event.button == 1) && state.hasCarriedPattern()
                    && (event.button == 0 || event.target == preview)) {
                // Capture left clicks before an occupied slot can swap or quick-move its pattern.
                // A full bus must leave the carried pattern alone instead of falling back to replacement.
                state.insertCarriedPattern(event.button);
                event.hasHandler = true;
                event.stopImmediatePropagation();
            }
        }, true);
        preview.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0 && !event.isShiftDown()) {
                state.scroll(-event.deltaY);
                event.stopImmediatePropagation();
            }
        });
        for (int visual = 0; visual < PREVIEW_BUFFER_SLOTS; visual++) {
            PatternItemSlot slot = state.createSlot(visual);
            slot.getStyle().backgroundTexture(IGuiTexture.EMPTY);
            // The continuous row behind the children supplies every cell, including unoccupied cells.
            slot.slotStyle(style -> style.slotOverlay(IGuiTexture.EMPTY));
            preview.addChild(HostElements.absolute(slot, visual % PREVIEW_COLUMNS * 18,
                    visual / PREVIEW_COLUMNS * 18, 18, 18));
        }
        return preview;
    }

    static UIElement patternPreviewScrollbar(CraftingPatternPreviewState state) {
        UIElement scrollbar = new UIElement() {
            @Override public void drawBackgroundAdditional(GUIContext context) {
                double max = state.getMaxScrollOffset();
                float progress = max <= 0 ? 0F : (float) JdkCompat.clamp(state.getScrollOffset() / max, 0, 1);
                int x = Math.round(getPositionX()) + SCROLLBAR_BORDER;
                int y = Math.round(getPositionY()) + SCROLLBAR_BORDER;
                // Flush LDLib's track texture before drawing AE2's native sprite on top of it.
                context.graphics.flush();
                context.pose.pushPose();
                try {
                    context.pose.translate(0, SCROLLBAR_TRAVEL * progress, 0);
                    context.graphics.blitSprite(max > 0 ? SCROLLBAR_ENABLED : SCROLLBAR_DISABLED,
                            x, y, SCROLLBAR_WIDTH, SCROLLBAR_THUMB_HEIGHT);
                } finally {
                    context.pose.popPose();
                }
            }
        };
        scrollbar.style(style -> style.backgroundTexture(NETextures.PATTERN_MANAGER_SCROLLBAR_TRACK));
        scrollbar.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            double max = state.getMaxScrollOffset();
            if (event.button != 0 || max <= 0) return;
            float localY = getLocalY(scrollbar, event.x, event.y) - SCROLLBAR_BORDER;
            double thumbY = state.getScrollOffset() / max * SCROLLBAR_TRAVEL;
            // A press on the handle retains its grab point; a track press centers it under the pointer.
            if (localY < thumbY || localY >= thumbY + SCROLLBAR_THUMB_HEIGHT) {
                state.setScrollOffset((localY - SCROLLBAR_THUMB_HEIGHT / 2.0) / SCROLLBAR_TRAVEL * max);
            } else state.stopScrolling();
            scrollbar.getModularUI().getDragHandler().startDrag((float) state.getScrollOffset(), null, scrollbar);
            event.stopImmediatePropagation();
        });
        scrollbar.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
            if (event.dragHandler == null || !(event.dragHandler.draggingObject instanceof Float initial)) return;
            float delta = getLocalY(scrollbar, event.x, event.y) - getLocalY(scrollbar, event.dragStartX, event.dragStartY);
            state.setScrollOffset(initial + delta / SCROLLBAR_TRAVEL * state.getMaxScrollOffset());
            event.stopImmediatePropagation();
        });
        scrollbar.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY == 0) return;
            state.scroll(-event.deltaY);
            event.stopImmediatePropagation();
        });
        return scrollbar;
    }

    private static float getLocalY(UIElement element, float x, float y) {
        return element.getLocalMouse(x, y).y - element.getPositionY();
    }

    private static UIElement playerInventory(CraftingPatternPreviewState state, boolean withLayout) {
        InventorySlots inventory = new InventorySlots();
        inventory.getChildren().forEach(child -> child.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        inventory.hotbar.layout(layout -> layout.marginTop(4));
        inventory.apply(slot -> {
            slot.getStyle().backgroundTexture(IGuiTexture.EMPTY);
            slot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.isShiftDown() && (event.button == 0 || event.button == 1) && state.quickMoveFromInventory(slot.getSlot())) {
                    event.hasHandler = true;
                    event.stopImmediatePropagation();
                }
            });
        });
        return withLayout ? HostElements.absolute(inventory, 7, 152, PREVIEW_WIDTH, 76) : inventory;
    }

    private static Button iconButton(ECOIcon icon, String tooltip, Runnable action) {
        return HostElements.tooltips(HostSideButtonBar.createButton().noText().addPreIcon(AETextures.icon(icon))
                .setOnServerClick(event -> action.run()), Component.translatable(tooltip));
    }

    private static Label compactLabel() {
        Label label = new Label();
        label.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).textShadow(false)
                .adaptiveWidth(false).adaptiveHeight(false));
        return label;
    }

    private static Label boundLabel(Supplier<Component> text) {
        Label label = compactLabel();
        label.setText(text.get());
        label.bind(DataBindingBuilder.componentS2C(text).build());
        return label;
    }

    static List<Component> statusTooltipLines(Component network, Component primary, Component secondary) {
        return List.of(network, primary, secondary).stream().filter(line -> !line.getString().isEmpty()).toList();
    }

    private static final class StatusTooltips extends UIElement {
        private final TooltipLine network;
        private final TooltipLine primary;
        private final TooltipLine secondary;

        private StatusTooltips(ECOMachineInterfaceBlockEntity<?> host) {
            network = new TooltipLine(() -> Component.translatable("gui.neoecoae.storage_interface.network").append(": ")
                    .append(Component.translatable(host.isTargetOnline()
                            ? "gui.neoecoae.storage_interface.connected" : "gui.neoecoae.storage_interface.disconnected")));
            primary = new TooltipLine(host::getPatternTransferPrimaryStatus);
            secondary = new TooltipLine(() -> host.hasPatternTransferSecondaryStatus()
                    ? host.getPatternTransferSecondaryStatus() : Component.empty());
            addChild(network);
            addChild(primary);
            addChild(secondary);
        }

        private void attach(UIElement element) {
            element.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                if (event.hoverTooltips == null) event.hoverTooltips = HoverTooltips.empty();
                // The tooltip renderer treats every Component as one line; embedded newlines are not split.
                event.hoverTooltips = event.hoverTooltips.append(statusTooltipLines(
                        network.getValue(), primary.getValue(), secondary.getValue()).toArray(Component[]::new));
            });
        }
    }

    private static final class TooltipLine extends UIElement implements IBindable<Component> {
        private Component text;

        private TooltipLine(Supplier<Component> supplier) {
            text = supplier.get();
            bind(DataBindingBuilder.componentS2C(supplier).build());
            setDisplay(false);
        }

        @Override public Component getValue() { return text; }
        @Override public IDataSource<Component> setValue(Component value) {
            text = value == null ? Component.empty() : value;
            return this;
        }
    }

    static final class PatternOperationIndicator extends UIElement implements IBindable<Integer> {
        private static final IGuiTexture[] NORMAL = {
            NETextures.Crafting.widget(8, 129, 10, 12),
            NETextures.Crafting.widget(18, 129, 10, 12),
            NETextures.Crafting.widget(28, 129, 10, 12)
        };
        private static final IGuiTexture[] HOVERED = {
            NETextures.Crafting.widget(8, 142, 10, 11),
            NETextures.Crafting.widget(18, 142, 10, 11),
            NETextures.Crafting.widget(28, 142, 10, 11)
        };
        private int state;

        PatternOperationIndicator(ECOMachineInterfaceBlockEntity<?> host) {
            Supplier<Integer> source = () -> host.isPatternOrganizeInProgress()
                ? 2 : host.isPatternTransferInProgress() ? 1 : 0;
            state = source.get();
            bind(DataBindingBuilder.intValS2C(source).build());
        }

        @Override public Integer getValue() { return state; }

        @Override public IDataSource<Integer> setValue(Integer value) {
            state = value == null ? 0 : JdkCompat.clamp(value, 0, 2);
            return this;
        }

        @Override public void drawBackgroundAdditional(GUIContext context) {
            boolean hovered = isHover();
            // The lower hover sprite is one pixel shorter; keep the hitbox fixed and shift the image down.
            context.drawTexture((hovered ? HOVERED : NORMAL)[state],
                getPositionX(), getPositionY() + (hovered ? 1 : 0), 10, hovered ? 11 : 12);
        }
    }

    private static final class TransferProgress extends UIElement implements IBindable<Float> {
        private float progress;
        @Override public Float getValue() { return progress; }
        @Override public IDataSource<Float> setValue(Float value) {
            progress = value == null ? 0F : JdkCompat.clamp(value, 0F, 1F);
            return this;
        }
        @Override public void drawContents(GUIContext context) {
            if (progress <= 0F) return;
            int x = Math.round(getPositionX()), y = Math.round(getPositionY());
            context.graphics.fill(x, y, x + Math.round(getSizeWidth() * progress), y + 2, 0xFF32D6FF);
        }
    }
}
