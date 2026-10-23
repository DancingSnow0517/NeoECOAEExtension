package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.FillDirection;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.Horizontal;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.Vertical;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.SupplierDataSource;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidTransferWrapper;
import com.lowdragmc.lowdraglib.utils.Size;
import com.lowdragmc.lowdraglib.utils.Position;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Native tank transfer and synchronization; no fluid quantities are held by the visual element. */
public class FluidSlot extends UIElement {
    public final Label amountLabel=new Label();
    private final NativeTank tank=new NativeTank();
    /** Native transfer/sync stays in the tree; its pixels belong to this element's background hook. */
    private static final class NativeTank extends TankWidget {
        com.lowdragmc.lowdraglib.side.fluid.FluidStack syncedFluid(){return lastFluidInTank;}
        long syncedCapacity(){return lastTankCapacity;}
        @Override public void drawInBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTicks){}
        void drawTank(GUIContext context){super.drawInBackground(context.graphics,(int)context.mouseX,(int)context.mouseY,context.partialTicks);}
    }
    private IFluidHandler handler;private int tankIndex;
    public static class SlotStyle {
        private FillDirection direction=FillDirection.DOWN_TO_UP;
        public IGuiTexture hover;
        public SlotStyle fillDirection(FillDirection value){direction=value;return this;}
        public SlotStyle hoverOverlay(IGuiTexture value){hover=value;return this;}
    }
    private final SlotStyle slotStyle=new SlotStyle();
    public FluidSlot(){
        tank.setBackground(com.lowdragmc.lowdraglib.gui.texture.IGuiTexture.EMPTY);
        tank.setShowAmount(false).setDrawHoverOverlay(false).setDrawHoverTips(false);
        addWidget(tank);addChild(amountLabel);amountLabel.setAllowHitTest(false);
        amountLabel.layout(l->l.positionType(cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition.ABSOLUTE).widthPercent(100).heightPercent(100));
        amountLabel.addClass("__fluid-slot_amount-label__");
        amountLabel.textStyle(s->s.textAlignVertical(Vertical.BOTTOM).textAlignHorizontal(Horizontal.RIGHT).fontSize(4.5f));
        amountLabel.bindDataSource(SupplierDataSource.of(this::getFluidAmountText));
        layout(l->l.width(18).height(18));
    }
    @Override public String getElementName(){return "fluid-slot";}
    public FluidSlot bind(IFluidHandler fluids,int index){Objects.requireNonNull(fluids);if(index<0||index>=fluids.getTanks())throw new IllegalArgumentException("Invalid tank index: "+index);handler=fluids;tankIndex=index;tank.setFluidTank(new FluidTransferWrapper(fluids),index);return this;}
    public FluidSlot setAllowClickFilled(boolean value){tank.setAllowClickFilled(value);return this;}
    public FluidSlot setAllowClickDrained(boolean value){tank.setAllowClickDrained(value);return this;}
    public FluidSlot slotStyle(Consumer<SlotStyle> action){action.accept(slotStyle);
        tank.setFillDirection(com.lowdragmc.lowdraglib.gui.texture.ProgressTexture.FillDirection.valueOf(slotStyle.direction.name()));return this;}
    public FluidStack getValue(){
        var value=tank.syncedFluid();
        if(isRemote()&&value!=null)return value.isEmpty()?FluidStack.EMPTY:new FluidStack(value.getFluid(),(int)Math.min(Integer.MAX_VALUE,value.getAmount()),value.getTag());
        return handler==null?FluidStack.EMPTY:handler.getFluidInTank(tankIndex);
    }
    public List<Component> getFullTooltipTexts(){
        var value=getValue();long capacity=handler==null?tank.syncedCapacity():handler.getTankCapacity(tankIndex);
        var result=new ArrayList<Component>();
        result.add(value.isEmpty()?Component.translatable("ldlib.fluid.empty"):value.getDisplayName());
        result.add(Component.literal(value.getAmount()+" / "+capacity+" mB"));return result;
    }
    public Component getFluidAmountText(){var value=getValue();return value.isEmpty()?Component.empty():Component.literal(com.lowdragmc.lowdraglib.gui.util.TextFormattingUtil.formatLongToCompactStringBuckets(value.getAmount(),3)+"B");}
    @Override public void drawBackgroundAdditional(GUIContext context){
        tank.drawTank(context);
        if((isHover()||isSelfOrChildHover())&&slotStyle.hover!=null)context.drawTexture(slotStyle.hover,getContentX(),getContentY(),getContentWidth(),getContentHeight());
    }
    @Override protected void onHoverTooltips(UIEvent event){event.hoverTooltips=new HoverTooltips(getFullTooltipTexts(),null,null,null);}
    @Override public void applyBounds(float x,float y,float w,float h){super.applyBounds(x,y,w,h);
        tank.setSelfPosition(new Position(Math.round(getLayoutStyle().paddingLeft)-1,Math.round(getLayoutStyle().paddingTop)-1));
        tank.setSize(new Size(Math.round(getContentWidth())+2,Math.round(getContentHeight())+2));}
}
