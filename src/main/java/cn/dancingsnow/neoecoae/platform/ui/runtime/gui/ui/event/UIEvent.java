package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
public final class UIEvent {
    public UIEvents type;
    public UIElement target, currentTarget;
    public float x,y,mouseX,mouseY,deltaX,deltaY,wheelDelta;
    public int button;
    public boolean hasHandler;
    public Object customData;
    public float dragStartX,dragStartY;
    public cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI.DragHandler dragHandler;
    public boolean isShiftDown(){return UIElement.isShiftDown();}
    public boolean isCtrlDown(){return UIElement.isCtrlDown();}
    public boolean isAltDown(){return UIElement.isAltDown();}
    public HoverTooltips hoverTooltips;
    private boolean stopped, immediate;
    public UIEvent(UIEvents type,UIElement target,float x,float y,int button) {
        this.type=type;this.target=target;this.currentTarget=target;this.x=this.mouseX=x;this.y=this.mouseY=y;this.button=button;
    }
    public void stopPropagation() { stopped=true; }
    public void stopImmediatePropagation() { stopped=immediate=true; }
    public boolean isPropagationStopped() { return stopped; }
    public boolean isImmediatePropagationStopped() { return immediate; }
}
