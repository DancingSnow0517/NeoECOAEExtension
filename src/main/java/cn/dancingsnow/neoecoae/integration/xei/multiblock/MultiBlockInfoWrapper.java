package cn.dancingsnow.neoecoae.integration.xei.multiblock;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.RequiredItem;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.SupplierDataSource;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.TextWrap;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Scene;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ScrollerView;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.FlexDirection;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class MultiBlockInfoWrapper {

    public static final int DEFAULT_WIDTH = 170;
    public static final int DEFAULT_HEIGHT = 190;
    public static final int MIN_HEIGHT = 120;
    public static final int EMI_VERTICAL_RESERVE = 105;

    private static final int PADDING = 4;
    private static final int GAP = 3;
    private static final int TITLE_HEIGHT = 10;
    private static final int CONTROL_HEIGHT = 16;
    private static final int MATERIALS_HEIGHT = 27;
    private static final int TITLE_COLOR = 0x3f3d52;

    @Getter
    private final MultiBlockDefinition definition;
    private final TrackedDummyWorld world;
    private final int width;
    private final int height;
    private final IntConsumer expandChanged;

    private Scene scene;
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
    }

    public ModularUI createModularUI() {
        var root = new UIElement().layout(layout -> layout
            .width(width)
            .height(height)
            .paddingAll(PADDING)
            .gapAll(GAP)
        ).style(style -> style.backgroundTexture(IGuiTexture.EMPTY));

        root.addChild(new TextElement()
            .setText(definition.getName())
            .textStyle(textStyle -> textStyle
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textWrap(TextWrap.HOVER_ROLL)
                .textColor(TITLE_COLOR)
                .textShadow(false))
            .layout(layout -> layout
                .widthPercent(100)
                .height(TITLE_HEIGHT)));

        UIElement sceneContainer = new UIElement()
            .layout(layout -> layout.widthPercent(100).flexGrow(1).flexShrink(1))
            .style(style -> style.backgroundTexture(NETextures.CARD_BACKGROUND));
        root.addChild(sceneContainer);

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

        UIElement controls = new UIElement().layout(layout -> layout
            .widthPercent(100)
            .height(CONTROL_HEIGHT)
            .flexDirection(FlexDirection.ROW)
            .gapAll(GAP));
        expandButton = cycleButton(this::cycleExpand);
        HostElements.tooltips(expandButton,
            Component.translatable("gui.neoecoae.multiblock.preview.length.tooltip",
                definition.getExpandMin(), definition.getExpandMax()),
            Component.translatable("gui.neoecoae.multiblock.preview.cycle_hint"));
        layerButton = cycleButton(this::cycleLayer);
        HostElements.tooltips(layerButton,
            Component.translatable("gui.neoecoae.multiblock.preview.layer.tooltip"),
            Component.translatable("gui.neoecoae.multiblock.preview.cycle_hint"));
        formedButton = new Button().setText("gui.neoecoae.multiblock.preview.formed", true)
            .setOnClick(event -> toggleFormed());
        formedButton.layout(layout -> layout.width(40).height(CONTROL_HEIGHT).paddingAll(0));
        HostElements.tooltips(formedButton, Component.translatable("gui.neoecoae.multiblock.preview.formed.tooltip"));
        controls.addChildren(expandButton, layerButton, formedButton);
        root.addChild(controls);

        requiredItems = new ScrollerView();
        requiredItems.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        requiredItems.viewPort(c -> c
            .layout(layout -> layout.paddingAll(0).paddingBottom(3))
            .style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        requiredItems.viewContainer(c -> c
            .layout(layout -> layout.flexDirection(FlexDirection.ROW))
            .style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        requiredItems.layout(layout -> layout.widthPercent(100).height(MATERIALS_HEIGHT).flexShrink(0));
        root.addChild(requiredItems);

        initializeRequiredItemSlots();
        expandChanged.accept(expand);
        createScene();
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))));
    }

    /** Left click steps forward, right click steps back; both wrap around. */
    private Button cycleButton(IntConsumer step) {
        Button button = new Button().setAcceptedButtons(0, 1).setOnClick(event -> {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            step.accept(event.button == 1 ? -1 : 1);
        });
        button.layout(layout -> layout.flexGrow(1).width(0).height(CONTROL_HEIGHT).paddingAll(0));
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
        expandButton.setText(Component.translatable("gui.neoecoae.multiblock.preview.length", expand));
        layerButton.setText(layer == -1
            ? Component.translatable("gui.neoecoae.multiblock.preview.layer.all")
            : Component.translatable("gui.neoecoae.multiblock.preview.layer", layer + 1, layerMax + 1));
    }

    private void createScene() {
        world.clear();
        MultiBlockContext.DummyDelegated context = MultiBlockContext.dummyDelegated(expand, world);
        context.setFormed(formed);
        definition.createLevel(context);
        this.layerMax = context.getYMax();
        layer = JdkCompat.clamp(layer, -1, layerMax);
        if (layer == -1) {
            scene.setRenderedCore(context.allBlocks());
        } else {
            List<BlockPos> rendered = new ArrayList<>();
            for (BlockPos pos : context.allBlocks()) {
                if (pos.getY() == layer) {
                    rendered.add(pos);
                }
            }
            scene.setRenderedCore(rendered);
        }
        updateControlText();
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

        for (int i = 0; i < context.getRequiredItems().size(); i++) {
            RequiredItemSlot slot = new RequiredItemSlot(0);
            slot.xeiRecipeIngredient(IngredientIO.INPUT)
                .xeiRecipeSlot(IngredientIO.INPUT, 1);
            requiredItemSlots.add(slot);
            requiredItems.addScrollViewChild(slot);
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

    private static final class RequiredItemSlot extends ItemSlot {
        private int count;
        private ItemStack requiredItem = ItemStack.EMPTY;

        private RequiredItemSlot(int count) {
            this.count = count;
            getStyle().backgroundTexture(NETextures.ITEM_SLOT);
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
            return !requiredItem.isEmpty() && ItemStack.isSameItemSameTags(requiredItem, stack);
        }

        private boolean isUnused() {
            return requiredItem.isEmpty();
        }

        @Override
        protected void drawItemStack(GUIContext guiContext, ItemStack itemStack) {
            if (itemStack.isEmpty()) {
                return;
            }
            DrawerHelper.drawItemStack(guiContext.graphics, itemStack.copyWithCount(1), 0, 0, -1, null);
            guiContext.graphics.pose().pushPose();
            guiContext.graphics.pose().translate(0, 0, 240);
            DrawerHelper.drawStringFixedCorner(guiContext.graphics, String.valueOf(count), 17, 17, 0xffffff, true, 0.8f);
            guiContext.graphics.pose().popPose();
        }
    }
}
