package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

public class ECOFluidInputHatchBlockEntity extends AbstractCraftingBlockEntity<ECOFluidInputHatchBlockEntity>
        implements IUIHolder.BlockEntityUI {

    private static final int TANK_CAPACITY = 64000;

    public FluidTank tank = new FluidTank(TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
        }

        @Override
        public FluidTank readFromNBT(CompoundTag nbt) {
            super.readFromNBT(nbt);
            // Manually trigger onContentsChanged after NBT restore since readFromNBT doesn't auto-notify
            // This ensures UI refreshes when tank data is synced from server
            onContentsChanged();
            return this;
        }
    };
    private final LazyOptional<IFluidHandler> fluidHandlerCap = LazyOptional.of(() -> tank);

    public ECOFluidInputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public ModularUI createUI(Player player) {
        return NELDLibUis.createFluidInputHatch(this, player);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        for (Direction face : Direction.values()) {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(face));
            IFluidHandler sourceHandler = blockEntity == null
                    ? null
                    : blockEntity
                            .getCapability(ForgeCapabilities.FLUID_HANDLER, face.getOpposite())
                            .orElse(null);
            if (sourceHandler != null) {
                if (!FluidUtil.tryFluidTransfer(tank, sourceHandler, tank.getCapacity(), true)
                        .isEmpty()) {
                    return;
                }
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        tank.writeToNBT(data);
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        tank.readFromNBT(data);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.put("neo_tank", tank.writeToNBT(new CompoundTag()));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        if (tag.contains("neo_tank")) {
            tank.readFromNBT(tag.getCompound("neo_tank"));
        }
    }

    @Override
    @Nullable public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidHandlerCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidHandlerCap.invalidate();
    }
}
