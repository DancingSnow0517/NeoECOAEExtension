package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
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
    static final int PREVIEW_QUERY_MAX_LENGTH = 128;
    private static final IGuiTexture SUBSTITUTION_ENABLED = NETextures.aeIcon(224, 208, 8, 8);
    private static final IGuiTexture SUBSTITUTION_DISABLED = NETextures.aeIcon(232, 208, 8, 8);
    private static final IGuiTexture FLUID_SUBSTITUTION_ENABLED = NETextures.aeIcon(224, 216, 8, 8);
    private static final IGuiTexture FLUID_SUBSTITUTION_DISABLED = NETextures.aeIcon(232, 216, 8, 8);
    private static final IGuiTexture PATTERN_ACCESS_SHOW = NETextures.aeIcon(64, 80, 16, 16);

    private CraftingInterfaceUI() {
    }

    public static ModularUI create(
            ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface,
            Player player) {
        IItemHandlerModifiable patternHandler =
                craftingInterface.createPatternInterfaceItemHandler(player.getUUID());
        CraftingPatternPreviewState previewState = new CraftingPatternPreviewState(craftingInterface, patternHandler);

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
        contentFrame.addChild(secondaryStatus(craftingInterface));
        contentFrame.addChild(patternTransferProgress(craftingInterface));
        root.addChild(contentFrame);
        root.addChild(previewSection(craftingInterface, previewState, player));
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

    private static UIElement secondaryStatus(ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface) {
        UIElement container = HostElements.syncedDisplay(craftingInterface::hasPatternTransferSecondaryStatus);
        container.addChild(statusLabel(craftingInterface::getPatternTransferSecondaryStatus));
        container.layout(layout -> layout.widthPercent(100).height(12));
        return container;
    }

    private static UIElement previewHeader(
            ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface,
            CraftingPatternPreviewState previewState,
            Player player) {
        UIElement header = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .height(TOOL_BUTTON_SIZE)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .gapAll(2));
        header.addChild(patternSearchField(previewState));
        header.addChild(patternFilterButton(
                previewState,
                CraftingPatternPreviewState::showsSubstitutionPatterns,
                SUBSTITUTION_ENABLED,
                SUBSTITUTION_DISABLED,
                CraftingPatternPreviewState::toggleSubstitutionPatterns,
                "gui.neoecoae.crafting_interface.preview.filter_substitutions"));
        header.addChild(patternFilterButton(
                previewState,
                CraftingPatternPreviewState::showsFluidSubstitutionPatterns,
                FLUID_SUBSTITUTION_ENABLED,
                FLUID_SUBSTITUTION_DISABLED,
                CraftingPatternPreviewState::toggleFluidSubstitutionPatterns,
                "gui.neoecoae.crafting_interface.preview.filter_fluid_substitutions"));
        header.addChild(iconButton(PATTERN_ACCESS_SHOW, "gui.neoecoae.crafting_interface.preview.organize",
                () -> {
                    if (player instanceof ServerPlayer serverPlayer) {
                        craftingInterface.organizePatternBuses(serverPlayer);
                    }
                }));
        return header;
    }

    private static CraftingInterfaceUI.SearchField patternSearchField(CraftingPatternPreviewState previewState) {
        SearchField field = ClientUIBridge.call("createSearchField", Consumer.class,
                (Consumer<String>) previewState::setSearch, SearchField.class,
                () -> new SearchField(previewState::setSearch));
        field.layout(layout -> layout.flex(1).height(TOOL_BUTTON_SIZE));
        return HostElements.tooltips(field,
                Component.translatable("gui.neoecoae.crafting_interface.preview.search.tooltip"));
    }

    public static class SearchField extends UIElement {
        private final Consumer<String> responder;

        public SearchField(Consumer<String> responder) {
            this.responder = responder == null ? value -> { } : responder;
            setFocusable(true);
            style(style -> style.backgroundTexture(Sprites.RECT_RD));
        }

        protected final void updateSearch(String value) {
            responder.accept(value);
        }
    }

    private static Button patternFilterButton(
            CraftingPatternPreviewState previewState,
            Function<CraftingPatternPreviewState, Boolean> enabled,
            IGuiTexture enabledIcon,
            IGuiTexture disabledIcon,
            Consumer<CraftingPatternPreviewState> action,
            String tooltip) {
        Button button = new Button()
                .noText()
                .addPreIcon(enabled.apply(previewState) ? enabledIcon : disabledIcon)
                .setOnClick(event -> action.accept(previewState));
        button.buttonStyle(style -> style
                .baseTexture(Sprites.RECT_RD)
                .hoverTexture(Sprites.RECT_RD_LIGHT)
                .pressedTexture(Sprites.RECT_RD_DARK));
        button.layout(layout -> layout.width(TOOL_BUTTON_SIZE).height(TOOL_BUTTON_SIZE));
        button.addEventListener(UIEvents.TICK, event -> button.getChildren().getFirst().style(style ->
                style.backgroundTexture(enabled.apply(previewState) ? enabledIcon : disabledIcon)));
        return HostElements.tooltips(button, Component.translatable(tooltip));
    }

    private static UIElement previewSection(
            ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface,
            CraftingPatternPreviewState previewState,
            Player player) {
        UIElement section = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .gapAll(4)
                .flexDirection(FlexDirection.COLUMN));
        section.addChild(previewHeader(craftingInterface, previewState, player));
        section.addChild(patternPreviewRow(previewState));
        return section;
    }

    private static UIElement patternPreviewRow(CraftingPatternPreviewState previewState) {
        UIElement row = new UIElement().layout(layout -> layout
                .width(PREVIEW_WIDTH + PREVIEW_SCROLLBAR_GAP + PREVIEW_SCROLLBAR_WIDTH)
                .height(PREVIEW_HEIGHT)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER));
        row.addChild(patternPreview(previewState));
        row.addChild(patternPreviewScrollbar(previewState));
        return row;
    }

    private static UIElement patternPreview(CraftingPatternPreviewState previewState) {
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
            PatternItemSlot slot = previewState.createSlot(visualSlot);
            slot.slotStyle(style -> style.slotOverlay(NETextures.PATTERN_OVERLAY));
            int column = visualSlot % PREVIEW_COLUMNS;
            int row = visualSlot / PREVIEW_COLUMNS;
            slot.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(column * 18)
                    .top(row * 18)
                    .width(18)
                    .height(18));
            slotLayer.addChild(slot);
        }
        preview.addChild(slotLayer);
        return preview;
    }

    private static UIElement patternPreviewScrollbar(CraftingPatternPreviewState previewState) {
        Scroller.Vertical scroller = previewState.scroller();
        scroller.layout(layout -> layout
                .width(PREVIEW_SCROLLBAR_WIDTH)
                .height(PREVIEW_HEIGHT)
                .marginLeft(PREVIEW_SCROLLBAR_GAP));
        scroller.headButton(button -> button.setDisplay(false));
        scroller.tailButton(button -> button.setDisplay(false));
        scroller.scrollContainer(container -> {
            container.layout(layout -> {
                layout.marginLeft((PREVIEW_SCROLLBAR_WIDTH - PREVIEW_SCROLLBAR_TRACK_WIDTH) / 2F);
                layout.width(PREVIEW_SCROLLBAR_TRACK_WIDTH);
            });
            container.style(style -> style.backgroundTexture(NETextures.AE_SCROLLBAR_TRACK));
        });
        scroller.scrollBar(button -> button
                .noText()
                .buttonStyle(style -> style
                        .baseTexture(NETextures.AE_SCROLLBAR_THUMB)
                        .hoverTexture(NETextures.AE_SCROLLBAR_THUMB)
                        .pressedTexture(NETextures.AE_SCROLLBAR_THUMB))
                .layout(layout -> layout.width(PREVIEW_SCROLLBAR_WIDTH)));
        scroller.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0) {
                previewState.scroll(event.deltaY < 0 ? 1 : -1);
                event.stopImmediatePropagation();
            }
        });
        return scroller;
    }

    private static Button iconButton(IGuiTexture icon, String tooltip, Runnable action) {
        Button button = new Button()
                .noText()
                .addPreIcon(icon)
                .setOnServerClick(event -> action.run());
        button.buttonStyle(style -> style
                .baseTexture(Sprites.RECT_RD)
                .hoverTexture(Sprites.RECT_RD_LIGHT)
                .pressedTexture(Sprites.RECT_RD_DARK));
        button.layout(layout -> layout.width(TOOL_BUTTON_SIZE).height(TOOL_BUTTON_SIZE));
        return HostElements.tooltips(button, Component.translatable(tooltip));
    }

    private static Label boundLabel(Supplier<Component> text) {
        Label label = new Label();
        label.setText(text.get());
        label.bind(DataBindingBuilder.componentS2C(text).build());
        label.textStyle(style -> style.textWrap(TextWrap.NONE).adaptiveWidth(false).adaptiveHeight(true));
        label.layout(layout -> layout.widthPercent(100).height(12));
        return label;
    }

    private static Label statusLabel(Supplier<Component> text) {
        Label label = boundLabel(text);
        label.layout(layout -> layout.widthPercent(100).height(12).marginLeft(2));
        return label;
    }
}
