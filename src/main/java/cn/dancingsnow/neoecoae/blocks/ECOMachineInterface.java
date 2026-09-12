package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public class ECOMachineInterface<C extends NECluster<C>> extends NEBlock<ECOMachineInterfaceBlockEntity<C>> implements BlockUIMenuType.BlockUI {
    public static final EnumProperty<cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode> STORAGE_MODE =
        EnumProperty.create("storage_mode", cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode.class);

    public ECOMachineInterface(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STORAGE_MODE,
            cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode.STORAGE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STORAGE_MODE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ECOMachineInterfaceBlockEntity<C> blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null || !blockEntity.supportsInterfaceUi()) {
            return InteractionResult.PASS;
        }
        if (blockEntity.supportsIntegratedWorkingStationInterfaceUi()) {
            if (player instanceof ServerPlayer serverPlayer) {
                blockEntity.openMenu(serverPlayer, MenuLocators.forBlockEntity(blockEntity));
                return InteractionResult.CONSUME;
            }
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            BlockUIMenuType.openUI(serverPlayer, pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        if (holder.player.level().getBlockEntity(holder.pos) instanceof ECOMachineInterfaceBlockEntity<?> be) {
            return be.createUI(holder);
        }
        return null;
    }

    @Override
    public boolean stillValid(BlockUIMenuType.BlockUIHolder holder) {
        return BlockUIMenuType.BlockUI.super.stillValid(holder)
            && holder.player.level().getBlockEntity(holder.pos) instanceof ECOMachineInterfaceBlockEntity<?> be
            && be.supportsInterfaceUi();
    }

    @Override
    protected boolean hideWhenFormed() {
        return true;
    }
}
