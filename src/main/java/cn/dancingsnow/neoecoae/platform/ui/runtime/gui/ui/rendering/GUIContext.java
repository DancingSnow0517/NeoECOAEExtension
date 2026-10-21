package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;

public final class GUIContext {
    public final GuiGraphics graphics;
    public final com.mojang.blaze3d.vertex.PoseStack pose;
    public int elementColor=-1;
    public final float mouseX, mouseY, partialTick;
    public final float partialTicks;
    private final List<Consumer<GUIContext>> deferred;
    private static final ThreadLocal<List<Consumer<GUIContext>>> PASS=new ThreadLocal<>();
    public static boolean beginPass(){if(PASS.get()!=null)return false;PASS.set(new ArrayList<>());return true;}
    public static void endPass(boolean owner,GUIContext context){if(!owner)return;var renderers=PASS.get();PASS.remove();
        if(renderers!=null)for(var renderer:List.copyOf(renderers))renderer.accept(context);}
    public GUIContext(GuiGraphics graphics, float mouseX, float mouseY, float partialTicks) {
        this(graphics, mouseX, mouseY, partialTicks, PASS.get()==null?new ArrayList<>():PASS.get());
    }
    private GUIContext(GuiGraphics graphics, float mouseX, float mouseY, float partialTicks,
            List<Consumer<GUIContext>> deferred) {
        this.graphics = graphics;this.pose=graphics.pose(); this.mouseX = mouseX; this.mouseY = mouseY;
        this.partialTick = this.partialTicks = partialTicks; this.deferred = deferred;
    }
    public void drawTexture(IGuiTexture texture, float x, float y, float width, float height) {
        if (texture != null) texture.draw(this, x, y, width, height);
    }
    public void postRendering(Consumer<GUIContext> renderer) {
        var position=new org.joml.Matrix4f(pose.last().pose());var normal=new org.joml.Matrix3f(pose.last().normal());
        deferred.add(ignored->{pose.pushPose();try{pose.last().pose().set(position);pose.last().normal().set(normal);renderer.accept(this);}finally{pose.popPose();}});
    }
    public void enableScissor(float x,float y,float width,float height){graphics.enableScissor(Math.round(x),Math.round(y),Math.round(x+width),Math.round(y+height));}
    public void disableScissor(){graphics.disableScissor();}
    public void flushPostRendering() {
        if(PASS.get()!=null)return;
        var pending = List.copyOf(deferred); deferred.clear(); pending.forEach(renderer -> renderer.accept(this));
    }
}
