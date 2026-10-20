package cn.dancingsnow.neoecoae.client.gui.workstation;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.workstation.LargeWorkstationUI;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolder;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public final class LargeWorkstationClientUI {
    private LargeWorkstationClientUI() {}

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> container
            && container.getMenu() instanceof IModularUIHolder holder
            && holder.getModularUI() != null) {
            LargeWorkstationUI.advanceFrame(holder.getModularUI());
        }
    }
}
