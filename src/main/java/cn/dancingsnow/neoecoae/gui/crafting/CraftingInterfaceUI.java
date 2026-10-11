package cn.dancingsnow.neoecoae.gui.crafting;

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
    private static final int PREVIEW_WIDTH = PREVIEW_COLUMNS * 18;
    private static final int PREVIEW_HEIGHT = PREVIEW_ROWS * 18;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_BORDER = 1;
    private static final int SCROLLBAR_THUMB_HEIGHT = 15;
    private static final int SCROLLBAR_TRAVEL = PREVIEW_HEIGHT - SCROLLBAR_BORDER * 2 - SCROLLBAR_THUMB_HEIGHT;
    private static final ResourceLocation SCROLLBAR_ENABLED = ResourceLocation.fromNamespaceAndPath("ae2", "big_scroller");
    private static final ResourceLocation SCROLLBAR_DISABLED = ResourceLocation.fromNamespaceAndPath("ae2", "big_scroller_disabled");
    private static final ResourceLocation JSON_LAYOUT =
        ResourceLocation.fromNamespaceAndPath("neoecoae", "screens/crafting/crafting_interface");
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
        root.addEventListener(UIEvents.TICK,
                event -> previewState.endQuickMoveDragIfReleased(root.isMouseDown(0)));
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
                case "network_indicator" -> networkIndicator();
                case "input_search" -> patternSearchField(previewState::setInputSearch, "inputs", false);
                case "output_search" -> patternSearchField(previewState::setOutputSearch, "output", false);
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

        private Label networkIndicator() {
            Label network = boundLabel(() -> Component.literal("●").withColor(
                host.isTargetOnline() ? 0x55CC77 : 0xDD5555));
            operationStatus.attach(network);
            return network;
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
        if (withLayout) return HostElements.absolute(field, "inputs".equals(direction) ? 21 : 103, 15, 66, 12);
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
        protected final void updateSearch(String value) { responder.accept(value); }
    }

    /** Identifies the insertion surface when the client draws tooltips while carrying an item. */
    public static final class PatternPreview extends UIElement { }

    static UIElement patternPreview(CraftingPatternPreviewState state) {
        // UIElement owns child rendering and clipping. Overriding drawContents here hides the pattern items.
        UIElement preview = new PatternPreview().setOverflowVisible(false);
        preview.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            if (state.hasCarriedPattern()) {
                event.hoverTooltips = HoverTooltips.empty().append(Component.translatable(
                        "gui.neoecoae.crafting_interface.preview.put_pattern"));
            }
        });
        preview.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.target == preview && state.insertCarriedPattern(event.button)) {
                event.hasHandler = true;
                event.stopImmediatePropagation();
            }
        });
        preview.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0 && !event.isShiftDown()) {
                state.scroll(event.deltaY < 0 ? 1 : -1);
                event.stopImmediatePropagation();
            }
        });
        for (int visual = 0; visual < ECOMachineInterfaceBlockEntity.PATTERN_INTERFACE_VISIBLE_SLOTS; visual++) {
            PatternItemSlot slot = state.createSlot(visual);
            slot.getStyle().backgroundTexture(NETextures.PATTERN_MANAGER_SLOT);
            slot.slotStyle(style -> style.slotOverlay(IGuiTexture.EMPTY));
            preview.addChild(HostElements.absolute(slot, visual % PREVIEW_COLUMNS * 18,
                    visual / PREVIEW_COLUMNS * 18, 18, 18));
        }
        return preview;
    }

    static UIElement patternPreviewScrollbar(CraftingPatternPreviewState state) {
        UIElement scrollbar = new UIElement() {
            @Override public void drawBackgroundAdditional(GUIContext context) {
                int max = state.getMaxScrollRow();
                float progress = max <= 0 ? 0F : Math.clamp(state.getScrollRow() / (float) max, 0F, 1F);
                int thumbY = Math.round(SCROLLBAR_TRAVEL * progress);
                int x = Math.round(getPositionX()) + SCROLLBAR_BORDER;
                int y = Math.round(getPositionY()) + SCROLLBAR_BORDER + thumbY;
                // Flush LDLib's track texture before drawing AE2's native sprite on top of it.
                context.graphics.flush();
                context.graphics.blitSprite(max > 0 ? SCROLLBAR_ENABLED : SCROLLBAR_DISABLED,
                        x, y, SCROLLBAR_WIDTH, SCROLLBAR_THUMB_HEIGHT);
            }
        };
        scrollbar.style(style -> style.backgroundTexture(NETextures.PATTERN_MANAGER_SCROLLBAR_TRACK));
        scrollbar.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            int max = state.getMaxScrollRow();
            if (event.button != 0 || max <= 0) return;
            float progress = Math.clamp((getLocalY(scrollbar, event.x, event.y)
                    - SCROLLBAR_BORDER - SCROLLBAR_THUMB_HEIGHT / 2F) / SCROLLBAR_TRAVEL, 0F, 1F);
            state.setScrollRow(Math.round(progress * max));
            scrollbar.getModularUI().getDragHandler().startDrag((float) state.getScrollRow(), null, scrollbar);
            event.stopImmediatePropagation();
        });
        scrollbar.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
            if (event.dragHandler == null || !(event.dragHandler.draggingObject instanceof Float initial)) return;
            float delta = getLocalY(scrollbar, event.x, event.y) - getLocalY(scrollbar, event.dragStartX, event.dragStartY);
            state.setScrollRow(Math.round(initial + delta / SCROLLBAR_TRAVEL * state.getMaxScrollRow()));
            event.stopImmediatePropagation();
        });
        scrollbar.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY == 0) return;
            state.scroll(event.deltaY < 0 ? 1 : -1);
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

    private static final class TransferProgress extends UIElement implements IBindable<Float> {
        private float progress;
        @Override public Float getValue() { return progress; }
        @Override public IDataSource<Float> setValue(Float value) {
            progress = value == null ? 0F : Math.clamp(value, 0F, 1F);
            return this;
        }
        @Override public void drawContents(GUIContext context) {
            if (progress <= 0F) return;
            int x = Math.round(getPositionX()), y = Math.round(getPositionY());
            context.graphics.fill(x, y, x + Math.round(getSizeWidth() * progress), y + 2, 0xFF32D6FF);
        }
    }
}
