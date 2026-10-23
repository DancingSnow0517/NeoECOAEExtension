package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.slot.LocalSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.IBindable;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.utils.Position;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ItemSlot extends UIElement implements IBindable<ItemStack>{
    public static class SlotStyle{
        public IGuiTexture hover,overlay;public boolean player,overlayEmptyOnly;
        public SlotStyle showSlotOverlayOnlyEmpty(boolean value){overlayEmptyOnly=value;return this;}
        public boolean quickMove=true;
        public SlotStyle acceptQuickMove(boolean value){quickMove=value;return this;}
        public SlotStyle hoverOverlay(IGuiTexture v){hover=v;return this;}
        public SlotStyle slotOverlay(IGuiTexture v){overlay=v;return this;}
        public SlotStyle isPlayerSlot(boolean v){player=v;return this;}
    }
    protected Slot slot;
    private final NativeSlot widget;
    private final SlotStyle slotStyle=new SlotStyle();
    private boolean phantom;
    private final List<Consumer<ItemStack>> valueListeners=new ArrayList<>();
    public ItemSlot(){this(new LocalSlot());}
    public ItemSlot(Slot slot){this.slot=slot;widget=new NativeSlot(slot);addWidget(widget);layout(l->l.width(18).height(18).paddingAll(1));}
    @Override public String getElementName(){return "item-slot";}
    public ItemSlot bind(Slot slot){this.slot=slot;widget.bindSlot(slot);return this;}
    public Slot getSlot(){return slot;}
    public ItemSlot slotStyle(Consumer<SlotStyle> config){config.accept(slotStyle);widget.setLocationInfo(slotStyle.player,false);return this;}
    @Override public ItemStack getValue(){return slot.getItem();}
    @Override public ItemSlot setValue(ItemStack stack){return setValue(stack,true);}
    public ItemSlot setValue(ItemStack stack,boolean notify){slot.set(stack==null?ItemStack.EMPTY:stack.copy());if(notify)valueListeners.forEach(l->l.accept(getValue()));return this;}
    public ItemSlot registerValueListener(Consumer<ItemStack> listener){valueListeners.add(listener);return this;}
    public ItemSlot xeiPhantom(){phantom=true;return this;}
    public ItemSlot xeiRecipeIngredient(com.lowdragmc.lowdraglib.jei.IngredientIO io){widget.setIngredientIO(io);return this;}
    public ItemSlot xeiRecipeSlot(com.lowdragmc.lowdraglib.jei.IngredientIO io,float chance){widget.setIngredientIO(io);widget.setXEIChance(chance);return this;}
    public ItemSlot setItem(ItemStack stack,boolean notify){return setValue(stack,notify);}
    protected void drawHover(GUIContext context){if(slotStyle.hover!=null)context.drawTexture(slotStyle.hover,0,0,16,16);}
    @Override public float intrinsicWidth(){return 16;}
    @Override public float intrinsicHeight(){return 16;}
    @Override public void applyBounds(float x,float y,float width,float height){super.applyBounds(x,y,width,height);widget.setSelfPosition(new Position(Math.round(getLayoutStyle().paddingLeft)-1,Math.round(getLayoutStyle().paddingTop)-1));}
    protected void drawItemStack(GUIContext context,ItemStack stack){context.graphics.renderItem(stack,0,0);context.graphics.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font,stack,0,0);}
    protected void drawSlotOverlay(GUIContext context){if(slotStyle.overlay!=null&&(!slotStyle.overlayEmptyOnly||getValue().isEmpty()))context.drawTexture(slotStyle.overlay,0,0,16,16);}
    public List<Component> getFullTooltipTexts(){return getFullTooltipTexts(true);}
    public List<Component> getFullTooltipTexts(boolean withItemTooltips){
        var result=new ArrayList<Component>();if(withItemTooltips&&!getValue().isEmpty())result.addAll(getValue().getTooltipLines(net.minecraft.client.Minecraft.getInstance().player,net.minecraft.world.item.TooltipFlag.Default.NORMAL));return result;
    }
    @Override protected void onHoverTooltips(UIEvent event){event.hoverTooltips=new HoverTooltips(getFullTooltipTexts(),getValue(),null,null);}
    private final class NativeSlot extends SlotWidget implements com.lowdragmc.lowdraglib.gui.ingredient.IGhostIngredientTarget{
        NativeSlot(Slot slot){super();canPutItems=canTakeItems=true;drawHoverOverlay=false;drawHoverTips=false;bindSlot(slot);}
        void bindSlot(Slot original){updateSlot(new Slot(original.container,original.getContainerSlot(),0,0){
            @Override public ItemStack getItem(){return original.getItem();}
            @Override public void set(ItemStack value){original.set(value);}
            @Override public void setByPlayer(ItemStack value){original.setByPlayer(value);}
            @Override public void setChanged(){original.setChanged();}
            @Override public boolean mayPlace(ItemStack value){return isActive()&&original.mayPlace(value);}
            @Override public boolean mayPickup(net.minecraft.world.entity.player.Player player){return isActive()&&original.mayPickup(player);}
            @Override public ItemStack remove(int amount){return original.remove(amount);}
            @Override public int getMaxStackSize(){return original.getMaxStackSize();}
            @Override public int getMaxStackSize(ItemStack value){return original.getMaxStackSize(value);}
            @Override public boolean isActive(){return original.isActive()&&ItemSlot.this.effectivelyVisible()&&NativeSlot.this.isEnabled();}
            @Override public boolean isHighlightable(){return original.isHighlightable();}
            @Override public boolean isSameInventory(Slot other){return original.isSameInventory(other);}
            @Override public void onTake(net.minecraft.world.entity.player.Player player,ItemStack stack){original.onTake(player,stack);}
            @Override public void onQuickCraft(ItemStack value,ItemStack old){original.onQuickCraft(value,old);}
        });}
        @Override public void drawInBackground(GuiGraphics graphics,int mouseX,int mouseY,float ticks){
            graphics.pose().pushPose();graphics.pose().translate(ItemSlot.this.getContentX(),ItemSlot.this.getContentY(),0);
            var context=new GUIContext(graphics,mouseX,mouseY,ticks);if(!getValue().isEmpty())drawItemStack(context,getValue());drawSlotOverlay(context);
            if(isMouseOverElement(mouseX,mouseY))drawHover(context);
            context.flushPostRendering();graphics.pose().popPose();
        }
        @Override public boolean canMergeSlot(ItemStack stack){return slotStyle.quickMove&&super.canMergeSlot(stack);}
        @Override public java.util.List<com.lowdragmc.lowdraglib.gui.ingredient.Target> getPhantomTargets(Object ingredient){
            if(!phantom||!ItemSlot.this.effectivelyVisible())return java.util.List.of();
            ItemStack result=ItemStack.EMPTY;
            if(ingredient instanceof ItemStack stack)result=stack;
            else if(com.lowdragmc.lowdraglib.LDLib.isJeiLoaded()&&ingredient instanceof mezz.jei.api.ingredients.ITypedIngredient<?> typed)result=typed.getItemStack().orElse(ItemStack.EMPTY);
            else if(com.lowdragmc.lowdraglib.LDLib.isEmiLoaded()&&ingredient instanceof dev.emi.emi.api.stack.EmiStack stack)result=stack.getItemStack();
            if(result.isEmpty())return java.util.List.of();
            ItemStack display=result.copy();
            return java.util.List.of(new com.lowdragmc.lowdraglib.gui.ingredient.Target(){
                public net.minecraft.client.renderer.Rect2i getArea(){return ItemSlot.this.toRectangleBox();}
                public void accept(Object ignored){ItemSlot.this.setValue(display.copy(),true);}
            });
        }
    }
}
