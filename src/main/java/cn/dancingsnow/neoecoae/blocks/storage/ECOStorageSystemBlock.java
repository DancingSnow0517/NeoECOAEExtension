package cn.dancingsnow.neoecoae.blocks.storage;

import cn.dancingsnow.neoecoae.blocks.AbstractECOSystemBlock;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class ECOStorageSystemBlock extends AbstractECOSystemBlock<ECOStorageSystemBlockEntity> {
    public ECOStorageSystemBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof ECOStorageSystemBlockEntity storageHost) {
            storageHost.restoreInfiniteDomainFromItem(stack);
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof ECOStorageSystemBlockEntity storageHost) {
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    storageHost.applyInfiniteDomainToControllerDrop(drop);
                    break;
                }
            }
        }
        return drops;
    }
}
