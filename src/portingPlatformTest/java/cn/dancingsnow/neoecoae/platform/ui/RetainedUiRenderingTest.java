package cn.dancingsnow.neoecoae.platform.ui;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Label;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetainedUiRenderingTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap(){cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap.initialize();}
    private static GuiGraphics graphics() {
        var graphics=mock(GuiGraphics.class);
        when(graphics.pose()).thenReturn(new PoseStack());
        return graphics;
    }

    @Test void customContentTransformIncludesTheAdditionalBackgroundAndDescendants() {
        var graphics=graphics();
        var positions=new ArrayList<Float>();
        var root=new UIElement(){
            @Override public void drawContents(GUIContext context){
                context.pose.pushPose();context.pose.translate(0,5,0);
                try{super.drawContents(context);}finally{context.pose.popPose();}
            }
            @Override public void drawBackgroundAdditional(GUIContext context){positions.add(context.pose.last().pose().m31());}
        };
        root.setOverflowVisible(false).layout(l->l.width(20).height(20));
        var child=new UIElement(){
            @Override public void drawBackgroundAdditional(GUIContext context){positions.add(context.pose.last().pose().m31());}
        };
        child.layout(l->l.width(10).height(10));root.addChild(child);root.arrange();
        root.drawInBackground(graphics,0,0,0);
        assertEquals(List.of(5F,5F),positions);
        assertEquals(0,graphics.pose().last().pose().m31());
        verify(graphics).enableScissor(0,5,20,25);
    }

    @Test void customLabelReplacesTheDefaultTextRenderer() {
        var graphics=graphics();var calls=new int[1];
        var label=new Label(){
            @Override public void drawBackgroundAdditional(GUIContext context){calls[0]++;}
        };
        label.layout(l->l.width(40).height(10));label.arrange();
        // A native font/client is deliberately absent: the custom fitted renderer owns the text.
        assertDoesNotThrow(()->label.drawInBackground(graphics,0,0,0));
        assertEquals(1,calls[0]);
    }

    @Test void zOrderAgreesWithHitTestingAndDoesNotLeakIntoLaterSiblings() {
        var graphics=graphics();var order=new ArrayList<String>();
        var root=new UIElement().layout(l->l.width(20).height(20));
        var front=new UIElement(){
            @Override public void drawBackgroundAdditional(GUIContext context){order.add("front:"+context.pose.last().pose().m32());}
        };
        front.layout(l->l.positionType(TaffyPosition.ABSOLUTE).width(20).height(20));front.style(s->s.zIndex(3));
        var back=new UIElement(){
            @Override public void drawBackgroundAdditional(GUIContext context){order.add("back:"+context.pose.last().pose().m32());}
        };
        back.layout(l->l.positionType(TaffyPosition.ABSOLUTE).width(20).height(20));
        root.addChildren(front,back);root.arrange();root.drawInBackground(graphics,0,0,0);
        assertEquals(List.of("back:0.0","front:3.0"),order);
        assertSame(front,root.hitTest(5,5));
        assertEquals(0,graphics.pose().last().pose().m32());
    }

    @Test void deferredTextureKeepsItsPoseAndClipThroughTextureGroups() {
        var graphics=graphics();graphics.pose().scale(0.5F,0.5F,1);
        boolean owner=GUIContext.beginPass();var context=new GUIContext(graphics,0,0,0);
        var calls=new ArrayList<Float>();
        try{
            context.enableScissor(10,20,40,30);
            IGuiTexture texture=new IGuiTexture(){
                @Override public void draw(GuiGraphics g,float mx,float my,float x,float y,float w,float h,float tick){fail("context was discarded");}
                @Override public void draw(GUIContext c,float x,float y,float w,float h){
                    c.postRendering(post->calls.add(post.pose.last().pose().m00()));
                }
            };
            IGuiTexture.group(texture).draw(context,0,0,16,16);
            context.disableScissor();assertTrue(calls.isEmpty());
        }finally{GUIContext.endPass(owner,context);}
        assertEquals(List.of(0.5F),calls);
        verify(graphics,times(2)).enableScissor(5,10,25,25);
        verify(graphics,times(2)).disableScissor();
    }

    @Test void smallWindowsFitTheWholeLayoutAndRoomyWindowsKeepNativePixels(){
        float scale=cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.RetainedUIGuiContainer.scaleToFit(427,240,176,253);
        assertTrue(scale<1);assertTrue(253*scale<=232);
        assertEquals(1,cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.RetainedUIGuiContainer.scaleToFit(960,540,176,253));
    }

    @Test void retainedDefaultOverflowStaysVisibleAndExplicitViewportsStayClipped(){
        var element=new UIElement();assertTrue(element.isOverflowVisible());
        element.setOverflowVisible(false);assertFalse(element.isOverflowVisible());
        assertFalse(new cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ScrollerView().viewPort.isOverflowVisible());
    }
}
