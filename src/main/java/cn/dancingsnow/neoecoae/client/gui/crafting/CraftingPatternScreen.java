package cn.dancingsnow.neoecoae.client.gui.crafting;

import cn.dancingsnow.neoecoae.client.gui.ECOModularContainerScreen;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingInterfaceUI;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CraftingPatternScreen extends ECOModularContainerScreen {
    public CraftingPatternScreen(ModularUIContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected boolean canHitItemSlot(ItemSlot itemSlot, float screenX, float screenY) {
        if (!super.canHitItemSlot(itemSlot, screenX, screenY)) return false;
        var preview = itemSlot.getFirstAncestorOfType(CraftingInterfaceUI.PatternPreview.class);
        if (preview == null) return true;
        var local = new Matrix4f(getMenu().getModularUI().getLastDrawPose()).invert()
            .transformPosition(new Vector3f(screenX, screenY, 0));
        return preview.containsPatternPoint(local.x, local.y);
    }
}
