package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;

public final class GUIContext {
    public final GuiGraphics graphics;
    public final com.mojang.blaze3d.vertex.PoseStack pose;
    public int elementColor=-1;
    public final float mouseX, mouseY, partialTick;
    public final float partialTicks;
    private static final class RenderPass {
        final List<Consumer<GUIContext>> deferred=new ArrayList<>();
        final Deque<ScreenRectangle> clips=new ArrayDeque<>();
    }
    private final RenderPass pass;
    private static final ThreadLocal<RenderPass> PASS=new ThreadLocal<>();
    public static boolean beginPass(){if(PASS.get()!=null)return false;PASS.set(new RenderPass());return true;}
    public static void endPass(boolean owner,GUIContext context){if(!owner)return;
        try{context.flushDeferred();}finally{PASS.remove();}}
    public GUIContext(GuiGraphics graphics, float mouseX, float mouseY, float partialTicks) {
        this(graphics, mouseX, mouseY, partialTicks, PASS.get()==null?new RenderPass():PASS.get());
    }
    private GUIContext(GuiGraphics graphics, float mouseX, float mouseY, float partialTicks,
            RenderPass pass) {
        this.graphics = graphics;this.pose=graphics.pose(); this.mouseX = mouseX; this.mouseY = mouseY;
        this.partialTick = this.partialTicks = partialTicks; this.pass = pass;
    }
    public void drawTexture(IGuiTexture texture, float x, float y, float width, float height) {
        if (texture != null) texture.draw(this, x, y, width, height);
    }
    public void postRendering(Consumer<GUIContext> renderer) {
        var position=new org.joml.Matrix4f(pose.last().pose());var normal=new org.joml.Matrix3f(pose.last().normal());
        var clip=pass.clips.peek();
        pass.deferred.add(ignored->{pose.pushPose();graphics.flush();
            if(clip!=null)graphics.enableScissor(clip.left(),clip.top(),clip.right(),clip.bottom());
            try{pose.last().pose().set(position);pose.last().normal().set(normal);renderer.accept(this);}
            finally{graphics.flush();if(clip!=null)graphics.disableScissor();pose.popPose();}});
    }
    /** Forge 1.20 scissors are screen coordinates; transform them alongside the GUI pose. */
    public void enableScissor(float x,float y,float width,float height){
        var matrix=pose.last().pose();
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        for(int corner=0;corner<4;corner++){
            var point=matrix.transformPosition(x+((corner&1)==0?0:width),y+((corner&2)==0?0:height),0,new org.joml.Vector3f());
            minX=Math.min(minX,point.x);minY=Math.min(minY,point.y);maxX=Math.max(maxX,point.x);maxY=Math.max(maxY,point.y);
        }
        int left=(int)Math.floor(minX),top=(int)Math.floor(minY),right=(int)Math.ceil(maxX),bottom=(int)Math.ceil(maxY);
        var clip=new ScreenRectangle(left,top,Math.max(0,right-left),Math.max(0,bottom-top));
        if(!pass.clips.isEmpty()){
            var intersection=clip.intersection(pass.clips.peek());
            clip=intersection==null?new ScreenRectangle(0,0,0,0):intersection;
        }
        pass.clips.push(clip);graphics.flush();graphics.enableScissor(clip.left(),clip.top(),clip.right(),clip.bottom());
    }
    public void disableScissor(){graphics.flush();graphics.disableScissor();pass.clips.pop();}
    public void flushPostRendering() {
        if(PASS.get()!=null)return;
        flushDeferred();
    }
    private void flushDeferred(){
        var pending=List.copyOf(pass.deferred);pass.deferred.clear();pending.forEach(renderer->renderer.accept(this));
    }
}
