package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.blocks.crafting.ECOFluidOutputHatchBlock;
import cn.dancingsnow.neoecoae.gui.GuiTextures;
import cn.dancingsnow.neoecoae.gui.widget.AEStylePlayerInventoryWidget;
import cn.dancingsnow.neoecoae.gui.widget.AEStyleTankWidget;
import cn.dancingsnow.neoecoae.gui.widget.AEStyleWidgetGroup;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.side.fluid.FluidTransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class ECOFluidInputHatchBlockEntity extends AbstractCraftingBlockEntity<ECOFluidInputHatchBlockEntity>
    implements IUIHolder.Block {

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
        Direction face = state.getValue(ECOFluidOutputHatchBlock.FACING);
        FluidTransferHelper.importToTarget(tank, tank.getCapacity(), f -> true, level, pos.relative(face), face.getOpposite());
    }

    private WidgetGroup createUI() {
        WidgetGroup root = new WidgetGroup();
        root.setSize(180, 145);
        root.setBackground(GuiTextures.BACKGROUND);

        TextTextureWidget text = new TextTextureWidget();
        text.setText(Component.translatable("block.neoecoae.input_hatch"));
        text.setSelfPosition(8, 8);
        text.textureStyle(t -> t.setType(TextTexture.TextType.LEFT_ROLL).setColor(0x403E53).setDropShadow(false));
        text.setSize(160, 9);
        root.addWidget(text);

        AEStyleWidgetGroup tankGroup = new AEStyleWidgetGroup();
        tankGroup.initTemplate();

        tankGroup.setSize(18, 18);
        tankGroup.setSelfPosition(81, 28);
        AEStyleTankWidget tankWidget = new AEStyleTankWidget(tank, 0, 0, 0,true, true);
        tankWidget.initTemplate();
        tankGroup.addWidget(tankWidget);

        root.addWidget(tankGroup);

        AEStylePlayerInventoryWidget playerInventoryWidget = new AEStylePlayerInventoryWidget();
        playerInventoryWidget.setSelfPosition(4, 52);
        root.addWidget(playerInventoryWidget);

        return root;
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(createUI(), this, entityPlayer);
    }

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
