package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.ValueWire;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** A retained ECO element tree backed by native LDLib1 widget, slot and active-menu routing. */
public class UIElement extends WidgetGroup {
    private final List<UIElement> children=new ArrayList<>();
    private final Layout layout=new Layout(this::markLayoutDirty);
    private final ElementStyle style=new ElementStyle();
    private final Set<String> classes=new LinkedHashSet<>();
    private final Map<UIEvents,List<Consumer<UIEvent>>> listeners=new EnumMap<>(UIEvents.class);
    private final Map<UIEvents,List<Consumer<UIEvent>>> captures=new EnumMap<>(UIEvents.class);
    private final List<BoundValue> bindings=new ArrayList<>();
    private final List<RPCEventBuilder.Event> rpcEvents=new ArrayList<>();
    private final Map<Integer,Consumer<Object>> rpcCallbacks=new HashMap<>();
    private int nextRpc=1;
    private boolean display=true,layoutDirty=true,hover,allowHitTest=true,focusable;
    private final ElementTransform transform=new ElementTransform();
    private UIElement pressedTarget;
    private ModularUI modularUI;
    private IDataSource<?> localSource;
    public UIElement(){super(0,0,0,0);}
    @SuppressWarnings("unchecked") private <T extends UIElement>T self(){return (T)this;}
    public <T extends UIElement>T layout(Consumer<Layout> config){config.accept(layout);return self();}
    public <T extends UIElement>T style(Consumer<ElementStyle> config){config.accept(style);return self();}
    public Layout getLayoutStyle(){return layout;}
    public ElementStyle getStyle(){return style;}
    @Override public UIElement setId(String id){super.setId(id);return this;}
    public <T extends UIElement>T addClass(String name){if(classes.add(name)&&modularUI!=null)modularUI.restyle();return self();}
    public <T extends UIElement>T addClasses(String... names){if(classes.addAll(List.of(names))&&modularUI!=null)modularUI.restyle();return self();}
    public <T extends UIElement>T removeClass(String name){if(classes.remove(name)&&modularUI!=null)modularUI.restyle();return self();}
    public boolean hasClass(String name){return classes.contains(name);}
    public Set<String> getClasses(){return Set.copyOf(classes);}
    public UIElement addChild(UIElement child){
        children.add(child);addWidget(child);child.attachUI(modularUI);markLayoutDirty();return this;
    }
    public UIElement addChild(int index,UIElement child){children.add(index,child);addWidget(index,child);child.attachUI(modularUI);markLayoutDirty();return this;}
    public UIElement addChildren(UIElement... elements){for(var element:elements)addChild(element);return this;}
    public UIElement removeChild(UIElement child){children.remove(child);removeWidget(child);child.detach();markLayoutDirty();return this;}
    public UIElement clearAllChildren(){for(var child:List.copyOf(children))removeChild(child);return this;}
    public List<UIElement> getChildren(){return Collections.unmodifiableList(children);}
    public UIElement getFirstChild(){return children.isEmpty()?null:children.get(0);}
    public List<UIElement> getElementsById(String name){return query("#"+name);}
    public List<UIElement> query(String selector){
        var result=new ArrayList<UIElement>();if(matches(selector))result.add(this);
        for(var child:children)result.addAll(child.query(selector));return result;
    }
    public boolean matches(String selector){return selector.startsWith("#")?Objects.equals(getId(),selector.substring(1)):
        selector.startsWith(".")?classes.contains(selector.substring(1)):getElementName().equals(selector);}
    public String getElementName(){return "element";}
    public void attachUI(ModularUI ui){if(ui==null){if(modularUI!=null)detach();return;}var old=modularUI;modularUI=ui;for(var child:children)child.attachUI(ui);
        var event=new UIEvent(UIEvents.MODULAR_UI_CHANGED,this,0,0,-1);event.customData=old;onModularUIChanged(event);}
    protected void onModularUIChanged(UIEvent event){}
    private void detach(){rpcCallbacks.clear();modularUI=null;onRemoved();children.forEach(UIElement::detach);}
    public ModularUI getModularUI(){return modularUI;}
    public UIElement setDisplay(boolean value){display=value;setVisible(value);markLayoutDirty();return this;}
    public boolean isDisplay(){return display;}
    public boolean isDisplayed(){return display&&getLayoutStyle().display!=cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyDisplay.NONE;}
    public boolean effectivelyVisible(){for(UIElement element=this;element!=null;element=element.getParentElement())if(!element.isVisible()||!element.isActive()||!element.isDisplayed())return false;return true;}
    public boolean isFocused(){return isFocus();}
    public UIElement setAllowHitTest(boolean value){allowHitTest=value;return this;}
    public UIElement getParentElement(){return parent instanceof UIElement p?p:null;}
    public <T extends UIElement>T getFirstAncestorOfType(Class<T> type){for(UIElement e=this;e!=null;e=e.getParentElement())if(type.isInstance(e))return type.cast(e);return null;}
    public float getContentWidth(){return Math.max(0,getSizeWidth()-layout.paddingLeft-layout.paddingRight);}
    public float getContentHeight(){return Math.max(0,getSizeHeight()-layout.paddingTop-layout.paddingBottom);}
    public float getContentX(){return getPositionX()+layout.paddingLeft;}
    public float getContentY(){return getPositionY()+layout.paddingTop;}
    public java.util.stream.Stream<UIElement> selectId(String id){return getElementsById(id).stream();}
    public UIElement getFirstAncestor(){return getParentElement();}
    protected void onRemoved(){}
    public UIElement setFocusable(boolean value){focusable=value;return this;}
    public void focus(){if(focusable&&effectivelyVisible())setFocus(true);}
    public HoverTooltips collectHoverTooltips(){var event=new UIEvent(UIEvents.HOVER_TOOLTIPS,this,0,0,-1);dispatchEvent(event);return event.hoverTooltips;}
    private final java.util.Map<String,java.util.List<java.util.function.Consumer<Object>>> messages=new java.util.HashMap<>();
    public void onMessage(String name,java.util.function.Consumer<Object> listener){messages.computeIfAbsent(name,key->new java.util.ArrayList<>()).add(listener);}
    public void sendMessage(String name){for(var listener:java.util.List.copyOf(messages.getOrDefault(name,java.util.List.of())))listener.accept(null);}
    public org.joml.Vector2f getLocalMouse(float x,float y){return new org.joml.Vector2f(x,y);}
    public static class ElementTransform {float x,y;public ElementTransform translate(float x,float y){this.x=x;this.y=y;return this;}}
    public UIElement transform(Consumer<ElementTransform> action){action.accept(transform);markLayoutDirty();return this;}
    @Override public UIElement setVisible(boolean value){super.setVisible(value);return this;}
    @Override public UIElement setActive(boolean value){super.setActive(value);if(value)removeClass("__disabled__");else addClass("__disabled__");return this;}
    public UIElement setOverflowVisible(boolean value){style.overflowVisible=value;return this;}
    public boolean isOverflowVisible(){return style.overflowVisible;}
    public boolean isHover(){return hover;}
    public boolean isSelfOrChildHover(){return hover||children.stream().anyMatch(UIElement::isSelfOrChildHover);}
    public static boolean isShiftDown(){return net.minecraft.client.gui.screens.Screen.hasShiftDown();}
    public static boolean isCtrlDown(){return net.minecraft.client.gui.screens.Screen.hasControlDown();}
    public static boolean isAltDown(){return net.minecraft.client.gui.screens.Screen.hasAltDown();}
    public <T extends UIElement>T addEventListener(UIEvents type,Consumer<UIEvent> listener){listeners.computeIfAbsent(type,k->new ArrayList<>()).add(listener);return self();}
    public <T extends UIElement>T addEventListener(UIEvents type,Consumer<UIEvent> listener,boolean capture){
        (capture?captures:listeners).computeIfAbsent(type,k->new ArrayList<>()).add(listener);return self();
    }
    protected void onMouseDown(UIEvent event){}
    protected void onMouseUp(UIEvent event){}
    protected void onMouseWheel(UIEvent event){}
    protected void onMouseMove(UIEvent event){}
    protected void onHoverTooltips(UIEvent event){if(!tooltipTexts.isEmpty())event.hoverTooltips=new HoverTooltips(List.copyOf(tooltipTexts),null,null,null);}
    private void invoke(UIEvent event,boolean capture){
        event.currentTarget=this;
        for(var listener:List.copyOf((capture?captures:listeners).getOrDefault(event.type,List.of()))){listener.accept(event);if(event.isImmediatePropagationStopped())return;}
        if(!capture)switch(event.type){case MOUSE_DOWN->onMouseDown(event);case MOUSE_UP->onMouseUp(event);case MOUSE_WHEEL->onMouseWheel(event);case MOUSE_MOVE->onMouseMove(event);case HOVER_TOOLTIPS->onHoverTooltips(event);default->{}}
    }
    public void dispatchEvent(UIEvent event){
        var path=new ArrayList<UIElement>();for(UIElement node=this;node!=null;node=node.parent instanceof UIElement p?p:null)path.add(node);
        for(int i=path.size()-1;i>=0;i--){path.get(i).invoke(event,true);if(event.isPropagationStopped())return;}
        for(var node:path){node.invoke(event,false);if(event.isPropagationStopped())return;}
    }
    public UIElement hitTest(double x,double y){
        if(!isVisible()||!isActive()||!isDisplayed())return null;
        boolean inside=isMouseOverElement(x,y);if(!inside&&!isOverflowVisible())return null;
        var ordered=children.stream().sorted(Comparator.comparingInt(child->child.style.z)).toList();
        for(int i=ordered.size()-1;i>=0;i--){var found=ordered.get(i).hitTest(x,y);if(found!=null)return found;}
        return inside&&allowHitTest?this:null;
    }
    /** WidgetGroup also includes overflowing children; retained clipping uses the element's own box. */
    @Override public boolean isMouseOverElement(double x,double y){
        return x>=getPositionX()&&y>=getPositionY()&&x<getPositionX()+getSizeWidth()&&y<getPositionY()+getSizeHeight();
    }
    private UIEvent pointer(UIEvents type,UIElement target,double x,double y,int button,double delta){
        var event=new UIEvent(type,target,(float)x,(float)y,button);event.wheelDelta=event.deltaY=(float)delta;
        target.dispatchEvent(event);return event;
    }
    private boolean nativePointer(UIElement target,UIEvents type,double x,double y,int button,double delta){
        if(target==null)return false;
        for(int i=target.widgets.size()-1;i>=0;i--){var widget=target.widgets.get(i);
            if(widget instanceof UIElement||!widget.isVisible()||!widget.isActive())continue;
            boolean result=switch(type){case MOUSE_DOWN->widget.mouseClicked(x,y,button);case MOUSE_UP->widget.mouseReleased(x,y,button);
                case MOUSE_WHEEL->widget.mouseWheelMove(x,y,delta);case MOUSE_MOVE->widget.mouseMoved(x,y);default->false;};
            if(result)return true;}
        return target.parent instanceof UIElement p&&nativePointer(p,type,x,y,button,delta);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        var target=hitTest(x,y);if(target==null)return false;pressedTarget=target;
        if(modularUI!=null)modularUI.pointerDown(button,(float)x,(float)y);
        var event=pointer(UIEvents.MOUSE_DOWN,target,x,y,button,0);
        return event.hasHandler||event.isPropagationStopped()||nativePointer(target,UIEvents.MOUSE_DOWN,x,y,button,0);
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        var target=hitTest(x,y);if(target==null)target=this;
        var event=pointer(UIEvents.MOUSE_UP,target,x,y,button,0);
        if(pressedTarget!=null&&pressedTarget!=target)pointer(UIEvents.MOUSE_UP,pressedTarget,x,y,button,0);
        boolean result=nativePointer(pressedTarget==null?target:pressedTarget,UIEvents.MOUSE_UP,x,y,button,0);pressedTarget=null;
        if(modularUI!=null)modularUI.pointerUp();return result||event.hasHandler||event.isPropagationStopped();
    }
    @Override public boolean mouseWheelMove(double x,double y,double delta){
        var target=hitTest(x,y);if(target==null)return false;var event=pointer(UIEvents.MOUSE_WHEEL,target,x,y,-1,delta);
        return event.hasHandler||event.isPropagationStopped()||nativePointer(target,UIEvents.MOUSE_WHEEL,x,y,-1,delta);
    }
    private void updateHover(double x,double y,Set<UIElement> path){
        boolean current=path.contains(this);
        if(current!=hover){hover=current;var event=new UIEvent(current?UIEvents.MOUSE_ENTER:UIEvents.MOUSE_LEAVE,this,(float)x,(float)y,-1);invoke(event,false);}
        for(var child:children)child.updateHover(x,y,path);
    }
    @Override public boolean mouseMoved(double x,double y){
        var target=hitTest(x,y);var path=new HashSet<UIElement>();
        for(var node=target;node!=null;node=node.getParentElement())path.add(node);
        updateHover(x,y,path);if(modularUI!=null)modularUI.hovered=target;
        var event=pointer(UIEvents.MOUSE_MOVE,target==null?this:target,x,y,-1,0);
        return nativePointer(target,UIEvents.MOUSE_MOVE,x,y,-1,0)||event.hasHandler;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        mouseMoved(x,y);if(modularUI!=null&&modularUI.getDragHandler().isDragging()){modularUI.getDragHandler().update((float)x,(float)y);return true;}
        if(pressedTarget!=null)for(var widget:pressedTarget.widgets)if(!(widget instanceof UIElement)&&widget.mouseDragged(x,y,button,dx,dy))return true;
        return false;
    }
    public void markLayoutDirty(){layoutDirty=true;if(parent instanceof UIElement element)element.markLayoutDirty();}
    public void arrange(){for(int pass=0;pass<4;pass++){layoutDirty=false;LayoutEngine.arrange(this);if(!layoutDirty)break;}}
    public void onLayoutApplied(){}
    public boolean isLayoutDirty(){return layoutDirty;}
    public float intrinsicWidth(){return 0;}
    public float intrinsicHeight(){return 0;}
    public void applyBounds(float x,float y,float width,float height){
        setSelfPosition(new Position(Math.round(x+transform.x),Math.round(y+transform.y)));setSize(new Size(Math.max(0,Math.round(width)),Math.max(0,Math.round(height))));
        boolean effective=isDisplayed();for(UIElement p=getParentElement();p!=null;p=p.getParentElement())effective&=p.isDisplayed();
        super.setVisible(display&&effective);
    }
    public void drawBackgroundAdditional(GUIContext context){}
    public void drawBackgroundTexture(GUIContext context){
        if(style.background!=null)context.drawTexture(style.background,getPositionX(),getPositionY(),getSizeWidth(),getSizeHeight());
    }
    /** Matches the retained API: an override can clip/transform its additional background and children together. */
    public void drawContents(GUIContext context){
        boolean clipped=!isOverflowVisible();
        if(clipped)context.enableScissor(getContentX(),getContentY(),getContentWidth(),getContentHeight());
        try{drawBackgroundAdditional(context);drawChildren(context);}
        finally{if(clipped)context.disableScissor();}
    }
    protected void drawChildren(GUIContext context){
        List<Widget> ordered=widgets;
        if(widgets.stream().anyMatch(widget->widget instanceof UIElement element&&element.style.z!=0)){
            ordered=widgets.stream().sorted(Comparator.comparingInt(widget->widget instanceof UIElement element?element.style.z:0)).toList();
        }
        for(var widget:ordered)if(widget.isVisible()){
            widget.drawInBackground(context.graphics,Math.round(context.mouseX),Math.round(context.mouseY),context.partialTicks);
        }
    }
    public void drawForegroundAdditional(GUIContext context){}
    public void drawBackgroundOverlay(GUIContext context){}
    @Override public void drawInBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTicks){
        boolean owner=GUIContext.beginPass();
        var context=new GUIContext(graphics,mouseX,mouseY,partialTicks);
        graphics.pose().pushPose();
        try{
        if(style.z!=0)graphics.pose().translate(0,0,style.z);
        drawBackgroundTexture(context);drawContents(context);
        if(style.overlay!=null)context.drawTexture(style.overlay,getPositionX(),getPositionY(),getSizeWidth(),getSizeHeight());
        drawBackgroundOverlay(context);context.flushPostRendering();
        }finally{graphics.pose().popPose();GUIContext.endPass(owner,context);}
    }
    @Override public void drawInForeground(GuiGraphics graphics,int mouseX,int mouseY,float partialTicks){
        super.drawInForeground(graphics,mouseX,mouseY,partialTicks);
        var context=new GUIContext(graphics,mouseX,mouseY,partialTicks);drawForegroundAdditional(context);context.flushPostRendering();
        if(!(parent instanceof UIElement)){
            var hovered=hitTest(mouseX,mouseY);if(hovered==null)return;
            var event=new UIEvent(UIEvents.HOVER_TOOLTIPS,hovered,mouseX,mouseY,-1);hovered.dispatchEvent(event);
            if(event.hoverTooltips!=null&&!event.hoverTooltips.tooltips().isEmpty()){
                if(gui!=null&&gui.getModularUIGui()!=null)gui.getModularUIGui().setHoverTooltip(event.hoverTooltips.tooltips(),event.hoverTooltips.image() instanceof net.minecraft.world.item.ItemStack stack ? stack : net.minecraft.world.item.ItemStack.EMPTY,null,null);
                else graphics.renderComponentTooltip(net.minecraft.client.Minecraft.getInstance().font,event.hoverTooltips.tooltips(),mouseX,mouseY);
            }
        }
    }
    public <T>void attachBinding(IDataSource<T> target,DataBindingBuilder.Binding<T> binding){if(binding.hasInitial())target.setValue(binding.initial());bindings.add(new BoundValue(target,binding));}
    public <T extends UIElement>T bindDataSource(IDataSource<?> source){localSource=source;return self();}
    private final class BoundValue{
        final IDataSource target;final DataBindingBuilder.Binding binding;Object previous;
        BoundValue(IDataSource target,DataBindingBuilder.Binding binding){this.target=target;this.binding=binding;previous=ValueWire.copy(target.getValue());}
        Object source(){return binding.source()==null?target.getValue():binding.source().get();}
        void receive(Object value){target.setValue(value);previous=ValueWire.copy(value);}
    }
    @Override public void writeInitialData(FriendlyByteBuf buffer){super.writeInitialData(buffer);for(var value:bindings){
        Object data=value.source();ValueWire.write(buffer,value.binding.type(),data);value.previous=ValueWire.copy(data);}}
    @Override public void readInitialData(FriendlyByteBuf buffer){super.readInitialData(buffer);for(var value:bindings)value.receive(ValueWire.read(buffer,value.binding.type()));}
    @Override public void detectAndSendChanges(){if(!(parent instanceof UIElement)&&layoutDirty)arrange();super.detectAndSendChanges();for(int i=0;i<bindings.size();i++){
        var value=bindings.get(i);if(value.binding.s2c()==SyncStrategy.NONE)continue;Object data=value.source();
        if(!Objects.equals(data,value.previous)||value.binding.s2c()==SyncStrategy.ALWAYS){int index=i;writeUpdateInfo(700,b->{b.writeVarInt(index);ValueWire.write(b,value.binding.type(),data);});value.previous=ValueWire.copy(data);}}}
    @Override public void updateScreen(){super.updateScreen();if(!(parent instanceof UIElement)&&layoutDirty)arrange();
        invoke(new UIEvent(UIEvents.TICK,this,0,0,-1),false);
        if(localSource!=null&&this instanceof IDataSource target)target.setValue(localSource.getValue());
        for(int i=0;i<bindings.size();i++){var value=bindings.get(i);Object data=value.target.getValue();
            if(value.binding.c2s()!=SyncStrategy.NONE&&!Objects.equals(data,value.previous)){int index=i;writeClientAction(700,b->{b.writeVarInt(index);ValueWire.write(b,value.binding.type(),data);});value.previous=ValueWire.copy(data);}}
    }
    @Override public void readUpdateInfo(int id,FriendlyByteBuf buffer){
        if(id==700){int index=buffer.readVarInt();if(index<0||index>=bindings.size())throw new IllegalArgumentException("Unknown binding");var value=bindings.get(index);value.receive(ValueWire.read(buffer,value.binding.type()));}
        else if(id==701){int request=buffer.readVarInt();int event=buffer.readVarInt();if(event<0||event>=rpcEvents.size())return;var spec=rpcEvents.get(event);if(spec.result()==null)return;Object result=ValueWire.read(buffer,spec.result());var callback=rpcCallbacks.remove(request);if(callback!=null)callback.accept(result);}
        else super.readUpdateInfo(id,buffer);
    }
    @Override public void handleClientAction(int id,FriendlyByteBuf buffer){
        if(!activeMenu())return;
        if(id==700){int index=buffer.readVarInt();if(index<0||index>=bindings.size())return;var value=bindings.get(index);
            if(value.binding.c2s()==SyncStrategy.NONE)return;Object received=ValueWire.read(buffer,value.binding.type());
            if(value.binding.sink()!=null)value.binding.sink().accept(received);value.receive(received);}
        else if(id==701){int event=buffer.readVarInt(),request=buffer.readVarInt();if(event<0||event>=rpcEvents.size())return;var spec=rpcEvents.get(event);Object[] args=new Object[spec.parameters().length];
            for(int i=0;i<args.length;i++)args[i]=ValueWire.read(buffer,spec.parameters()[i]);
            Object result=spec.handler().apply(args);if(spec.result()!=null)writeUpdateInfo(701,b->{b.writeVarInt(request);b.writeVarInt(event);ValueWire.write(b,spec.result(),result);});}
        else super.handleClientAction(id,buffer);
    }
    public boolean activeMenu(){return modularUI!=null&&modularUI.getMenu()!=null&&modularUI.player.containerMenu==modularUI.getMenu()&&modularUI.getMenu().stillValid(modularUI.player);}
    public RPCEmitter addRPCEvent(RPCEventBuilder.Event event){int index=rpcEvents.size();rpcEvents.add(event);return new RPCEmitter(this,index);}
    public boolean sendRpc(int index,Consumer<Object> callback,Object... values){
        if(!activeMenu()||index<0||index>=rpcEvents.size())return false;var spec=rpcEvents.get(index);if(values.length!=spec.parameters().length)throw new IllegalArgumentException("RPC arity mismatch");
        int request=nextRpc++;if(callback!=null){if(rpcCallbacks.size()>=128)return false;rpcCallbacks.put(request,callback);}
        writeClientAction(701,b->{b.writeVarInt(index);b.writeVarInt(request);for(int i=0;i<values.length;i++)ValueWire.write(b,spec.parameters()[i],values[i]);});return true;
    }
}
