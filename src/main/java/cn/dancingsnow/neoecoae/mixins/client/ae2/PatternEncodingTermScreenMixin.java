package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.items.PatternEncodingTermMenu;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import cn.dancingsnow.neoecoae.gui.widget.UploadButton;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = PatternEncodingTermScreen.class, remap = false)
public class PatternEncodingTermScreenMixin<C extends PatternEncodingTermMenu> extends MEStorageScreen<C> {
    @Unique
    private UploadButton neoecoae$uploadButton;

    public PatternEncodingTermScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void init() {
        super.init();
        var bounds = new Rect2i(leftPos, topPos, imageWidth, imageHeight);
        var lastModeTabStyle = getStyle().getWidget("modeTabButton3");
        var lastModeTab = lastModeTabStyle.resolve(bounds);
        // Place the upload button fifth, directly below the four encoding tabs.
        neoecoae$uploadButton = addRenderableWidget(new UploadButton(
            lastModeTab.getX(),
            lastModeTab.getY() + lastModeTabStyle.getHeight(),
            b -> ((PatternEncodingTermMenuExtension)this.getMenu()).neoecoae$uploadPattern()
        ));
    }

    @Override
    public List<Rect2i> getExclusionZones() {
        var zones = new ArrayList<>(super.getExclusionZones());
        if (neoecoae$uploadButton != null && neoecoae$uploadButton.visible) {
            zones.add(new Rect2i(neoecoae$uploadButton.getX(), neoecoae$uploadButton.getY(),
                neoecoae$uploadButton.getWidth(), neoecoae$uploadButton.getHeight()));
        }
        return zones;
    }
}
