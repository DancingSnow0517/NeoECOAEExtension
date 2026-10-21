package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.*;
import java.util.function.Consumer;
public class ScrollerView extends UIElement {
    public final UIElement viewPort=new UIElement().setOverflowVisible(false),viewContainer=new UIElement();
    public final Scroller verticalScroller=new Scroller(true),horizontalScroller=new Scroller(false);
    public static class ScrollerStyle {
        ScrollerMode mode=ScrollerMode.BOTH;ScrollDisplay horizontal=ScrollDisplay.AUTO,vertical=ScrollDisplay.AUTO;
        public ScrollerStyle mode(ScrollerMode v){mode=v;return this;}
        public ScrollerStyle horizontalScrollDisplay(ScrollDisplay v){horizontal=v;return this;}
        public ScrollerStyle verticalScrollDisplay(ScrollDisplay v){vertical=v;return this;}
    }
    private final ScrollerStyle scrollerStyle=new ScrollerStyle();
    public ScrollerView(){
        viewPort.layout(l->l.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).right(0).bottom(0));
        viewContainer.layout(l->l.positionType(TaffyPosition.ABSOLUTE).left(0).top(0));viewPort.addChild(viewContainer);
        addChildren(viewPort,verticalScroller,horizontalScroller);horizontalScroller.setDisplay(false);
        verticalScroller.setDisplay(false);
        verticalScroller.layout(l->l.positionType(TaffyPosition.ABSOLUTE).right(0).top(0).heightAuto().bottom(0));
        horizontalScroller.layout(l->l.positionType(TaffyPosition.ABSOLUTE).left(0).bottom(0).widthAuto().height(8).right(0));
        verticalScroller.setOnValueChanged(v->updateOffset());horizontalScroller.setOnValueChanged(v->updateOffset());
        addEventListener(UIEvents.MOUSE_WHEEL,e->{if(viewContainer.getSizeHeight()>viewPort.getContentHeight()){
            verticalScroller.setValue(verticalScroller.getValue()-e.deltaY*18/Math.max(1,viewContainer.getSizeHeight()-viewPort.getContentHeight()));e.stopPropagation();e.hasHandler=true;}});
        addEventListener(UIEvents.TICK,e->refresh());
    }
    public ScrollerView scrollerStyle(Consumer<ScrollerStyle> action){action.accept(scrollerStyle);
        if(scrollerStyle.mode==ScrollerMode.VERTICAL)viewContainer.layout(l->l.widthPercent(100).heightAuto());
        else if(scrollerStyle.mode==ScrollerMode.HORIZONTAL)viewContainer.layout(l->l.widthAuto().heightPercent(100));
        else viewContainer.layout(l->l.widthAuto().heightAuto());return this;}
    public ScrollerView viewPort(Consumer<UIElement> action){action.accept(viewPort);return this;}
    public ScrollerView viewContainer(Consumer<UIElement> action){action.accept(viewContainer);return this;}
    public ScrollerView verticalScroller(Consumer<Scroller> action){action.accept(verticalScroller);return this;}
    public ScrollerView horizontalScroller(Consumer<Scroller> action){action.accept(horizontalScroller);return this;}
    public ScrollerView addScrollViewChild(UIElement element){viewContainer.addChild(element);return this;}
    public ScrollerView clearAllScrollViewChildren(){viewContainer.clearAllChildren();return this;}
    private void refresh(){
        boolean vertical=scrollerStyle.mode!=ScrollerMode.HORIZONTAL&&scrollerStyle.vertical!=ScrollDisplay.NEVER&&(scrollerStyle.vertical==ScrollDisplay.ALWAYS||viewContainer.getSizeHeight()>viewPort.getContentHeight());
        boolean changed=verticalScroller.isDisplay()!=vertical;
        if(changed)verticalScroller.setDisplay(vertical);
        boolean horizontal=scrollerStyle.mode!=ScrollerMode.VERTICAL&&scrollerStyle.horizontal!=ScrollDisplay.NEVER&&(scrollerStyle.horizontal==ScrollDisplay.ALWAYS||viewContainer.getSizeWidth()>viewPort.getContentWidth());
        if(horizontalScroller.isDisplay()!=horizontal){horizontalScroller.setDisplay(horizontal);changed=true;}
        if(changed){viewPort.layout(l->l.right(vertical?verticalScroller.getSizeWidth():0).bottom(horizontal?8:0));
            verticalScroller.layout(l->l.bottom(horizontal?8:0));horizontalScroller.layout(l->l.right(vertical?verticalScroller.getSizeWidth():0));}
        verticalScroller.setScrollBarSize(viewContainer.getSizeHeight()<=0?100:Math.min(100,viewPort.getContentHeight()/viewContainer.getSizeHeight()*100));
        horizontalScroller.setScrollBarSize(viewContainer.getSizeWidth()<=0?100:Math.min(100,viewPort.getContentWidth()/viewContainer.getSizeWidth()*100));
        updateOffset();
    }
    private void updateOffset(){float y=-verticalScroller.getValue()*Math.max(0,viewContainer.getSizeHeight()-viewPort.getContentHeight());
        float x=-horizontalScroller.getValue()*Math.max(0,viewContainer.getSizeWidth()-viewPort.getContentWidth());
        viewContainer.setSelfPosition(Math.round(viewPort.getLayoutStyle().paddingLeft+x),Math.round(viewPort.getLayoutStyle().paddingTop+y));}
    @Override public void onLayoutApplied(){refresh();}
}
