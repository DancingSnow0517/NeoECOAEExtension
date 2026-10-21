package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.*;
import java.util.function.Consumer;
public class Scroller extends UIElement {
    public final Button headButton=new Button().noText(),tailButton=new Button().noText(),scrollBar=new Button().noText();
    public final UIElement scrollContainer=new UIElement();
    private float value,min=0,max=1,barSize=20,lastLength=-1;private Consumer<Float> changed;
    private final boolean vertical;
    public Scroller(){this(true);}
    public Scroller(boolean vertical){this.vertical=vertical;
        if(vertical){
            layout(l->l.width(8).heightPercent(100));
            scrollContainer.layout(l->l.flex(1).widthPercent(100));
            headButton.layout(l->l.height(8));tailButton.layout(l->l.height(8));
        }else{
            layout(l->l.flexDirection(FlexDirection.ROW).widthPercent(100).height(8));
            scrollContainer.layout(l->l.flex(1).heightPercent(100));
            headButton.layout(l->l.width(8).heightPercent(100));tailButton.layout(l->l.width(8).heightPercent(100));
        }
        addChildren(headButton,scrollContainer,tailButton);scrollContainer.addChild(scrollBar);
        headButton.setOnClick(e->setValue(value-.1f));tailButton.setOnClick(e->setValue(value+.1f));
        scrollBar.addEventListener(UIEvents.MOUSE_DOWN,e->{if(e.button==0&&getModularUI()!=null){getModularUI().getDragHandler().startDrag(value,null,scrollBar);e.stopPropagation();}});
        scrollBar.addEventListener(UIEvents.DRAG_SOURCE_UPDATE,e->{if(e.dragHandler==null||!(e.dragHandler.draggingObject instanceof Float initial))return;
            float distance=vertical?e.y-e.dragStartY:e.x-e.dragStartX;float length=vertical?scrollContainer.getContentHeight():scrollContainer.getContentWidth();
            setValue(initial+distance/Math.max(1,length*(1-barSize/100))*(max-min));});
    }
    public Scroller headButton(Consumer<Button> c){c.accept(headButton);return this;}
    public Scroller tailButton(Consumer<Button> c){c.accept(tailButton);return this;}
    public Scroller scrollBar(Consumer<Button> c){c.accept(scrollBar);return this;}
    public Scroller scrollContainer(Consumer<UIElement> c){c.accept(scrollContainer);return this;}
    public float getValue(){return value;}
    public float getMinValue(){return min;}
    public float getMaxValue(){return max;}
    public Scroller setValue(float v){float next=Math.max(min,Math.min(max,v));if(next!=value){value=next;positionBar();if(changed!=null)changed.accept(value);}return this;}
    public Scroller setScrollBarSize(float percent){float next=Math.max(0,Math.min(100,percent));float length=vertical?scrollContainer.getContentHeight():scrollContainer.getContentWidth();
        if(next!=barSize||length!=lastLength){barSize=next;lastLength=length;positionBar();}return this;}
    public Scroller setOnValueChanged(Consumer<Float> action){changed=action;return this;}
    private void positionBar(){float fraction=max==min?0:(value-min)/(max-min);
        scrollBar.layout(l->{l.positionType(TaffyPosition.ABSOLUTE);if(vertical)l.heightPercent(barSize).widthPercent(100).top(scrollContainer.getContentHeight()*(1-barSize/100)*fraction);
            else l.widthPercent(barSize).heightPercent(100).left(scrollContainer.getContentWidth()*(1-barSize/100)*fraction);});}
}
