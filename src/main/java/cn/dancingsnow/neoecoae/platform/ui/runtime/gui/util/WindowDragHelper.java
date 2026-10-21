package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.util;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
public final class WindowDragHelper {
    private WindowDragHelper(){}
    public static void setDragMove(UIElement bar,UIElement window,Object bounds,Object snap){
        bar.addEventListener(UIEvents.MOUSE_DOWN,e->{if(e.button==0&&bar.getModularUI()!=null){bar.getModularUI().getDragHandler().startDrag(new org.joml.Vector2f(window.getSelfPositionX(),window.getSelfPositionY()),null,bar);e.stopPropagation();e.hasHandler=true;}});
        bar.addEventListener(UIEvents.DRAG_SOURCE_UPDATE,e->{if(e.dragHandler!=null&&e.dragHandler.draggingObject instanceof org.joml.Vector2f origin){
            window.getLayoutStyle().left(origin.x+e.x-e.dragStartX).top(origin.y+e.y-e.dragStartY);e.stopPropagation();}});
    }
}
