package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.lowdragmc.lowdraglib.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MultiBlockPreviewWidget extends WidgetGroup implements ISceneBlockRenderHook {
    private final TrackedDummyWorld world;
    private final MultiBlockDefinition def;
    private final SceneWidget sceneWidget;
    private final DraggableScrollableWidgetGroup scrollableWidgetGroup;
    private final IItemHandlerModifiable selectedItemHandler = new ItemStackHandler(1);
    private int expand;
    private int layer = -1;
    private int layerMax = 0;
    private boolean formed = false;

    public MultiBlockPreviewWidget(MultiBlockDefinition def) {
        this.def = def;
        expand = def.getExpandMin();
        world = new TrackedDummyWorld();
        setSize(160, 160);
        setClientSideWidget();
        addWidget(
            new ImageWidget(
                3, 3, 160, 10,
                new TextTexture(def.getName()::getString)
                    .setType(TextTexture.TextType.ROLL)
                    .setWidth(170)
                    .setDropShadow(true)
            )
        );
        sceneWidget = new SceneWidget(3, 3, 150, 150, world, true)
            .setDraggable(true)
            .setScalable(true)
            .setRenderSelect(true)
            .setRenderFacing(false)
            .setOnAddedTooltips(this::onSceneTooltip)
            .setOnSelected(this::onSelect);

        addWidget(sceneWidget);
        addWidget(
            new SlotWidget(selectedItemHandler, 0, 3, 14, false, false)
                .setBackgroundTexture(ColorPattern.T_GRAY.rectTexture())
                .setIngredientIO(IngredientIO.RENDER_ONLY)
        );

        if (RenderSystem.isOnRenderThread()) {
            sceneWidget.useCacheBuffer();
        } else {
            RenderSystem.recordRenderCall(sceneWidget::useCacheBuffer);
        }

        scrollableWidgetGroup = new DraggableScrollableWidgetGroup(3, 132, 154, 22)
            .setXScrollBarHeight(4)
            .setXBarStyle(new ColorRectTexture(0x7f7f7f), new ColorRectTexture(0xaaaaaa))
            .setScrollable(true)
            .setDraggable(true);

        addWidget(
            new ButtonWidget(138, 30, 18, 18,
                new GuiTextureGroup(
                    ColorPattern.T_GRAY.rectTexture(),
                    new TextTexture("").setSupplier(() -> "E: " + expand)
                ),
                c -> expand()
            )
        );

        addWidget(
            new ButtonWidget(138, 50, 18, 18,
                new GuiTextureGroup(
                    ColorPattern.T_GRAY.rectTexture(),
                    new TextTexture("").setSupplier(() -> layer >= 0 ? "L: " + layer : "All")
                ),
                c -> nextLayer()
            )
        );
        addWidget(
            new ButtonWidget(138, 70, 18, 18,
                new GuiTextureGroup(
                    ColorPattern.T_GRAY.rectTexture(),
                    new TextTexture("").setSupplier(() -> "F: " + formed)
                ),
                c -> cycleFormed()
            )
        );

        addWidget(scrollableWidgetGroup);

        createScene();
    }

    private void cycleFormed() {
        formed = !formed;
        createScene();
    }

    private void expand() {
        if (expand == def.getExpandMax()) {
            expand = def.getExpandMin();
        } else {
            expand++;
        }
        createScene();
    }

    private void nextLayer() {
        if (layer + 1 > layerMax) {
            layer = -1;
        } else {
            layer++;
        }
        createScene();
    }

    @Override
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        RenderSystem.enableBlend();
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    public void onSelect(BlockPos pos, Direction direction) {
        BlockState state = world.getBlockState(pos);
        selectedItemHandler.setStackInSlot(0, state.getBlock().asItem().getDefaultInstance());
    }

    public void onSceneTooltip(SceneWidget widget, List<Component> list) {
        BlockPosFace blockPosFace = widget.getHoverPosFace();
        if (blockPosFace == null) return;
        BlockState state = world.getBlockState(blockPosFace.pos());
        list.add(state.getBlock().getName());
    }

    public void createScene() {
        world.clear();
        MultiBlockContext.DummyDelegated context = MultiBlockContext.dummyDelegated(expand, world);
        context.setFormed(formed);
        def.createLevel(context);
        this.layerMax = context.getYMax();
        layer = Math.clamp(layer, -1, layerMax);
        if (layer == -1) {
            sceneWidget.setRenderedCore(context.allBlocks(), this);
        } else {
            List<BlockPos> rendered = new ArrayList<>();
            for (BlockPos pos : context.allBlocks()) {
                if (pos.getY() == layer) {
                    rendered.add(pos);
                }
            }
            sceneWidget.setRenderedCore(rendered);
        }
        scrollableWidgetGroup.clearAllWidgets();
        IItemHandlerModifiable itemHandler = new ItemStackHandler(NonNullList.copyOf(context.getRequiredItems()));
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            SlotWidget widget = new SlotWidget(itemHandler, i, 4 + i * 18, 0, false, false)
                .setBackgroundTexture(ColorPattern.T_GRAY.rectTexture())
                .setIngredientIO(IngredientIO.INPUT);
            scrollableWidgetGroup.addWidget(widget);
        }
        sceneWidget.needCompileCache();
    }

    @Override
    public void apply(boolean isTESR, RenderType layer) {

    }
}
