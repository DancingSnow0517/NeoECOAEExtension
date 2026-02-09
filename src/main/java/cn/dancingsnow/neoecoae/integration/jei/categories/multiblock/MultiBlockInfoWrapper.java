package cn.dancingsnow.neoecoae.integration.jei.categories.multiblock;

import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.appliedenergistics.yoga.YogaEdge;
import org.appliedenergistics.yoga.YogaGutter;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.ArrayList;
import java.util.List;

public class MultiBlockInfoWrapper {

    @Getter
    private final MultiBlockDefinition definition;
    private final TrackedDummyWorld world;

    private Scene scene;
    private Button expandButton;
    private Button layerButton;
    private Button formedButton;

    private int expand = 1;
    private int layer = -1;
    private int layerMax = 0;
    private boolean formed = false;

    private ItemStack selectedItem = ItemStack.EMPTY;
    private UIElement requiredItems;

    public MultiBlockInfoWrapper(MultiBlockDefinition definition) {
        this.definition = definition;
        this.world = new TrackedDummyWorld();
    }

    public ModularUI createModularUI() {
        var root = new UIElement().layout(layout -> layout
            .setWidth(170)
            .setHeight(200)
            .setPadding(YogaEdge.ALL, 4)
            .setGap(YogaGutter.ALL, 2)
        ).addClass("panel_bg");

        scene = new Scene()
            .createScene(world)
            .setDraggable(true)
            .setScalable(true)
            .setRenderSelect(true)
            .setRenderFacing(false)
            .setShowHoverBlockTips(true)
            .useCacheBuffer()
            .setOnSelected(this::onSelect);
        scene.getLayout().setWidth(165).setHeight(170);
        root.addChild(scene);

        UIElement buttons = new UIElement().layout(layout -> layout
            .positionType(YogaPositionType.ABSOLUTE)
            .setPosition(YogaEdge.RIGHT, 2)
            .setPosition(YogaEdge.TOP, 2)
        );
        expandButton = new Button().setText("E: " + expand).setOnClick(event -> expand());
        expandButton.getLayout().setHeight(18).setWidth(18);
        buttons.addChild(expandButton);

        layerButton = new Button().setText("L: " + layer).setOnClick(event -> nextLayer());
        layerButton.getLayout().setHeight(18).setWidth(18);
        buttons.addChild(layerButton);

        formedButton = new Button().setText("F: " + formed).setOnClick(event -> cycleFormed());
        formedButton.getLayout().setHeight(18).setWidth(18);
        buttons.addChild(formedButton);

        root.addChild(buttons);

        root.addChild(new TextElement()
            .setText(definition.getName())
            .textStyle(textStyle -> textStyle.textWrap(TextWrap.HOVER_ROLL))
            .layout(layout -> layout.setPositionType(YogaPositionType.ABSOLUTE)
                .setPosition(YogaEdge.LEFT, 2)
                .setPosition(YogaEdge.TOP, 2)));

        root.addChild(new ItemSlot()
            .bindDataSource(SupplierDataSource.of(() -> selectedItem))
            .layout(layout -> layout.setPositionType(YogaPositionType.ABSOLUTE)
                .setPosition(YogaEdge.LEFT, 2)
                .setPosition(YogaEdge.TOP, 14))
            .addClass("panel_border"));

        requiredItems = new Scroller.Horizontal()
            .layout(layout -> layout.setWidthPercent(100).setHeight(18).paddingAll(0))
            .addClass("panel_border");
        root.addChild(requiredItems);

        createScene();
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))));
    }

    private void onSelect(BlockPos blockPos, Direction direction) {
        BlockState state = world.getBlockState(blockPos);
        if (!state.isAir()) {
            selectedItem = new ItemStack(state.getBlock());
        } else {
            selectedItem = ItemStack.EMPTY;
        }
    }

    private void cycleFormed() {
        formed = !formed;
        formedButton.setText("F: " + formed);
        createScene();
    }

    private void expand() {
        if (expand == definition.getExpandMax()) {
            expand = definition.getExpandMin();
        } else {
            expand++;
        }
        expandButton.setText("E: " + expand);
        if (formed) {
            cycleFormed();
        } else {
            createScene();
        }
        createScene();
    }

    private void nextLayer() {
        if (layer + 1 > layerMax) {
            layer = -1;
        } else {
            layer++;
        }
        layerButton.setText("L: " + layer);
        if (formed) {
            cycleFormed();
        } else {
            createScene();
        }
    }

    private void createScene() {
        world.clear();
        MultiBlockContext.DummyDelegated context = MultiBlockContext.dummyDelegated(expand, world);
        context.setFormed(formed);
        definition.createLevel(context);
        this.layerMax = context.getYMax();
        layer = Math.clamp(layer, -1, layerMax);
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
//        scrollableWidgetGroup.clearAllWidgets();
        IItemHandlerModifiable itemHandler = new ItemStackHandler(NonNullList.copyOf(context.getRequiredItems()));
        requiredItems.clearAllChildren();
        for (ItemStack requiredItem : context.getRequiredItems()) {
            requiredItems.addChild(new ItemSlot()
                .setItem(requiredItem)
                .xeiRecipeIngredient(IngredientIO.INPUT)
                .xeiRecipeSlot(IngredientIO.INPUT, 1));
        }
//        for (int i = 0; i < itemHandler.getSlots(); i++) {
//            SlotWidget widget = new SlotWidget(itemHandler, i, 4 + i * 18, 0, false, false)
//                .setBackgroundTexture(ColorPattern.T_GRAY.rectTexture())
//                .setIngredientIO(IngredientIO.INPUT);
//            scrollableWidgetGroup.addWidget(widget);
//        }
    }
}
