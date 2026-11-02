package cn.dancingsnow.neoecoae.gui.multiblock;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockRotation;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Client-only scene setup shared by recipe previews and the host's structure builder. */
public final class MultiblockPreviewScene {
    private MultiblockPreviewScene() {
    }

    public static Scene create(TrackedDummyWorld world) {
        return new Scene().createScene(world)
            .setDraggable(true).setScalable(true)
            .setRenderSelect(true).setRenderFacing(false)
            .setShowHoverBlockTips(true).useCacheBuffer();
    }

    public static void attach(
        UIElement container,
        Supplier<MultiBlockDefinition> definition,
        IntSupplier length,
        BooleanSupplier mirrored
    ) {
        TrackedDummyWorld world = new TrackedDummyWorld();
        Scene scene = create(world);
        scene.layout(layout -> layout.widthPercent(100).heightPercent(100));
        container.addChild(scene);
        container.addEventListener(UIEvents.TICK, new UIEventListener() {
            private MultiBlockDefinition previousDefinition;
            private int previousLength = -1;
            private boolean previousMirrored;

            @Override
            public void handleEvent(UIEvent event) {
                for (UIElement parent = container; parent != null; parent = parent.getParent()) {
                    if (!parent.isDisplayed()) return;
                }
                MultiBlockDefinition current = definition.get();
                if (current == null) return;
                int repeats = Math.clamp(length.getAsInt(), current.getExpandMin(), current.getExpandMax());
                boolean mirror = mirrored.getAsBoolean();
                if (current == previousDefinition && repeats == previousLength && mirror == previousMirrored) return;
                previousDefinition = current;
                previousLength = repeats;
                previousMirrored = mirror;

                world.setBlockFilter(null);
                world.clear();
                var context = new MultiBlockContext.DummyDelegated(repeats, world) {
                    @Override
                    public void setBlock(BlockPos pos, BlockState state) {
                        // Keep the controller and every preview layer, mirroring by the build rule.
                        BlockPos transformed = MultiBlockRotation.localToWorld(pos,
                            MultiBlockRotation.CONTROLLER_ANCHOR, Direction.NORTH, mirror);
                        super.setBlock(transformed, MultiBlockRotation.rotateState(state, Direction.NORTH, mirror));
                    }
                };
                current.createLevel(context);
                scene.setRenderedCore(context.allBlocks());
                world.setBlockFilter(new HashSet<>(context.allBlocks())::contains);
            }
        });
    }
}
