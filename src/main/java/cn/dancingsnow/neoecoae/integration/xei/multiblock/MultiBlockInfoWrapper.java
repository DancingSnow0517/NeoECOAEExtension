package cn.dancingsnow.neoecoae.integration.xei.multiblock;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.RequiredItem;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.gui.util.UISoundUtils;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class MultiBlockInfoWrapper {

    public static final int DEFAULT_WIDTH = 168;
    public static final int DEFAULT_HEIGHT = 148;
    public static final int MIN_HEIGHT = DEFAULT_HEIGHT;

    private static final int TOOLBAR_HEIGHT = 10;
    private static final int CONTROL_SIZE = 8;
    private static final int MATERIAL_COLUMNS = 5;
    private static final int TITLE_COLOR = 0x3f3d52;
    private static final IGuiTexture BACKGROUND = SpriteTexture.of(
        NeoECOAE.id("textures/guis/multiblock_preview.png")).setSprite(0, 0, 168, 138);

    @Getter
    private final MultiBlockDefinition definition;
    private final TrackedDummyWorld world;
    private final int width;
    private final int height;
    private final IntConsumer expandChanged;

    private Scene scene;
    private final java.util.Map<UIElement, List<Component>> controlTooltips = new java.util.IdentityHashMap<>();
    private Button expandButton;
    private Button layerButton;
    private Button formedButton;
    private UIElement selectedSlot;

    private int expand = 1;
    private int layer = -1;
    private int layerMax = 0;
    private boolean formed = false;

    private ItemStack selectedItem = ItemStack.EMPTY;
    private ScrollerView requiredItems;
    private int materialRowOffset;
    private List<MultiBlockPreviewInfo.Row> infoRows = List.of();
    private final List<RequiredItemSlot> requiredItemSlots = new ArrayList<>();

    public MultiBlockInfoWrapper(MultiBlockDefinition definition) {
        this(definition, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public MultiBlockInfoWrapper(MultiBlockDefinition definition, int width, int height) {
        this(definition, width, height, ignored -> {});
    }

    public MultiBlockInfoWrapper(
        MultiBlockDefinition definition,
        int width,
        int height,
        IntConsumer expandChanged
    ) {
        this.definition = definition;
        this.world = new TrackedDummyWorld();
        this.width = Math.max(width, DEFAULT_WIDTH);
        this.height = Math.max(height, MIN_HEIGHT);
        this.expandChanged = expandChanged;
        this.expand = definition.getExpandMin();
    }

    public ModularUI createModularUI() {
        var root = new UIElement().layout(layout -> layout.width(width).height(height).paddingAll(0))
            .style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        root.addChild(HostElements.absolute(new TextElement()
            .setText(definition.getName())
            .textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false)
                .fontSize(8).textWrap(TextWrap.HOVER_ROLL).textColor(TITLE_COLOR).textShadow(false)),
            4, 0, 124, TOOLBAR_HEIGHT));

        UIElement artwork = new UIElement().style(style -> style.backgroundTexture(BACKGROUND));
        root.addChild(HostElements.absolute(artwork, 0, TOOLBAR_HEIGHT, DEFAULT_WIDTH, 138));
        UIElement sceneContainer = new UIElement().style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        artwork.addChild(HostElements.absolute(sceneContainer, 6, 6, 156, 83));

        scene = new Scene()
            .createScene(world)
            .setDraggable(true)
            .setScalable(true)
            .setRenderSelect(true)
            .setRenderFacing(false)
            .setShowHoverBlockTips(true)
            .useCacheBuffer()
            .setOnSelected(this::onSelect);
        HostElements.absolute(scene, 0, 0, 0, 0).layout(layout -> layout.widthPercent(100).heightPercent(100));
        sceneContainer.addChild(scene);

        selectedSlot = new ItemSlot().bindDataSource(SupplierDataSource.of(() -> selectedItem));
        HostElements.absolute(selectedSlot, 3, 3, 18, 18).setDisplay(false);
        sceneContainer.addChild(selectedSlot);

        UIElement controls = new UIElement().style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        root.addChild(HostElements.absolute(controls, 132, 1, 32, CONTROL_SIZE));
        expandButton = cycleButton(this::cycleExpand, 0);
        layerButton = cycleButton(this::cycleLayer, 1);
        formedButton = new MiniButton(2).setOnClick(event -> toggleFormed());
        controls.addChildren(HostElements.absolute(expandButton, 0, 0, CONTROL_SIZE, CONTROL_SIZE),
            HostElements.absolute(layerButton, 12, 0, CONTROL_SIZE, CONTROL_SIZE),
            HostElements.absolute(formedButton, 24, 0, CONTROL_SIZE, CONTROL_SIZE));

        UIElement info = new UIElement().style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        artwork.addChild(HostElements.absolute(info, 5, 99, 62, 34));
        for (int i = 0; i < 3; i++) {
            final int index = i;
            UIElement row = new UIElement() {
                @Override public void drawBackgroundAdditional(GUIContext context) { drawInfoRow(context, this, index); }
            };
            row.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                if (index < infoRows.size()) {
                    var value = infoRows.get(index);
                    event.hoverTooltips = HoverTooltips.empty().append(
                        value.label().copy().append(": ").append(value.value()), value.help(),
                        Component.translatable(MultiBlockPreviewInfo.PREFIX + "scope"));
                }
            });
            info.addChild(HostElements.absolute(row, 0, 2 + i * 10, 62, 10));
        }

        requiredItems = new ScrollerView().scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL)
            .horizontalScrollDisplay(ScrollDisplay.NEVER).verticalScrollDisplay(ScrollDisplay.NEVER));
        requiredItems.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        requiredItems.viewPort(view -> view.layout(layout -> layout.paddingAll(0))
            .style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        requiredItems.viewContainer(view -> view.layout(layout -> layout.width(90).paddingAll(0))
            .style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        HostElements.tooltips(requiredItems, Component.translatable(MultiBlockPreviewInfo.PREFIX + "materials_scroll"));
        requiredItems.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY == 0) return;
            int lastRow = Math.max(0, (requiredItemSlots.size() + 4) / 5 - 2);
            if (lastRow == 0) return;
            materialRowOffset = Math.max(0, Math.min(lastRow, materialRowOffset + (event.deltaY > 0 ? -1 : 1)));
            requiredItems.verticalScroller(scroller -> scroller.setValue(materialRowOffset / (float) lastRow));
            event.hasHandler = true;
            event.stopImmediatePropagation();
        }, true);
        artwork.addChild(HostElements.absolute(requiredItems, 74, 98, 90, 36));

        initializeRequiredItemSlots();
        expandChanged.accept(expand);
        createScene();
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))));
    }

    /** Left click steps forward, right click steps back; both wrap around. */
    private Button cycleButton(IntConsumer step, int icon) {
        Button button = new MiniButton(icon).setOnClick(event -> step.accept(1));
        button.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 1) {
                UISoundUtils.playButtonClickSound();
                step.accept(-1);
            }
        });
        button.layout(layout -> layout.width(CONTROL_SIZE).height(CONTROL_SIZE).paddingAll(0));
        return button;
    }

    public List<RequiredItem> getRequiredItems() {
        var context = MultiBlockContext.dummyDelegated(expand, new TrackedDummyWorld());
        definition.createLevel(context);
        return context.getRequiredItems();
    }

    private void onSelect(BlockPos blockPos, Direction direction) {
        BlockState state = world.getBlockState(blockPos);
        selectedItem = state.isAir() ? ItemStack.EMPTY : new ItemStack(state.getBlock());
        selectedSlot.setDisplay(!selectedItem.isEmpty());
    }

    private void toggleFormed() {
        setFormed(!formed);
        createScene();
    }

    private void setFormed(boolean formed) {
        this.formed = formed;
        formedButton.buttonStyle(style -> style
            .baseTexture(formed ? NETextures.BUTTON_HIGHLIGHTED : NETextures.BUTTON)
            .hoverTexture(NETextures.BUTTON_HIGHLIGHTED)
            .pressedTexture(NETextures.BUTTON_HIGHLIGHTED));
    }

    private void cycleExpand(int step) {
        int min = definition.getExpandMin();
        expand = min + Math.floorMod(expand - min + step, definition.getExpandMax() - min + 1);
        expandChanged.accept(expand);
        setFormed(false);
        createScene();
    }

    private void cycleLayer(int step) {
        layer = Math.floorMod(layer + 1 + step, layerMax + 2) - 1;
        setFormed(false);
        createScene();
    }

    private void updateControlText() {
        setControlTooltip(expandButton, List.of(
            Component.translatable("gui.neoecoae.multiblock.preview.length", expand + 4),
            Component.translatable("gui.neoecoae.multiblock.preview.length.tooltip",
                definition.getExpandMin() + 4, definition.getExpandMax() + 4),
            Component.translatable("gui.neoecoae.multiblock.preview.cycle_hint")));
        setControlTooltip(layerButton, List.of(layer == -1
            ? Component.translatable("gui.neoecoae.multiblock.preview.layer.all")
            : Component.translatable("gui.neoecoae.multiblock.preview.layer", layer + 1, layerMax + 1),
            Component.translatable("gui.neoecoae.multiblock.preview.layer.tooltip"),
            Component.translatable("gui.neoecoae.multiblock.preview.cycle_hint")));
        setControlTooltip(formedButton, List.of(
            Component.translatable("gui.neoecoae.multiblock.preview.formed.tooltip"),
            Component.translatable(MultiBlockPreviewInfo.PREFIX + (formed ? "formed_on" : "formed_off"))));
    }

    private void setControlTooltip(UIElement element, List<Component> lines) {
        if (!controlTooltips.containsKey(element)) {
            element.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                event.hoverTooltips = HoverTooltips.empty().append(controlTooltips.get(element).toArray(Component[]::new)));
        }
        controlTooltips.put(element, lines);
    }

    private void createScene() {
        // Build without the previous layer/length filter hiding newly added preview blocks.
        world.setBlockFilter(null);
        world.clear();
        MultiBlockContext.DummyDelegated context = MultiBlockContext.dummyDelegated(expand, world);
        context.setFormed(formed);
        definition.createLevel(context);
        this.layerMax = context.getYMax();
        layer = Math.clamp(layer, -1, layerMax);
        if (layer == -1) {
            scene.setRenderedCore(context.allBlocks());
            world.setBlockFilter(context.allBlocks()::contains);
        } else {
            List<BlockPos> rendered = new ArrayList<>();
            for (BlockPos pos : context.allBlocks()) {
                if (pos.getY() == layer) {
                    rendered.add(pos);
                }
            }
            scene.setRenderedCore(rendered);
            world.setBlockFilter(rendered::contains);
        }
        updateControlText();
        infoRows = MultiBlockPreviewInfo.rows(definition.getOwner().value(), context.getRequiredItems());
        updateRequiredItemSlots(context.getRequiredItems());
    }

    /**
     * JEI keeps references to the recipe slot widgets after the recipe UI is
     * created. Keep those widgets alive while the preview length changes, so
     * JEI and AE2 observe the updated stack count instead of the initial one.
     */
    private void initializeRequiredItemSlots() {
        requiredItemSlots.clear();
        MultiBlockContext.DummyDelegated context = MultiBlockContext.dummyDelegated(
            definition.getExpandMax(),
            new TrackedDummyWorld()
        );
        definition.createLevel(context);

        int size = context.getRequiredItems().size();
        requiredItems.viewContainer(view -> view.layout(layout -> layout.height(Math.max(36, ((size + 4) / 5) * 18))));
        for (int i = 0; i < size; i++) {
            RequiredItemSlot slot = new RequiredItemSlot(0);
            slot.xeiRecipeIngredient(IngredientIO.INPUT)
                .xeiRecipeSlot(IngredientIO.INPUT, 1);
            requiredItemSlots.add(slot);
            requiredItems.addScrollViewChild(HostElements.absolute(slot, i % MATERIAL_COLUMNS * 18,
                i / MATERIAL_COLUMNS * 18, 18, 18));
        }
    }

    private void updateRequiredItemSlots(List<RequiredItem> items) {
        for (RequiredItemSlot slot : requiredItemSlots) {
            slot.setRequiredItem(null);
        }

        for (RequiredItem item : items) {
            RequiredItemSlot slot = requiredItemSlots.stream()
                .filter(candidate -> candidate.matches(item.stack()))
                .findFirst()
                .orElseGet(() -> requiredItemSlots.stream()
                    .filter(RequiredItemSlot::isUnused)
                    .findFirst()
                    .orElse(null));
            if (slot != null) {
                slot.setRequiredItem(item);
            }
        }
    }

    private void drawInfoRow(GUIContext context, UIElement element, int index) {
        if (index >= infoRows.size()) return;
        var row = infoRows.get(index);
        var font = Minecraft.getInstance().font;
        int x = Math.round(element.getPositionX()), y = Math.round(element.getPositionY());
        context.graphics.fill(x, y, x + 62, y + 10, index % 2 == 0 ? 0xffadb0c4 : 0xff9a9fb4);
        // Fit both columns as one line, including languages with wider labels.
        float scale = Math.min(0.7f, 59f / Math.max(1, font.width(row.label()) + font.width(row.value()) + 4));
        context.pose.pushPose();
        context.pose.translate(x + 1, y + (10 - 9 * scale) / 2, 0);
        context.pose.scale(scale, scale, 1);
        context.graphics.drawString(font, row.label(), 0, 0, 0x555563, false);
        context.graphics.drawString(font, row.value(), Math.round(60 / scale) - font.width(row.value()), 0, 0x242432, false);
        context.pose.popPose();
    }

    /** Pixel glyphs remain legible without shrinking a normal 20px button's borders. */
    private final class MiniButton extends Button {
        private final int icon;
        private MiniButton(int icon) {
            this.icon = icon;
            noText();
            layout(layout -> layout.width(CONTROL_SIZE).height(CONTROL_SIZE).paddingAll(0));
        }

        @Override public void drawBackgroundAdditional(GUIContext context) {
            int x = Math.round(getPositionX()), y = Math.round(getPositionY());
            var graphics = context.graphics;
            graphics.fill(x, y, x + 8, y + 8, 0xff878fa5);
            graphics.fill(x + 1, y + 1, x + 7, y + 7,
                icon == 2 && formed ? 0xffb9d4c4 : getState() == State.DEFAULT ? 0xffcbccd4 : 0xfff2f2f2);
            int ink = 0xff3f3d52;
            if (icon == 0) {
                graphics.fill(x + 1, y + 3, x + 7, y + 4, ink);
                graphics.fill(x + 1, y + 2, x + 2, y + 5, ink);
                graphics.fill(x + 6, y + 2, x + 7, y + 5, ink);
            } else if (icon == 1) {
                graphics.fill(x + 2, y + 2, x + 6, y + 3, ink);
                graphics.fill(x + 2, y + 4, x + 6, y + 5, ink);
                graphics.fill(x + 2, y + 6, x + 6, y + 7, ink);
            } else {
                graphics.fill(x + 2, y + 2, x + 6, y + 6, ink);
                graphics.fill(x + 3, y + 3, x + 5, y + 5, 0xffcbccd4);
            }
        }
    }

    private static final class RequiredItemSlot extends ItemSlot {
        private int count;
        private ItemStack requiredItem = ItemStack.EMPTY;

        private RequiredItemSlot(int count) {
            this.count = count;
            getStyle().backgroundTexture(IGuiTexture.EMPTY);
            layout(layout -> layout.width(18).height(18).paddingAll(1).flexShrink(0));
            slotStyle(style -> style.slotOverlay(IGuiTexture.EMPTY));
        }

        private void setRequiredItem(@Nullable RequiredItem requiredItem) {
            if (requiredItem == null || requiredItem.isEmpty()) {
                this.requiredItem = ItemStack.EMPTY;
                this.count = 0;
                setItem(ItemStack.EMPTY, false);
            } else {
                this.requiredItem = requiredItem.stack();
                this.count = requiredItem.count();
                setItem(requiredItem.stackWithCount(), false);
            }
        }

        private boolean matches(ItemStack stack) {
            return !requiredItem.isEmpty() && ItemStack.isSameItemSameComponents(requiredItem, stack);
        }

        private boolean isUnused() {
            return requiredItem.isEmpty();
        }

        @Override
        protected void drawItemStack(GUIContext guiContext, ItemStack itemStack) {
            if (itemStack.isEmpty()) {
                return;
            }
            ItemStack icon = itemStack.copyWithCount(1);
            // LDLib's item layer needs its depth setup; its helper uses vanilla decorations at full size.
            DrawerHelper.drawItemStack(guiContext.graphics, icon, 0, 0, -1,
                count == 1 ? null : String.valueOf(count));
        }
    }
}
