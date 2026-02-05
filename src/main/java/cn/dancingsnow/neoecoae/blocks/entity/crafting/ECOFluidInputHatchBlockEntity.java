package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.appliedenergistics.yoga.YogaAlign;
import org.appliedenergistics.yoga.YogaEdge;
import org.appliedenergistics.yoga.YogaGutter;
import org.appliedenergistics.yoga.YogaJustify;

import java.util.List;

public class ECOFluidInputHatchBlockEntity extends AbstractCraftingBlockEntity<ECOFluidInputHatchBlockEntity> {

    public FluidTank tank = new FluidTank(16000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
        }
    };

    public ECOFluidInputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        for (Direction face : Direction.values()) {
            IFluidHandler sourceHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos.relative(face), face.getOpposite());
            if (sourceHandler != null) {
                if (!FluidUtil.tryFluidTransfer(tank, sourceHandler, tank.getCapacity(), true).isEmpty()) {
                    return;
                }
            }
        }
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement root = new UIElement().layout(layout -> layout
            .setPadding(YogaEdge.ALL, 4)
            .setGap(YogaGutter.ALL, 2)
        ).addClass("panel_bg");
        root.addChild(new TextElement()
            .setText("block.neoecoae.input_hatch", true)
            .textStyle(textStyle -> textStyle.textWrap(TextWrap.HOVER_ROLL).adaptiveHeight(true)));

        UIElement slotContainer = new UIElement().layout(layout -> {
            layout.setMargin(YogaEdge.TOP, 10);
            layout.setMargin(YogaEdge.BOTTOM, 10);
            layout.setJustifyContent(YogaJustify.SPACE_AROUND);
            layout.setAlignContent(YogaAlign.CENTER);
        });
        slotContainer.addChild(new FluidSlot()
            .bind(tank, 0)
            .setAllowClickDrained(true)
            .setAllowClickFilled(true)
            .slotStyle(slotStyle -> slotStyle.fillDirection(FillDirection.DOWN_TO_UP))
            .addClass("panel_border"));

        root.addChild(slotContainer);
        root.addChild(new InventorySlots());
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }
//    private WidgetGroup createUI() {
//        WidgetGroup root = new WidgetGroup();
//        root.setSize(180, 145);
//        root.setBackground(GuiTextures.BACKGROUND);
//
//        TextTextureWidget text = new TextTextureWidget();
//        text.setText(Component.translatable("block.neoecoae.input_hatch"));
//        text.setSelfPosition(8, 8);
//        text.textureStyle(t -> t.setType(TextTexture.TextType.LEFT_ROLL).setColor(0x403E53).setDropShadow(false));
//        text.setSize(160, 9);
//        root.addWidget(text);
//
//        AEStyleWidgetGroup tankGroup = new AEStyleWidgetGroup();
//        tankGroup.initTemplate();
//
//        tankGroup.setSize(18, 18);
//        tankGroup.setSelfPosition(81, 28);
//        AEStyleTankWidget tankWidget = new AEStyleTankWidget(tank, 0, 0, 0,true, true);
//        tankWidget.initTemplate();
//        tankGroup.addWidget(tankWidget);
//
//        root.addWidget(tankGroup);
//
//        AEStylePlayerInventoryWidget playerInventoryWidget = new AEStylePlayerInventoryWidget();
//        playerInventoryWidget.setSelfPosition(4, 52);
//        root.addWidget(playerInventoryWidget);
//
//        return root;
//    }
//
//    @Override
//    public ModularUI createUI(Player entityPlayer) {
//        return new ModularUI(createUI(), this, entityPlayer);
//    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        tank.writeToNBT(registries, data);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        tank.readFromNBT(registries, data);
    }
}
