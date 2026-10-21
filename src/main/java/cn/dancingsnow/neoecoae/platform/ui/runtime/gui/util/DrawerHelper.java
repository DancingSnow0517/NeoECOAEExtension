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
        com.lowdragmc.lowdraglib.gui.util.DrawerHelper.drawFluidForGui(graphics,com.lowdragmc.lowdraglib.side.fluid.FluidStack.create(fluid.getFluid(),fluid.getAmount(),fluid.getTag()),x,y,w,h);}
    public static void drawFluidForGui(GuiGraphics graphics,FluidStack fluid,float x,float y,float w,float h,int color){
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,(color>>>24)/255f);
        try{drawFluidForGui(graphics,fluid,x,y,w,h);}finally{com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);}}
}
