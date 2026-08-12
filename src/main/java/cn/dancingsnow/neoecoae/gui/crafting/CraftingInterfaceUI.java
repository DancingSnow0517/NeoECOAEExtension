package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Control surface for browsing network patterns and moving compatible ones into ECO crafting buses. */
public final class CraftingInterfaceUI {
    private static final int STATUS_CONNECTED = 0x55CC77;
    private static final int STATUS_DISCONNECTED = 0xDD5555;
    public static final int PREVIEW_COLUMNS = 9;
    public static final int PREVIEW_ROWS = 4;
    private static final int PREVIEW_WIDTH = PREVIEW_COLUMNS * 18;
    private static final int PREVIEW_HEIGHT = PREVIEW_ROWS * 18;
    private static final int PLAYER_INVENTORY_WIDTH = 9 * 18;
    private static final int ROOT_SIDE_MARGIN = 7;
    private static final int TOOL_BUTTON_SIZE = 16;
    private static final int PREVIEW_SCROLLBAR_WIDTH = 12;
    private static final int PREVIEW_SCROLLBAR_GAP = 2;
    private static final int PREVIEW_SCROLLBAR_TRACK_WIDTH = 6;
    private static final int PREVIEW_SCROLLBAR_THUMB_HEIGHT = 15;
    private static final int PREVIEW_QUERY_MAX_LENGTH = 128;

    private CraftingInterfaceUI() {
    }

