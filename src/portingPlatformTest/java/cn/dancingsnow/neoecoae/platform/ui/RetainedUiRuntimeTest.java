package cn.dancingsnow.neoecoae.platform.ui;

import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.ScrollerMode;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.style.StylesheetManager;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.*;
import com.lowdragmc.lowdraglib.gui.ingredient.IGhostIngredientTarget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class RetainedUiRuntimeTest {
    @BeforeAll static void bootstrap(){PlatformTestBootstrap.initialize();}

    @Test void absolutePercentagesUseThePaddedContentBox(){
        var root=new UIElement().layout(l->l.width(200).height(100).paddingAll(10));
        var child=new UIElement().layout(l->l.positionType(TaffyPosition.ABSOLUTE).widthPercent(50).heightPercent(50).leftPercent(25).topPercent(25));
        root.addChild(child);root.arrange();
        assertEquals(90,child.getSizeWidth());assertEquals(40,child.getSizeHeight());
        assertEquals(55,child.getPositionX());assertEquals(30,child.getPositionY());
    }

    @Test void gridKeepsDirectionalOutputTogglesInTheirAssignedCells(){
        var grid=new UIElement().layout(l->l.width(70).height(70).display(TaffyDisplay.GRID)
            .gridTemplateColumns("1fr 1fr 1fr").gridTemplateRows("1fr 1fr 1fr").gapAll(5));
        var top=new UIElement().layout(l->l.gridColumn("2").gridRow("1"));
        var back=new UIElement().layout(l->l.gridColumn("3").gridRow("3"));
        grid.addChildren(top,back);grid.arrange();
        assertEquals(25,top.getPositionX());assertEquals(0,top.getPositionY());
        assertEquals(50,back.getPositionX());assertEquals(50,back.getPositionY());
        assertEquals(20,back.getSizeWidth());assertEquals(20,back.getSizeHeight());
    }

    @Test void clickCapturesThenBubblesOnceAndCanPreventTheButtonAction(){
        var root=new UIElement().layout(l->l.width(40).height(20));
        Button button=new Button().noText().layout(l->l.width(40).height(20));
        AtomicInteger actions=new AtomicInteger();List<String> order=new ArrayList<>();
        button.setOnClick(event->actions.incrementAndGet());
        root.addEventListener(UIEvents.MOUSE_DOWN,event->order.add("capture"),true);
        button.addEventListener(UIEvents.MOUSE_DOWN,event->order.add("button"));
        root.addChild(button);root.arrange();root.mouseClicked(5,5,0);root.mouseReleased(5,5,0);
        assertEquals(List.of("capture","button"),order);assertEquals(1,actions.get());
        root.addEventListener(UIEvents.MOUSE_DOWN,event->event.stopImmediatePropagation(),true);
        root.mouseClicked(5,5,0);root.mouseReleased(5,5,0);assertEquals(1,actions.get());
    }

    @Test void leavingAnElementClearsHoverAndChildTicksDoNotRetickAncestors(){
        var root=new UIElement().layout(l->l.width(20).height(20));
        var child=new UIElement().layout(l->l.width(20).height(20));root.addChild(child);root.arrange();
        AtomicInteger leave=new AtomicInteger(),rootTicks=new AtomicInteger(),childTicks=new AtomicInteger();
        child.addEventListener(UIEvents.MOUSE_LEAVE,e->leave.incrementAndGet());
        root.addEventListener(UIEvents.TICK,e->rootTicks.incrementAndGet());child.addEventListener(UIEvents.TICK,e->childTicks.incrementAndGet());
        root.mouseMoved(2,2);root.mouseMoved(30,30);root.updateScreen();
        assertEquals(1,leave.get());assertFalse(child.isHover());assertEquals(1,rootTicks.get());assertEquals(1,childTicks.get());
    }

    @Test void nativeSlotsRespectHiddenAncestorsAndKeepDisplaySlotsNonExtractable(){
        var root=new UIElement().layout(l->l.width(18).height(18));var item=new ItemSlot();root.addChild(item);root.arrange();
        item.setValue(new ItemStack(Items.DIAMOND,8),false);
        var nativeSlot=(SlotWidget)item.widgets.get(0);
        assertTrue(nativeSlot.getHandler().isActive());assertFalse(nativeSlot.getHandler().mayPlace(new ItemStack(Items.DIRT)));
        assertTrue(nativeSlot.getHandler().remove(1).isEmpty());assertEquals(8,item.getValue().getCount());
        root.setDisplay(false);assertFalse(nativeSlot.getHandler().isActive());
    }

    @Test void ghostDropEditsOnlyTheFilterDisplayAndNotTheIngredientStack(){
        var slot=new ItemSlot().xeiPhantom();slot.arrange();AtomicInteger edits=new AtomicInteger();slot.registerValueListener(stack->edits.incrementAndGet());
        var source=new ItemStack(Items.EMERALD,7);
        var target=((IGhostIngredientTarget)slot.widgets.get(0)).getPhantomTargets(source).get(0);
        target.accept(source);assertEquals(1,edits.get());assertEquals(7,source.getCount());assertEquals(7,slot.getValue().getCount());
        slot.getValue().shrink(2);assertEquals(7,source.getCount());
    }

    @Test void stylesheetCascadesSpecificRulesAndPreservesExplicitZeroPadding(){
        var sheet=StylesheetManager.parse(".panel { background: #111; padding-all: 5; } .special { background: #222; } button:host { padding-all: 3; } button.__disabled__ .__button_text__ {text-color: #666;}");
        var root=new UIElement().addClasses("panel","special");
        Button button=new Button().layout(l->l.paddingAll(0));root.addChild(button);button.setActive(false);sheet.apply(root);
        assertEquals(0,button.getLayoutStyle().paddingLeft);assertEquals(5,root.getLayoutStyle().paddingLeft);
        assertEquals(0xff666666,button.text.getTextStyle().color);
        button.setActive(true);sheet.apply(root);assertEquals(0xff404040,button.text.getTextStyle().color);
    }

    @Test void verticalScrollKeepsChildrenAtTheirRealHeightAndMovesTheContent(){
        var view=new ScrollerView().scrollerStyle(s->s.mode(ScrollerMode.VERTICAL));view.layout(l->l.width(100).height(40));
        for(int i=0;i<6;i++)view.addScrollViewChild(new UIElement().layout(l->l.height(20)));
        view.arrange();assertTrue(view.verticalScroller.isDisplayed());assertEquals(120,view.viewContainer.getSizeHeight());
        view.verticalScroller.setValue(1);assertEquals(-80,view.viewContainer.getSelfPositionY());
    }

    @Test void horizontalMaterialsRemainFullWidthAndCanScroll(){
        var view=new ScrollerView().scrollerStyle(s->s.mode(ScrollerMode.HORIZONTAL));view.layout(l->l.width(50).height(26));
        view.viewContainer(c->c.layout(l->l.flexDirection(FlexDirection.ROW)));
        for(int i=0;i<5;i++)view.addScrollViewChild(new UIElement().layout(l->l.width(18).height(18)));
        view.arrange();assertEquals(90,view.viewContainer.getSizeWidth());assertTrue(view.horizontalScroller.isDisplayed());
        view.horizontalScroller.setValue(1);assertEquals(-40,view.viewContainer.getSelfPositionX());
    }

    @Test void clippedAndCoveredElementsDoNotBecomeHovered(){
        var root=new UIElement().layout(l->l.width(40).height(20));
        var clipped=new UIElement().layout(l->l.positionType(TaffyPosition.ABSOLUTE).left(50).width(20).height(20));
        var back=new UIElement().layout(l->l.positionType(TaffyPosition.ABSOLUTE).width(20).height(20));
        var front=new UIElement().layout(l->l.positionType(TaffyPosition.ABSOLUTE).width(20).height(20));
        root.addChildren(clipped,back,front);root.arrange();
        root.mouseMoved(55,5);assertFalse(clipped.isHover());assertFalse(root.isHover());
        root.mouseMoved(5,5);assertTrue(front.isHover());assertTrue(root.isHover());assertFalse(back.isHover());
        root.setDisplay(false);root.mouseMoved(5,5);assertFalse(front.isHover());
    }

    @Test void horizontalScrollbarKeepsItsArrowsAtOppositeEnds(){
        var scroller=new Scroller(false);scroller.layout(l->l.width(100).height(8));scroller.arrange();
        assertEquals(8,scroller.headButton.getSizeWidth());assertEquals(8,scroller.headButton.getSizeHeight());
        assertEquals(8,scroller.scrollContainer.getPositionX());assertEquals(84,scroller.scrollContainer.getSizeWidth());
        assertEquals(92,scroller.tailButton.getPositionX());assertEquals(8,scroller.tailButton.getSizeWidth());
    }

    @Test void fluidAmountLabelTracksItsTankAndUsesTheSourceAlignment(){
        var tank=new net.minecraftforge.fluids.capability.templates.FluidTank(4000);
        var slot=new FluidSlot().bind(tank,0);slot.layout(l->l.width(18).height(40).paddingAll(1));slot.arrange();
        tank.setFluid(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000));
        slot.updateScreen();assertEquals("1B",slot.amountLabel.getValue().getString());
        assertEquals(cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.Vertical.BOTTOM,slot.amountLabel.getTextStyle().vertical);
        assertEquals(cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.Horizontal.RIGHT,slot.amountLabel.getTextStyle().horizontal);
        assertEquals(4.5f,slot.amountLabel.getTextStyle().size);
        tank.setFluid(net.minecraftforge.fluids.FluidStack.EMPTY);slot.updateScreen();assertEquals("",slot.amountLabel.getValue().getString());
        assertThrows(IllegalArgumentException.class,()->slot.bind(tank,1));
    }

    @Test void resourceReloadUsesTheResourcePackStylesheetAndInvalidatesTheCache(){
        var manager=new StylesheetManager();var id=new net.minecraft.resources.ResourceLocation("test","theme");
        java.util.concurrent.atomic.AtomicReference<String> path=new java.util.concurrent.atomic.AtomicReference<>();
        manager.setResourceOpener(location->{path.set(location.toString());return new java.io.ByteArrayInputStream(".panel { padding-all: 7; }".getBytes(java.nio.charset.StandardCharsets.UTF_8));});
        var root=new UIElement().addClass("panel");var before=manager.getStylesheetSafe(id);before.apply(root);
        assertEquals("test:lss/theme.lss",path.get());assertEquals(7,root.getLayoutStyle().paddingLeft);
        manager.setResourceOpener(location->new java.io.ByteArrayInputStream(".panel { padding-all: 2; }".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var after=manager.getStylesheetSafe(id);assertNotSame(before,after);after.apply(root);assertEquals(2,root.getLayoutStyle().paddingLeft);
    }
}
