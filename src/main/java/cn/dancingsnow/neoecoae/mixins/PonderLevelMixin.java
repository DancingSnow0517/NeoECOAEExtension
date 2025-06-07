package cn.dancingsnow.neoecoae.mixins;

import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.extensions.IBlockGetterExtension;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PonderLevel.class)
public class PonderLevelMixin extends SchematicLevel implements IBlockGetterExtension {
    public PonderLevelMixin(Level original) {
        super(original);
    }

    @Override
    public ModelData getModelData(BlockPos pos) {
        BlockEntity be = getBlockEntity(pos);
        //noinspection ConstantValue
        if (be != null) {
            return be.getModelData();
        }
        return super.getModelData(pos);
    }
}
