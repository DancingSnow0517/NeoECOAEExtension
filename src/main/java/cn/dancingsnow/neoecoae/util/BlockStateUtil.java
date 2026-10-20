package cn.dancingsnow.neoecoae.util;

import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import net.minecraft.world.level.block.Block;

public class BlockStateUtil {

    public static <T extends Block> void simpleExistingBlockState(DataGenContext<Block, T> ctx, RegistrateBlockstateProvider provider) {
        provider.simpleBlock(ctx.get(), provider.models().getExistingFile(provider.modLoc("block/" + ctx.getName())));
    }
}
