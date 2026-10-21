package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
public class ItemStackTexture implements IGuiTexture {
    private final ItemStack[] stacks;
    public ItemStackTexture(ItemStack... stacks){this.stacks=stacks;}
    @Override public void draw(GuiGraphics g,float mx,float my,float x,float y,float w,float h,float tick){
        if(stacks.length==0)return;var stack=stacks[(int)(net.minecraft.Util.getMillis()/1000%stacks.length)];
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(w/16,h/16,1);g.renderItem(stack,0,0);g.pose().popPose();
    }
}