    public static ModularUI create(
            ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface,
            Player player) {
        IItemHandlerModifiable patternHandler =
                craftingInterface.createPatternInterfaceItemHandler(player.getUUID());
        PreviewState previewState = new PreviewState(craftingInterface, patternHandler);

        UIElement root = new UIElement().layout(layout -> layout
                .width(PLAYER_INVENTORY_WIDTH + PREVIEW_SCROLLBAR_WIDTH + PREVIEW_SCROLLBAR_GAP + ROOT_SIDE_MARGIN * 2)
                .paddingLeft(ROOT_SIDE_MARGIN)
                .paddingRight(ROOT_SIDE_MARGIN)
                .paddingTop(8)
                .paddingBottom(8)
                .gapAll(5)
                .flexDirection(FlexDirection.COLUMN))
                .addClass("panel_bg");
        root.addChild(boundLabel(() -> Component.translatable("gui.neoecoae.crafting_interface.title")));

        UIElement contentFrame = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .paddingLeft(8)
                .paddingRight(8)
                .paddingTop(8)
                .paddingBottom(7)
                .gapAll(4)
                .flexDirection(FlexDirection.COLUMN))
                .style(style -> style.backgroundTexture(Sprites.BORDER_THICK_RT1));
        contentFrame.addChild(statusLabel(() -> Component.translatable("gui.neoecoae.storage_interface.network")
                .append(": ")
                .append(Component.translatable(craftingInterface.isTargetOnline()
                        ? "gui.neoecoae.storage_interface.connected"
                        : "gui.neoecoae.storage_interface.disconnected")
                        .withColor(craftingInterface.isTargetOnline() ? STATUS_CONNECTED : STATUS_DISCONNECTED))));
        contentFrame.addChild(transferButton(craftingInterface));
        contentFrame.addChild(statusLabel(craftingInterface::getPatternTransferPrimaryStatus));
        contentFrame.addChild(patternTransferProgress(craftingInterface));
        root.addChild(contentFrame);
        root.addChild(previewSection(craftingInterface, previewState));
        InventorySlots playerInventory = new InventorySlots();
        playerInventory.layout(layout -> layout
                .width(PLAYER_INVENTORY_WIDTH)
                .marginTop(2));
        root.addChild(playerInventory);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), player);
    }

    private static Button transferButton(ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface) {
        Button button = new Button()
                .setText(Component.translatable("gui.neoecoae.host.crafting.pattern_transfer"))
                .setOnServerClick(event -> craftingInterface.startNetworkPatternTransfer());
        button.buttonStyle(style -> style
                .baseTexture(Sprites.RECT_RD)
                .hoverTexture(Sprites.RECT_RD_LIGHT)
                .pressedTexture(Sprites.RECT_RD_DARK));
        button.layout(layout -> layout.widthPercent(100).height(18));
        return button;
    }

    private static UIElement patternTransferProgress(
            ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface) {
        UIElement container = HostElements.syncedDisplay(craftingInterface::isPatternTransferInProgress);
        ProgressBar progressBar = new ProgressBar();
        progressBar.label(label -> label.setText(""));
        progressBar.barContainer(element -> element.layout(layout -> layout.paddingAll(1)));
        progressBar.bind(DataBindingBuilder.floatValS2C(craftingInterface::getPatternTransferProgress).build());
        progressBar.addClass("eco-pattern-transfer-progress");
        progressBar.layout(layout -> layout.widthPercent(100).height(7));
        container.addChild(progressBar);
        container.layout(layout -> layout.widthPercent(100).height(7).marginTop(-1));
        return container;
    }

    private static UIElement previewHeader(
            ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface,
            PreviewState previewState) {
        UIElement header = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .height(TOOL_BUTTON_SIZE)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .gapAll(2));
        header.addChild(patternSearchField(previewState));
        header.addChild(patternFilterButton(
                previewState,
                PreviewState::showsSubstitutionPatterns,
                Icon.S_SUBSTITUTION_ENABLED,
                Icon.S_SUBSTITUTION_DISABLED,
                PreviewState::toggleSubstitutionPatterns,
                "gui.neoecoae.crafting_interface.preview.filter_substitutions"));
        header.addChild(patternFilterButton(
                previewState,
                PreviewState::showsFluidSubstitutionPatterns,
                Icon.S_FLUID_SUBSTITUTION_ENABLED,
                Icon.S_FLUID_SUBSTITUTION_DISABLED,
                PreviewState::toggleFluidSubstitutionPatterns,
                "gui.neoecoae.crafting_interface.preview.filter_fluid_substitutions"));
        header.addChild(iconButton(Icon.PATTERN_ACCESS_SHOW, "gui.neoecoae.crafting_interface.preview.organize",
                craftingInterface::organizePatternBuses));
        return header;
    }

    private static NativeSearchField patternSearchField(PreviewState previewState) {
        NativeSearchField field = new NativeSearchField(previewState::setSearch);
        field.layout(layout -> layout.flex(1).height(TOOL_BUTTON_SIZE));
        // ExtendedAE clears its matrix query with a right-click. Keep that compact interaction so the search field
        // retains enough width for long pattern names.
        field.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 1) {
                field.clearText();
                event.stopImmediatePropagation();
            }
        });
        return HostElements.tooltips(field,
                Component.translatable("gui.neoecoae.crafting_interface.preview.search.tooltip"));
    }

    /**
     * Bridges a vanilla EditBox into ModularUI. LowDragLib's TextField has its own character editor, but it does not
     * own a native Minecraft text widget, which means Windows IME focus is not established for this field. The
     * vanilla EditBox handles charTyped/keyPressed, selection, clipboard, and the IME commit path used by AE2.
     */
    public static void attachNativeSearchFields(ScreenEvent.Init.Post event, ModularUI modularUI) {
        if (modularUI == null) {
            return;
        }

        var widget = modularUI.getWidget();
        for (NativeSearchField field : modularUI.getElementsByType(NativeSearchField.class)) {
            if (field.nativeWidgetRegistered) {
                continue;
            }

            // Put the native field first in the Screen hit-test order. The broad ModularUI widget remains last so it
            // continues to render the whole panel and dispatches the non-text controls.
            event.removeListener(widget);
            event.addListener(field.editBox);
            event.addListener(widget);
            field.nativeWidgetRegistered = true;
        }
    }

    public static final class NativeSearchField extends UIElement {
        private final PositionedEditBox editBox;
        private boolean nativeWidgetRegistered;

        private NativeSearchField(java.util.function.Consumer<String> responder) {
            editBox = new PositionedEditBox();
            editBox.setBordered(false);
            editBox.setTextColor(0xFFFFFF);
            editBox.setTextShadow(false);
            editBox.setMaxLength(PREVIEW_QUERY_MAX_LENGTH);
            editBox.setFilter(value -> true);
            editBox.setHint(Component.translatable("gui.neoecoae.crafting_interface.preview.search"));
            editBox.setResponder(responder);
            editBox.setVisible(true);
            editBox.active = true;

            setFocusable(true);
            style(style -> style.backgroundTexture(Sprites.RECT_RD));
        }

        private void clearText() {
            editBox.setValue("");
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            super.drawContents(guiContext);
            syncNativeBounds();
            editBox.renderNative(guiContext.graphics, guiContext.mouseX, guiContext.mouseY, guiContext.partialTick);
        }

        private void syncNativeBounds() {
            editBox.setX(Math.round(getPositionX()));
            editBox.setY(Math.round(getPositionY()));
            editBox.setWidth(Math.max(1, Math.round(getSizeWidth())));
            editBox.setHeight(Math.max(1, Math.round(getSizeHeight())));
        }

        /** Keep the Screen's hit test in the same coordinate system as ModularUI, even before the next render. */
        private final class PositionedEditBox extends EditBox {
            private PositionedEditBox() {
                super(Minecraft.getInstance().font, 0, 0, 1, TOOL_BUTTON_SIZE,
                        Component.translatable("gui.neoecoae.crafting_interface.preview.search"));
            }

            @Override
            public void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
                                     float partialTick) {
                // Screen owns this widget for the native keyboard/IME focus path. Its visual pass is rendered by
                // NativeSearchField so the ModularUI panel remains the final layer on screen.
            }

            private void renderNative(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
                                      float partialTick) {
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public boolean isMouseOver(double mouseX, double mouseY) {
                syncNativeBounds();
                return super.isMouseOver(mouseX, mouseY);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                syncNativeBounds();
                return super.mouseClicked(mouseX, mouseY, button);
            }

            @Override
            public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
                syncNativeBounds();
                return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }

            @Override
            public boolean mouseReleased(double mouseX, double mouseY, int button) {
                syncNativeBounds();
                return super.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    private static Button patternFilterButton(
            PreviewState previewState,
            java.util.function.Function<PreviewState, Boolean> enabled,
            Icon enabledIcon,
            Icon disabledIcon,
            java.util.function.Consumer<PreviewState> action,
            String tooltip) {
        Button button = new Button()
                .noText()
                .addPreIcon(AETextures.icon(enabled.apply(previewState) ? enabledIcon : disabledIcon))
                .setOnClick(event -> action.accept(previewState));
        button.buttonStyle(style -> style
                .baseTexture(Sprites.RECT_RD)
                .hoverTexture(Sprites.RECT_RD_LIGHT)
                .pressedTexture(Sprites.RECT_RD_DARK));
        button.layout(layout -> layout.width(TOOL_BUTTON_SIZE).height(TOOL_BUTTON_SIZE));
        button.addEventListener(UIEvents.TICK, event -> button.getChildren().getFirst().style(style ->
                style.backgroundTexture(AETextures.icon(enabled.apply(previewState) ? enabledIcon : disabledIcon))));
        return HostElements.tooltips(button, Component.translatable(tooltip));
    }

    private static UIElement previewSection(
            ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface,
            PreviewState previewState) {
        UIElement section = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .gapAll(4)
                .flexDirection(FlexDirection.COLUMN));
        section.addChild(previewHeader(craftingInterface, previewState));
        section.addChild(patternPreviewRow(craftingInterface, previewState));
        return section;
    }

    private static UIElement patternPreviewRow(
            ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface,
            PreviewState previewState) {
        UIElement row = new UIElement().layout(layout -> layout
                .width(PREVIEW_WIDTH + PREVIEW_SCROLLBAR_GAP + PREVIEW_SCROLLBAR_WIDTH)
                .height(PREVIEW_HEIGHT)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER));
        row.addChild(patternPreview(craftingInterface, previewState));
        row.addChild(patternPreviewScrollbar(previewState));
        return row;
    }

    private static UIElement patternPreview(
            ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface,
            PreviewState previewState) {
        UIElement preview = new UIElement().layout(layout -> layout
                .width(PREVIEW_WIDTH)
                .height(PREVIEW_HEIGHT)
                .alignItems(AlignItems.CENTER))
                .addClass("panel_border");
        preview.addEventListener(UIEvents.TICK, event -> previewState.refresh());
        // Consume ordinary wheel input before AE2 sees it, but leave Shift + wheel
        // to the individual preview slot so it can retain the quick-move shortcut.
        preview.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0 && !event.isShiftDown()) {
                previewState.scroll(event.deltaY < 0 ? 1 : -1);
                event.stopImmediatePropagation();
            }
        });
        UIElement slotLayer = new UIElement().layout(layout -> layout
                .width(PREVIEW_WIDTH)
                .height(PREVIEW_HEIGHT))
                .setOverflowVisible(false);
        for (int visualSlot = 0; visualSlot < ECOMachineInterfaceBlockEntity.PATTERN_INTERFACE_VISIBLE_SLOTS; visualSlot++) {
            slotLayer.addChild(previewState.createSlot(visualSlot));
        }
        preview.addChild(slotLayer);
        return preview;
    }

    private static UIElement patternPreviewScrollbar(PreviewState previewState) {
        UIElement scrollbar = new UIElement() {
            @Override
            public void drawContents(GUIContext guiContext) {
                int totalRows = previewState.getRowCount();
                if (totalRows <= PREVIEW_ROWS) {
                    return;
                }
                int height = PREVIEW_HEIGHT;
                int maxScroll = Math.max(1, previewState.getMaxScrollRow());
                int thumbY = Math.round((height - PREVIEW_SCROLLBAR_THUMB_HEIGHT)
                        * (previewState.getScrollRow() / (float) maxScroll));
                int x = Math.round(getPositionX());
                int y = Math.round(getPositionY());
                guiContext.drawTexture(NETextures.AE_SCROLLBAR_TRACK,
                        x + (PREVIEW_SCROLLBAR_WIDTH - PREVIEW_SCROLLBAR_TRACK_WIDTH) / 2F, y,
                        PREVIEW_SCROLLBAR_TRACK_WIDTH, height);
                guiContext.drawTexture(NETextures.AE_SCROLLBAR_THUMB, x, y + thumbY,
                        PREVIEW_SCROLLBAR_WIDTH, PREVIEW_SCROLLBAR_THUMB_HEIGHT);
            }
        }.layout(layout -> layout
                .width(PREVIEW_SCROLLBAR_WIDTH)
                .height(PREVIEW_HEIGHT)
                .marginLeft(PREVIEW_SCROLLBAR_GAP));
        scrollbar.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            int maxScroll = previewState.getMaxScrollRow();
            if (event.button == 0 && maxScroll > 0) {
                float localY = getLocalY(scrollbar, event.x, event.y);
                float progress = Math.clamp((localY - PREVIEW_SCROLLBAR_THUMB_HEIGHT / 2F)
                        / (PREVIEW_HEIGHT - PREVIEW_SCROLLBAR_THUMB_HEIGHT), 0F, 1F);
                previewState.setScrollRow(Math.round(progress * maxScroll));
                scrollbar.getModularUI().getDragHandler().startDrag(
                        (float) previewState.getScrollRow(), null, scrollbar);
                event.stopImmediatePropagation();
            }
        });
        scrollbar.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
            if (event.dragHandler == null || !(event.dragHandler.draggingObject instanceof Float initialRow)) {
                return;
            }
            float deltaY = getLocalY(scrollbar, event.x, event.y)
                    - getLocalY(scrollbar, event.dragStartX, event.dragStartY);
            float remainingSpace = PREVIEW_HEIGHT - PREVIEW_SCROLLBAR_THUMB_HEIGHT;
            float row = initialRow + deltaY / remainingSpace * previewState.getMaxScrollRow();
            previewState.setScrollRow(Math.round(row));
            event.stopImmediatePropagation();
        });
        scrollbar.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0) {
                previewState.scroll(event.deltaY < 0 ? 1 : -1);
                event.stopImmediatePropagation();
            }
        });
        return scrollbar;
    }

    /** UIElement#getLocalMouse uses the root UI coordinate space, not this element's layout offset. */
    private static float getLocalY(UIElement element, float x, float y) {
        return element.getLocalMouse(x, y).y - element.getPositionY();
    }

    private static Button iconButton(Icon icon, String tooltip, Runnable action) {
        Button button = new Button()
                .noText()
                .addPreIcon(AETextures.icon(icon))
                .setOnServerClick(event -> action.run());
        button.buttonStyle(style -> style
                .baseTexture(Sprites.RECT_RD)
                .hoverTexture(Sprites.RECT_RD_LIGHT)
                .pressedTexture(Sprites.RECT_RD_DARK));
        button.layout(layout -> layout.width(TOOL_BUTTON_SIZE).height(TOOL_BUTTON_SIZE));
        return HostElements.tooltips(button, Component.translatable(tooltip));
    }

    /** Client-local filter state over the full synchronized handler. */
    private static final class PreviewState {
        private final ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface;
        private final IItemHandlerModifiable handler;
        private final List<PatternItemSlot> slots = new ArrayList<>();
        private final List<Integer> visibleSlots = new ArrayList<>();
        private final boolean[] highlightedSlots = new boolean[ECOMachineInterfaceBlockEntity.PATTERN_INTERFACE_VISIBLE_SLOTS];
        private int[] appliedView = new int[0];
        private String search = "";
        private boolean showSubstitution = true;
        private boolean showFluidSubstitution = true;
        private int scrollRow;
        private int lastRevision = Integer.MIN_VALUE;
        private int requestedIndexRevision = Integer.MIN_VALUE;
        private boolean filterDirty = true;

        private PreviewState(
                ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface,
                IItemHandlerModifiable handler) {
            this.craftingInterface = craftingInterface;
            this.handler = handler;
        }

        private PatternItemSlot createSlot(int visualSlot) {
            ItemHandlerSlot itemHandlerSlot = new ItemHandlerSlot(handler, visualSlot)
                    .addChangeListener(this::markContentDirty);
            PatternItemSlot slot = new PatternItemSlot(itemHandlerSlot);
            slot.highlighted(() -> highlightedSlots[visualSlot]);
            slot.slotStyle(style -> style.slotOverlay(NETextures.PATTERN_OVERLAY));
            slot.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left((visualSlot % PREVIEW_COLUMNS) * 18)
                    .top((visualSlot / PREVIEW_COLUMNS) * 18)
                    .width(18)
                    .height(18));
            slots.add(slot);
            return slot;
        }

        /** Slot packets and managed revision packets can arrive in either order. */
        private void markContentDirty() {
            filterDirty = true;
        }

        private boolean showsSubstitutionPatterns() {
            return showSubstitution;
        }

        private boolean showsFluidSubstitutionPatterns() {
            return showFluidSubstitution;
        }

        private void toggleSubstitutionPatterns() {
            showSubstitution = !showSubstitution;
            resetFilter();
        }

        private void toggleFluidSubstitutionPatterns() {
            showFluidSubstitution = !showFluidSubstitution;
            resetFilter();
        }

        private void setSearch(String value) {
            String next = value == null ? "" : value.substring(0, Math.min(value.length(), PREVIEW_QUERY_MAX_LENGTH));
            if (!search.equals(next)) {
                search = next;
                resetFilter();
            }
        }

        private void resetFilter() {
            scrollRow = 0;
            filterDirty = true;
        }

        private void refresh() {
            if (craftingInterface.getLevel() == null || !craftingInterface.getLevel().isClientSide) {
                return;
            }
            int revision = craftingInterface.getPatternContentRevision();
            var searchIndex = craftingInterface.getClientPatternSearchIndex();
            if (searchIndex.revision() != revision) {
                if (requestedIndexRevision != revision) {
                    requestedIndexRevision = revision;
                    craftingInterface.rpcToServer("requestPatternSearchIndex", searchIndex.revision());
                }
                return;
            }
            if (!filterDirty && revision == lastRevision) {
                return;
            }
            rebuildFilter(searchIndex);
            lastRevision = revision;
            filterDirty = false;
            scrollRow = Math.clamp(scrollRow, 0, getMaxScrollRow());
            updateSlots();
        }

        private void rebuildFilter(ECOMachineInterfaceBlockEntity.PatternSearchIndex searchIndex) {
            visibleSlots.clear();
            List<String> terms = tokenize(search);
            for (int patternIndex = 0; patternIndex < searchIndex.size(); patternIndex++) {
                byte flags = searchIndex.flags(patternIndex);
                if (!passesSubstitutionFilter(flags)) {
                    continue;
                }
                if ((flags & 4) == 0) {
                    if (terms.isEmpty()) {
                        visibleSlots.add(patternIndex);
                    }
                    continue;
                }
                if (terms.isEmpty() || matchesSearch(searchIndex.keywords(patternIndex), terms)) {
                    visibleSlots.add(patternIndex);
                }
            }
        }

        private boolean passesSubstitutionFilter(byte flags) {
            return (showSubstitution || (flags & 1) == 0)
                    && (showFluidSubstitution || (flags & 2) == 0);
        }

        private static boolean matchesSearch(String keywords, List<String> terms) {
            return terms.stream().allMatch(keywords::contains);
        }

        private static List<String> tokenize(String value) {
            return Arrays.stream(value.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                    .filter(term -> !term.isEmpty()).toList();
        }

        private void updateSlots() {
            Arrays.fill(highlightedSlots, false);
            int start = Math.min(scrollRow * PREVIEW_COLUMNS, visibleSlots.size());
            int end = Math.min(start + PREVIEW_COLUMNS * PREVIEW_ROWS, visibleSlots.size());
            int[] view = new int[end - start];
            for (int offset = start; offset < end; offset++) {
                int patternIndex = visibleSlots.get(offset);
                int visualOffset = offset - start;
                view[visualOffset] = patternIndex;
                highlightedSlots[visualOffset] = !search.isBlank();
            }
            if (!Arrays.equals(appliedView, view)) {
                appliedView = view;
                CompoundTag payload = new CompoundTag();
                payload.putIntArray("slots", view);
                craftingInterface.rpcToServer("setPatternInterfaceView", payload);
            }
        }

        private int getRowCount() {
            return (visibleSlots.size() + PREVIEW_COLUMNS - 1) / PREVIEW_COLUMNS;
        }

        private int getMaxScrollRow() {
            return Math.max(0, getRowCount() - PREVIEW_ROWS);
        }

        private int getScrollRow() {
            return scrollRow;
        }

        private void setScrollRow(int value) {
            int next = Math.clamp(value, 0, getMaxScrollRow());
            if (next != scrollRow) {
                scrollRow = next;
                updateSlots();
            }
        }

        private void scroll(int delta) {
            setScrollRow(scrollRow + delta);
        }
    }

    private static Label boundLabel(Supplier<Component> text) {
        Label label = new Label();
        label.setText(text.get());
        label.bind(DataBindingBuilder.componentS2C(text).build());
        label.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).adaptiveWidth(false).adaptiveHeight(true));
        label.layout(layout -> layout.widthPercent(100).height(12));
        return label;
    }

    private static Label statusLabel(Supplier<Component> text) {
        Label label = boundLabel(text);
        label.layout(layout -> layout.widthPercent(100).height(12).marginLeft(2));
        return label;
    }
}
