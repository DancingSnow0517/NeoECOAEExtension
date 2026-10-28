package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Constructs a native LDLib1 menu from the retained ECO element tree. */
public final class ModularUI {
    public final Player player;
    public final UI ui;
    private com.lowdragmc.lowdraglib.gui.modular.ModularUI nativeUI;
    UIElement hovered;
    private int lastMouseDownButton=-1;
    private float mouseDownX,mouseDownY,lastMouseX,lastMouseY;
    private final DragHandler dragHandler=new DragHandler();
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public ModularUI(UI ui){this(ui,net.minecraft.client.Minecraft.getInstance().player);}
    public ModularUI(UI ui,Player player){this.ui=ui;this.player=player;ui.root().attachUI(this);}
    public com.lowdragmc.lowdraglib.gui.modular.ModularUI nativeUI(IUIHolder holder){
        if(nativeUI==null){for(var sheet:ui.stylesheets())sheet.apply(ui.root());ui.root().arrange();
            nativeUI=new com.lowdragmc.lowdraglib.gui.modular.ModularUI(ui.root(),holder,player);
            nativeUI.registerCloseListener(()->ui.root().attachUI(null));}
        return nativeUI;
    }
    public AbstractContainerMenu getMenu(){return nativeUI==null?null:nativeUI.getModularUIContainer();}
    public boolean hasValidHolder(){return nativeUI!=null&&nativeUI.holder!=null&&!nativeUI.holder.isInvalid();}
    public UI getUi(){return ui;}
    public UIElement getRootElement(){return ui.root();}
    public Player getPlayer(){return player;}
    public void restyle(){for(var sheet:ui.stylesheets())sheet.apply(ui.root());}
    public UIElement prepareWidget(){for(var sheet:ui.stylesheets())sheet.apply(ui.root());ui.root().arrange();ui.root().setClientSideWidget();return ui.root();}
    public boolean isRemote(){return player.level().isClientSide;}
    public static ModularUI from(com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer menu){return menu.getModularUI().mainGroup instanceof UIElement element?element.getModularUI():null;}
    public UIElement getWidget(){return ui.root();}
    public UIElement hitTestAtScreen(float x,float y){return ui.root().hitTest(x,y);}
    public UIElement getLastHoveredElement(){return hovered;}
    public boolean isDrawTooltips(){return true;}
    public int getLastMouseDownButton(){return lastMouseDownButton;}
    void pointerDown(int button,float x,float y){lastMouseDownButton=button;mouseDownX=x;mouseDownY=y;pointerMove(x,y);}
    void pointerMove(float x,float y){lastMouseX=x;lastMouseY=y;}
    /** Last pointer position, in the same absolute coordinates as element positions. */
    public float getLastMouseX(){return lastMouseX;}
    public float getLastMouseY(){return lastMouseY;}
    public int getScreenWidth(){return nativeUI==null?0:nativeUI.getScreenWidth();}
    public int getScreenHeight(){return nativeUI==null?0:nativeUI.getScreenHeight();}
    public int getLeftPos(){return nativeUI==null?0:nativeUI.getGuiLeft();}
    public int getTopPos(){return nativeUI==null?0:nativeUI.getGuiTop();}
    void pointerUp(){lastMouseDownButton=-1;dragHandler.clear();}
    public <T extends UIElement>java.util.List<T> getElementsByType(Class<T> type){var result=new java.util.ArrayList<T>();collect(ui.root(),type,result);return result;}
    private static <T extends UIElement>void collect(UIElement root,Class<T> type,java.util.List<T> result){if(type.isInstance(root))result.add(type.cast(root));for(var child:root.getChildren())collect(child,type,result);}
    public DragHandler getDragHandler(){return dragHandler;}
    public final class DragHandler {
        public Object draggingObject;private UIElement source;
        public boolean isDragging(){return source!=null;}
        public void startDrag(Object value,Object visual,UIElement source){draggingObject=value;this.source=source;}
        public void clear(){source=null;draggingObject=null;}
        void update(float x,float y){if(source==null)return;var event=new cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent(cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents.DRAG_SOURCE_UPDATE,source,x,y,lastMouseDownButton);
            event.dragStartX=mouseDownX;event.dragStartY=mouseDownY;event.dragHandler=this;source.dispatchEvent(event);}
    }
}
