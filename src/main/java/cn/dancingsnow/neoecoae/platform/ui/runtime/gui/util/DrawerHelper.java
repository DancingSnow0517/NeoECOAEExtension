package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;
import java.util.List;
public final class DrawerHelper {
    private DrawerHelper(){}
    public static List<Component> getItemToolTip(ItemStack stack){return com.lowdragmc.lowdraglib.gui.util.DrawerHelper.getItemToolTip(stack);}
    public static void drawItemStack(GuiGraphics graphics,ItemStack stack,int x,int y,int color,String count){com.lowdragmc.lowdraglib.gui.util.DrawerHelper.drawItemStack(graphics,stack,x,y,color,count);}
    public static void drawStringFixedCorner(GuiGraphics graphics,String text,float x,float y,int color,boolean shadow,float scale){com.lowdragmc.lowdraglib.gui.util.DrawerHelper.drawStringFixedCorner(graphics,text,x,y,color,shadow,scale);}
    public static void drawFluidForGui(GuiGraphics graphics,FluidStack fluid,float x,float y,float w,float h){
        if(fluid.isEmpty()||w<=0||h<=0)return;
        var contents=com.lowdragmc.lowdraglib.side.fluid.FluidStack.create(fluid.getFluid(),fluid.getAmount(),fluid.getTag());
        int width=Math.max(1,Math.round(w)),height=Math.max(1,Math.round(h));
        var pose=graphics.pose();pose.pushPose();
        try{pose.translate(x,y,0);pose.scale(w/width,h/height,1);
            com.lowdragmc.lowdraglib.gui.util.DrawerHelper.drawFluidForGui(graphics,contents,contents.getAmount(),0,0,width,height);
        }finally{pose.popPose();}}
    public static void drawFluidForGui(GuiGraphics graphics,FluidStack fluid,float x,float y,float w,float h,int color){
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,(color>>>24)/255f);
        try{drawFluidForGui(graphics,fluid,x,y,w,h);}finally{com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);}}
}
