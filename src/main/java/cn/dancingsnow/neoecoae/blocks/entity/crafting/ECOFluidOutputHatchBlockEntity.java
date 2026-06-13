package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class ECOFluidOutputHatchBlockEntity extends AbstractCraftingBlockEntity<ECOFluidOutputHatchBlockEntity> {

    public static int MAX_CAPACITY = 16000;

    public FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, MAX_CAPACITY) {
        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
            markForUpdate();
        }
    };

    public ECOFluidOutputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        data.putChild("tank", tank);
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        data.readChild("tank", tank);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (ResourceHandlerUtil.isEmpty(tank)) {
            return;
        }
        for (Direction face : Direction.values()) {
            ResourceHandler<FluidResource> targetHandler = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(face), face.getOpposite());
            if (targetHandler != null) {
                try (Transaction transaction = Transaction.open(null)) {
                    int moved = ResourceHandlerUtil.move(tank, targetHandler, fluid -> true, tank.getCapacityAsInt(0, tank.getResource(0)), transaction);
                    if (moved > 0) {
                        transaction.commit();
                        return;
                    }
                }
            }
        }
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return CraftingUIHelper.createFluidHatchUI(holder, tank, "block.neoecoae.output_hatch", false, true);
    }

}
