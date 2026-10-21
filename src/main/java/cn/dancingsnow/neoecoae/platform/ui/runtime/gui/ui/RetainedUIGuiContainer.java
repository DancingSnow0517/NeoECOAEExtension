package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui;

import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

/** Keeps a fixed source layout usable when it is taller than the current GUI viewport. */
public final class RetainedUIGuiContainer extends ModularUIGuiContainer {
    private float renderScale=1;

    public RetainedUIGuiContainer(com.lowdragmc.lowdraglib.gui.modular.ModularUI ui,int windowId){
        super(ui,windowId);
    }

    @Override public void init(){
        var root=(UIElement)modularUI.mainGroup;
        float[] bounds={0,0,root.getSizeWidth(),root.getSizeHeight()};
        includeVisibleBounds(root,root.getPositionX(),root.getPositionY(),bounds);
        float requiredWidth=root.getSizeWidth()+2*Math.max(-bounds[0],bounds[2]-root.getSizeWidth());
        float requiredHeight=root.getSizeHeight()+2*Math.max(-bounds[1],bounds[3]-root.getSizeHeight());
        renderScale=scaleToFit(width,height,requiredWidth,requiredHeight);
        width=(int)Math.ceil(width/renderScale);
        height=(int)Math.ceil(height/renderScale);
        super.init();
    }

    public static float scaleToFit(int width,int height,float contentWidth,float contentHeight){
        if(contentWidth<=width&&contentHeight<=height)return 1;
        return Math.max(0.01F,Math.min(1,Math.min(Math.max(1,width-8)/Math.max(1,contentWidth),
            Math.max(1,height-8)/Math.max(1,contentHeight))));
    }

    private static void includeVisibleBounds(UIElement element,float rootX,float rootY,float[] bounds){
        if(!element.isVisible()||!element.isDisplayed())return;
        bounds[0]=Math.min(bounds[0],element.getPositionX()-rootX);
        bounds[1]=Math.min(bounds[1],element.getPositionY()-rootY);
        bounds[2]=Math.max(bounds[2],element.getPositionX()-rootX+element.getSizeWidth());
        bounds[3]=Math.max(bounds[3],element.getPositionY()-rootY+element.getSizeHeight());
        if(element.isOverflowVisible())for(var child:element.getChildren())includeVisibleBounds(child,rootX,rootY,bounds);
    }

    public float getRenderScale(){return renderScale;}
    public double toLogical(double coordinate){return coordinate/renderScale;}
    public double toScreen(double coordinate){return coordinate*renderScale;}

    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTicks){
        graphics.pose().pushPose();
        try{
            graphics.pose().scale(renderScale,renderScale,1);
            super.render(graphics,(int)Math.round(toLogical(mouseX)),(int)Math.round(toLogical(mouseY)),partialTicks);
        }finally{graphics.flush();graphics.pose().popPose();}
    }

    @Override public boolean mouseClicked(double x,double y,int button){return super.mouseClicked(toLogical(x),toLogical(y),button);}
    @Override public boolean mouseReleased(double x,double y,int button){return super.mouseReleased(toLogical(x),toLogical(y),button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        return super.mouseDragged(toLogical(x),toLogical(y),button,toLogical(dx),toLogical(dy));
    }
    @Override public void mouseMoved(double x,double y){super.mouseMoved(toLogical(x),toLogical(y));}
    @Override public boolean mouseScrolled(double x,double y,double delta){return super.mouseScrolled(toLogical(x),toLogical(y),delta);}
    @Override public List<Rect2i> getGuiExtraAreas(){
        return super.getGuiExtraAreas().stream().map(area->new Rect2i(Math.round(area.getX()*renderScale),
            Math.round(area.getY()*renderScale),Math.round(area.getWidth()*renderScale),Math.round(area.getHeight()*renderScale))).toList();
    }
}
