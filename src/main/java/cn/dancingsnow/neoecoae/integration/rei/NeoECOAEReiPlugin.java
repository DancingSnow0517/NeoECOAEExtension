package cn.dancingsnow.neoecoae.integration.rei;

import cn.dancingsnow.neoecoae.integration.xei.GhostItemTargets;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.stream.Stream;

/**
 * REI client plugin. Discovered by REI's annotation scan only when REI is installed, so no other class links it.
 * Restores ghost item dragging into LDLib2 filter slots (LDLib2 26.1 ships only a JEI bridge).
 */
@REIPluginClient
public class NeoECOAEReiPlugin implements REIClientPlugin {
    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.registerDraggableStackVisitor(new GhostItemVisitor());
    }

    private static final class GhostItemVisitor implements DraggableStackVisitor<Screen> {
        @Override
        public <R extends Screen> boolean isHandingScreen(R screen) {
            return !GhostItemTargets.active(screen).isEmpty();
        }

        @Override
        public Stream<BoundsProvider> getDraggableAcceptingBounds(DraggingContext<Screen> context, DraggableStack stack) {
            if (item(stack).isEmpty()) return Stream.empty();
            return GhostItemTargets.active(context.getScreen()).stream()
                .map(target -> BoundsProvider.ofShape(Shapes.box(target.x(), target.y(), 0,
                    target.x() + target.width(), target.y() + target.height(), 1)));
        }

        @Override
        public DraggedAcceptorResult acceptDraggedStack(DraggingContext<Screen> context, DraggableStack stack) {
            ItemStack item = item(stack);
            if (item.isEmpty()) return DraggedAcceptorResult.PASS;
            var position = context.getCurrentPosition();
            for (var target : GhostItemTargets.active(context.getScreen())) {
                if (target.contains(position.x, position.y)) {
                    target.accept().accept(item.copyWithCount(1));
                    return DraggedAcceptorResult.CONSUMED;
                }
            }
            return DraggedAcceptorResult.PASS;
        }

        private static ItemStack item(DraggableStack stack) {
            var entry = stack.getStack();
            if (entry.getType() != VanillaEntryTypes.ITEM) return ItemStack.EMPTY;
            ItemStack item = entry.castValue();
            return item == null ? ItemStack.EMPTY : item;
        }
    }
}
