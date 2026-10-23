package cn.dancingsnow.neoecoae.client.gui.workstation;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.workstation.LargeWorkstationUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public final class LargeWorkstationClientUI {
    private LargeWorkstationClientUI() {}

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> container
            && container.getMenu() instanceof ModularUIContainer menu) {
            ModularUI ui = ModularUI.from(menu);
            if (ui != null) LargeWorkstationUI.advanceFrame(ui);
        }
    }
}
