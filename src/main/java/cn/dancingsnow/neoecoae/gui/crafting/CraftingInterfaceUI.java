package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.client.gui.Icon;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/** Control surface for browsing network patterns and moving compatible ones into ECO crafting buses. */
public final class CraftingInterfaceUI {
    private static final int STATUS_CONNECTED = 0x55CC77;
    private static final int STATUS_DISCONNECTED = 0xDD5555;
    private static final int PREVIEW_COLUMNS = 9;
    private static final int PREVIEW_ROWS = 4;
    private static final int PREVIEW_WIDTH = PREVIEW_COLUMNS * 18;
    private static final int PREVIEW_HEIGHT = PREVIEW_ROWS * 18;
    private static final int PLAYER_INVENTORY_WIDTH = 9 * 18;
    private static final int ROOT_SIDE_MARGIN = 7;
    private static final int TOOL_BUTTON_SIZE = 16;
    private static final int PREVIEW_SCROLLBAR_WIDTH = 12;
    private static final int PREVIEW_SCROLLBAR_GAP = 2;
    private static final int PREVIEW_SCROLLBAR_TRACK_WIDTH = 6;
    private static final int PREVIEW_SCROLLBAR_THUMB_HEIGHT = 15;

    private CraftingInterfaceUI() {
    }

