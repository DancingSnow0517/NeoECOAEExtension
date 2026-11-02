package cn.dancingsnow.neoecoae.integration.xei;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Viewer-neutral ghost item targets for LDLib2 UI elements. LDLib2 26.1 only bridges JEI ghost ingredients, so
 * other recipe viewers (REI) read these targets. Accepting a target only sets configuration; it never moves items.
 * Client thread only; elements are held weakly and drop out when their UI is discarded.
 */
public final class GhostItemTargets {
    private static final Map<UIElement, Consumer<ItemStack>> TARGETS = new WeakHashMap<>();

    private GhostItemTargets() {}

    public static synchronized void register(UIElement element, Consumer<ItemStack> accept) {
        TARGETS.put(element, accept);
    }

    /** Displayed targets that belong to {@code screen}'s ModularUI; empty for any other screen. */
    public static synchronized List<Target> active(@Nullable Screen screen) {
        ModularUI ui = modularUI(screen);
        if (ui == null || TARGETS.isEmpty()) return List.of();
        List<Target> result = new ArrayList<>();
        for (var entry : TARGETS.entrySet()) {
            UIElement element = entry.getKey();
            if (element.getModularUI() != ui || !element.isDisplayed() || !element.isActiveInHierarchy()) continue;
            result.add(new Target(element.getPositionX(), element.getPositionY(),
                element.getSizeWidth(), element.getSizeHeight(), entry.getValue()));
        }
        return result;
    }

    @Nullable
    private static ModularUI modularUI(@Nullable Screen screen) {
        if (screen instanceof ModularUIContainerScreen containerScreen) return containerScreen.getMenu().getModularUI();
        if (screen instanceof ModularUIScreen uiScreen) return uiScreen.getModularUI();
        return null;
    }

    public record Target(float x, float y, float width, float height, Consumer<ItemStack> accept) {
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }
}
