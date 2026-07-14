package cn.dancingsnow.neoecoae.integration.emi;

import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.screens.Screen;

public final class ECOEmiScreenCompat {
    private static boolean registered;

    private ECOEmiScreenCompat() {}

    public static void register(EmiRegistry registry) {
        if (registered) {
            return;
        }
        registered = true;
        registry.addExclusionArea(ModularUIGuiContainer.class, (screen, consumer) -> {
            if (shouldHideNativeHostEmi(screen)) {
                return;
            }
            screen.getGuiExtraAreas()
                    .forEach(area ->
                            consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight())));
        });
    }

    public static boolean shouldHideNativeHostEmi(Screen screen) {
        return screen instanceof ModularUIGuiContainer modularScreen && isNativeHostScreen(modularScreen);
    }

    private static boolean isNativeHostScreen(ModularUIGuiContainer screen) {
        ModularUI ui = screen.modularUI;
        if (ui == null) {
            return false;
        }
        IUIHolder holder = ui.holder;
        return holder instanceof ECOCraftingSystemBlockEntity
                || holder instanceof ECOComputationSystemBlockEntity
                || holder instanceof ECOStorageSystemBlockEntity;
    }
}