    public static ModularUI create(
            ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface,
            Player player) {
        craftingInterface.ensurePatternPreview();
        PreviewState previewState = new PreviewState();

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
        root.addChild(contentFrame);
        root.addChild(previewSection(craftingInterface, previewState));
        root.addChild(new InventorySlots().layout(layout -> layout
                .width(PLAYER_INVENTORY_WIDTH)
                .marginTop(2)));

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

    private static TextField patternSearchField(PreviewState previewState) {
        TextField field = new TextField();
        field.setTextResponder(previewState::setSearch);
        field.textFieldStyle(style -> style
                .textColor(0xFFFFFF)
                .textShadow(false)
                .placeholder(Component.translatable("gui.neoecoae.crafting_interface.preview.search")));
        field.style(style -> style.backgroundTexture(Sprites.RECT_RD));
        field.layout(layout -> layout.flex(1).height(TOOL_BUTTON_SIZE));
        // ExtendedAE clears its matrix query with a right-click. Keep that compact interaction so the search field
        // retains enough width for long pattern names.
        field.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 1) {
                event.stopImmediatePropagation();
            }
        });
        field.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 1) {
                field.setText("");
                previewState.setSearch("");
                event.stopImmediatePropagation();
            }
        });
        return HostElements.tooltips(field,
                Component.translatable("gui.neoecoae.crafting_interface.preview.search.tooltip"));
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
        preview.addEventListener(UIEvents.TICK, event -> previewState.refresh(
                craftingInterface.getPatternPreviewSnapshot(), craftingInterface.getLevel()));
        // Consume the wheel on the client before AE2 sees it. Without this, AE2's
        // inventory-scroll shortcut moves the pattern under the cursor into the
        // player inventory while this panel is being scrolled.
        preview.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0) {
                event.stopImmediatePropagation();
            }
        });
        preview.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0) {
                previewState.scroll(event.deltaY < 0 ? 1 : -1);
                event.stopImmediatePropagation();
            }
        });
        for (int row = 0; row < PREVIEW_ROWS; row++) {
            UIElement previewRow = new UIElement().layout(layout -> layout
                    .width(PREVIEW_WIDTH)
                    .height(18)
                    .flexDirection(FlexDirection.ROW));
            for (int column = 0; column < PREVIEW_COLUMNS; column++) {
                int visualSlot = row * PREVIEW_COLUMNS + column;
                LocalSlot localSlot = new LocalSlot();
                previewState.setSlot(visualSlot, localSlot);
                PatternItemSlot itemSlot = new PatternItemSlot(localSlot);
                itemSlot.highlighted(() -> previewState.isSlotMatched(visualSlot));
                itemSlot.slotStyle(style -> style.slotOverlay(NETextures.PATTERN_OVERLAY));
                itemSlot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                    if (event.button == 0 || event.button == 1) {
                        int sourceIndex = previewState.sourceIndexAt(visualSlot);
                        if (sourceIndex >= 0) {
                            craftingInterface.rpcToServer("takePatternPreviewEntry", sourceIndex, event.button == 1);
                            event.stopImmediatePropagation();
                        }
                    }
                });
                previewRow.addChild(itemSlot);
            }
            preview.addChild(previewRow);
        }
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
                float localY = scrollbar.getLocalMouse(event.x, event.y).y - scrollbar.getPositionY();
                float progress = Math.clamp((localY - PREVIEW_SCROLLBAR_THUMB_HEIGHT / 2F)
                        / (PREVIEW_HEIGHT - PREVIEW_SCROLLBAR_THUMB_HEIGHT), 0F, 1F);
                previewState.setScrollRow(Math.round(progress * maxScroll));
                event.stopImmediatePropagation();
            }
        });
        scrollbar.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0) {
                previewState.scroll(event.deltaY < 0 ? 1 : -1);
                event.stopImmediatePropagation();
            }
        });
        return scrollbar;
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

    /** Per-open-screen state, intentionally never synchronized through the block entity. */
    private static final class PreviewState {
        private final LocalSlot[] slots = new LocalSlot[PREVIEW_COLUMNS * PREVIEW_ROWS];
        private ItemStack[] snapshot = new ItemStack[0];
        private List<PatternSearchData> searchData = List.of();
        private List<Integer> visibleIndexes = List.of();
        private final Set<Integer> matchedIndexes = new HashSet<>();
        private String search = "";
        private boolean showSubstitution = true;
        private boolean showFluidSubstitution = true;
        private int scrollRow;

        private PreviewState() {
            Arrays.fill(slots, null);
        }

        private void setSlot(int visualSlot, LocalSlot slot) {
            slots[visualSlot] = slot;
        }

        private boolean showsSubstitutionPatterns() {
            return showSubstitution;
        }

        private boolean showsFluidSubstitutionPatterns() {
            return showFluidSubstitution;
        }

        private void toggleSubstitutionPatterns() {
            showSubstitution = !showSubstitution;
            rebuild();
        }

        private void toggleFluidSubstitutionPatterns() {
            showFluidSubstitution = !showFluidSubstitution;
            rebuild();
        }

        private void setSearch(String value) {
            String next = value == null ? "" : value;
            if (!search.equals(next)) {
                search = next;
                scrollRow = 0;
                rebuild();
            }
        }

        private void refresh(ItemStack[] nextSnapshot, Level level) {
            if (level == null || !level.isClientSide || nextSnapshot == null || sameSnapshot(nextSnapshot)) {
                return;
            }
            snapshot = copySnapshot(nextSnapshot);
            searchData = new ArrayList<>(snapshot.length);
            for (ItemStack stack : snapshot) {
                searchData.add(PatternSearchData.create(stack, level));
            }
            rebuild();
        }

        private boolean sameSnapshot(ItemStack[] nextSnapshot) {
            if (snapshot.length != nextSnapshot.length) {
                return false;
            }
            for (int index = 0; index < snapshot.length; index++) {
                if (!ItemStack.matches(snapshot[index], nextSnapshot[index])) {
                    return false;
                }
            }
            return true;
        }

        private static ItemStack[] copySnapshot(ItemStack[] source) {
            ItemStack[] copy = new ItemStack[source.length];
            for (int index = 0; index < source.length; index++) {
                copy[index] = source[index] == null ? ItemStack.EMPTY : source[index].copy();
            }
            return copy;
        }

        private void rebuild() {
            List<String> queryTerms = tokenize(search);
            List<Integer> nextVisible = new ArrayList<>();
            matchedIndexes.clear();
            boolean noSearch = queryTerms.isEmpty();
            for (int index = 0; index < searchData.size(); index++) {
                PatternSearchData data = searchData.get(index);
                if (noSearch && showSubstitution && showFluidSubstitution) {
                    nextVisible.add(index);
                    continue;
                }
                if (!data.encodedPattern()
                        || (!showSubstitution && data.canSubstitute())
                        || (!showFluidSubstitution && data.canSubstituteFluids())) {
                    continue;
                }
                if (noSearch || data.names().stream().anyMatch(name -> matches(name, queryTerms))) {
                    nextVisible.add(index);
                    if (!noSearch) {
                        matchedIndexes.add(index);
                    }
                }
            }
            visibleIndexes = List.copyOf(nextVisible);
            scrollRow = Math.clamp(scrollRow, 0, getMaxScrollRow());
            updateSlots();
        }

        private void updateSlots() {
            for (int visualSlot = 0; visualSlot < slots.length; visualSlot++) {
                LocalSlot slot = slots[visualSlot];
                if (slot == null) {
                    continue;
                }
                int sourceIndex = sourceIndexAt(visualSlot);
                slot.set(sourceIndex >= 0 && sourceIndex < snapshot.length
                        ? snapshot[sourceIndex].copy() : ItemStack.EMPTY);
            }
        }

        private int sourceIndexAt(int visualSlot) {
            int visibleIndex = scrollRow * PREVIEW_COLUMNS + visualSlot;
            return visibleIndex >= 0 && visibleIndex < visibleIndexes.size()
                    ? visibleIndexes.get(visibleIndex) : -1;
        }

        private boolean isSlotMatched(int visualSlot) {
            return matchedIndexes.contains(sourceIndexAt(visualSlot));
        }

        private int getRowCount() {
            return (visibleIndexes.size() + PREVIEW_COLUMNS - 1) / PREVIEW_COLUMNS;
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

        private static List<String> tokenize(String value) {
            return Arrays.stream(value.trim().toLowerCase(Locale.ROOT).split(" "))
                    .filter(term -> !term.isEmpty())
                    .toList();
        }

        private static boolean matches(List<String> nameTokens, List<String> queryTerms) {
            int queryIndex = 0;
            for (String nameToken : nameTokens) {
                if (nameToken.contains(queryTerms.get(queryIndex))) {
                    queryIndex++;
                    if (queryIndex == queryTerms.size()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private record PatternSearchData(
            boolean encodedPattern,
            boolean canSubstitute,
            boolean canSubstituteFluids,
            List<List<String>> names) {
        private static PatternSearchData create(ItemStack stack, Level level) {
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                return new PatternSearchData(false, false, false, List.of());
            }
            var encodedPattern = stack.get(AEComponents.ENCODED_CRAFTING_PATTERN);
            boolean canSubstitute = encodedPattern != null && encodedPattern.canSubstitute();
            boolean canSubstituteFluids = encodedPattern != null && encodedPattern.canSubstituteFluids();
            var details = PatternDetailsHelper.decodePattern(stack, level);
            if (details == null) {
                return new PatternSearchData(true, canSubstitute, canSubstituteFluids, List.of());
            }
            List<List<String>> names = new ArrayList<>();
            for (var output : details.getOutputs()) {
                if (output != null) {
                    names.add(PreviewState.tokenize(output.what().getDisplayName().getString()));
                }
            }
            for (var input : details.getInputs()) {
                if (input != null && input.getPossibleInputs().length > 0) {
                    var possibleInput = input.getPossibleInputs()[0];
                    if (possibleInput != null) {
                        names.add(PreviewState.tokenize(possibleInput.what().getDisplayName().getString()));
                    }
                }
            }
            return new PatternSearchData(true, canSubstitute, canSubstituteFluids, List.copyOf(names));
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
