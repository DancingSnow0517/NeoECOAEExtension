package cn.dancingsnow.neoecoae.client.gui;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Resolves LDLib2 item slots at the coordinates supplied to the native container screen. */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public class ECOModularContainerScreen extends ModularUIContainerScreen {

    public ECOModularContainerScreen(ModularUIContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof ModularUIContainerScreen screen
            && screen.getClass() == ModularUIContainerScreen.class
            && screen.getMenu().getModularUI() instanceof ECOModularUI) {
            var menu = screen.getMenu();
            event.setNewScreen(new ECOModularContainerScreen(menu, menu.inventory, screen.getTitle()));
        }
    }

    public Slot hitModularItemSlot(double mouseX, double mouseY) {
        float screenX = (float) mouseX;
        float screenY = (float) mouseY;
        var hovered = menu.getModularUI().hitTestAtScreen(screenX, screenY);
        return hovered instanceof ItemSlot itemSlot && canHitItemSlot(itemSlot, screenX, screenY)
            ? itemSlot.getSlot() : null;
    }

    protected boolean canHitItemSlot(ItemSlot itemSlot, float screenX, float screenY) {
        return itemSlot.isActive() && menu.asModularUIHolderMenu().isItemSlot(itemSlot.getSlot());
    }

}
