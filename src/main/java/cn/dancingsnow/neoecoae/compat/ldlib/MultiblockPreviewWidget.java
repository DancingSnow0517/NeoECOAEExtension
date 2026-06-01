package cn.dancingsnow.neoecoae.compat.ldlib;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class MultiblockPreviewWidget extends WidgetGroup {
    public static final int WIDTH = 170;
    public static final int HEIGHT = 190;

    private final MultiBlockDefinition definition;
    private final TrackedDummyWorld world = new TrackedDummyWorld();
    private final SimpleContainer selectedContainer = new SimpleContainer(1);

    private SceneWidget scene;
    private DraggableScrollableWidgetGroup requiredItems;
    private SimpleContainer requiredContainer = new SimpleContainer(0);
    private TextTexture expandText;
    private TextTexture layerText;
    private TextTexture formedText;
    private TextTextureWidget selectedText;

    private int expand;
    private int layer = -1;
    private int layerMax = 0;
    private boolean formed = false;

    public MultiblockPreviewWidget(MultiBlockDefinition definition) {
        super(0, 0, WIDTH, HEIGHT);
        this.definition = definition;
        this.expand = definition.getExpandMin();
        setClientSideWidget();
        setBackground(ResourceBorderTexture.BORDERED_BACKGROUND);
        buildWidgets();
        createScene();
    }

    private void buildWidgets() {
        addWidget(new TextTextureWidget(4, 4, 96, 12, definition.getName().getString())
                .textureStyle(texture -> texture.setType(TextTexture.TextType.LEFT_ROLL).setWidth(96)));

        expandText = new TextTexture("E:" + expand, 0xFFFFFFFF);
        layerText = new TextTexture("L:*", 0xFFFFFFFF);
        formedText = new TextTexture("F:N", 0xFFFFFFFF);
        addWidget(button(104, expandText, this::nextExpand));
        addWidget(button(126, layerText, this::nextLayer));
        addWidget(button(148, formedText, this::toggleFormed));

        scene = new SceneWidget(2, 18, 166, 122, world)
                .setDraggable(true)
                .setScalable(true)
                .setRenderSelect(true)
                .setRenderFacing(false)
                .setHoverTips(true)
                .useCacheBuffer()
                .setOnSelected(this::onSelect);
        addWidget(scene);

        addWidget(new SlotWidget(selectedContainer, 0, 4, 144, false, false)
                .setDrawHoverTips(true)
                .setCanPutItems(false)
                .setCanTakeItems(false));
        selectedText = new TextTextureWidget(24, 147, 142, 10, "");
        selectedText.textureStyle(texture -> texture.setType(TextTexture.TextType.LEFT_ROLL).setWidth(142));
        addWidget(selectedText);

        requiredItems = new DraggableScrollableWidgetGroup(2, 160, 166, 28)
                .setBackground(new ColorRectTexture(0x66000000))
                .setScrollable(true)
                .setUseScissor(true);
        addWidget(requiredItems);
    }

    private ButtonWidget button(int x, TextTexture text, Runnable action) {
        text.setType(TextTexture.TextType.NORMAL);
        return new ButtonWidget(
                x,
                3,
                20,
                13,
                new GuiTextureGroup(ResourceBorderTexture.BUTTON_COMMON, text),
                click -> action.run());
    }

    private void onSelect(BlockPos pos, Direction direction) {
        BlockState state = world.getBlockState(pos);
        ItemStack stack = state.isAir() ? ItemStack.EMPTY : new ItemStack(state.getBlock());
        selectedContainer.setItem(0, stack);
        selectedText.setText(stack.isEmpty() ? Component.empty() : stack.getHoverName());
    }

    private void nextExpand() {
        if (expand >= definition.getExpandMax()) {
            expand = definition.getExpandMin();
        } else {
            expand++;
        }
        expandText.updateText("E:" + expand);
        if (formed) {
            formed = false;
            formedText.updateText("F:N");
        }
        createScene();
    }

    private void nextLayer() {
        if (layer + 1 > layerMax) {
            layer = -1;
        } else {
            layer++;
        }
        updateLayerText();
        if (formed) {
            formed = false;
            formedText.updateText("F:N");
        }
        createScene();
    }

    private void toggleFormed() {
        formed = !formed;
        formedText.updateText(formed ? "F:Y" : "F:N");
        createScene();
    }

    private void createScene() {
        world.clear();
        selectedContainer.setItem(0, ItemStack.EMPTY);
        selectedText.setText(Component.empty());

        MultiBlockContext.DummyDelegated context = MultiBlockContext.dummyDelegated(expand, world);
        context.setFormed(formed);
        definition.createLevel(context);
        layerMax = context.getYMax();
        if (layer > layerMax) {
            layer = -1;
        }
        updateLayerText();

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
        scene.needCompileCache();
        refreshRequiredItems(context.getRequiredItems());
    }

    private void updateLayerText() {
        layerText.updateText(layer < 0 ? "L:*" : "L:" + layer);
    }

    private void refreshRequiredItems(List<ItemStack> stacks) {
        requiredItems.clearAllWidgets();
        requiredContainer = new SimpleContainer(stacks.size());
        for (int i = 0; i < stacks.size(); i++) {
            requiredContainer.setItem(i, stacks.get(i));
            int x = 3 + i * 18;
            requiredItems.addWidget(new SlotWidget(requiredContainer, i, x, 4, false, false)
                    .setDrawHoverTips(true)
                    .setCanPutItems(false)
                    .setCanTakeItems(false)
                    .setIngredientIO(IngredientIO.INPUT));
        }
        requiredItems.computeMax();
    }
}
