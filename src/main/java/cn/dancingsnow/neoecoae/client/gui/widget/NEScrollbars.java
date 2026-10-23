package cn.dancingsnow.neoecoae.client.gui.widget;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.style.Blitter;
import net.minecraft.resources.ResourceLocation;
public final class NEScrollbars {
    private static final ResourceLocation ATLAS=new ResourceLocation("neoecoae","textures/gui/vendor/ae2/scrollbars.png");
    public static final Scrollbar.Style BIG=Scrollbar.Style.create(ATLAS,12,15,0,0,12,0);
    public static final Scrollbar.Style SMALL=Scrollbar.Style.create(ATLAS,7,15,24,0,31,0);
    public static Blitter smallHandle(boolean enabled) {
        return cn.dancingsnow.neoecoae.client.gui.workstation.WorkstationInterfaceArtwork.scrollbar(enabled);
    }
    private NEScrollbars(){}
}
