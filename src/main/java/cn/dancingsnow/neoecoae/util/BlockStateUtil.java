package cn.dancingsnow.neoecoae.util;

import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.generators.RegistrateBlockModelGenerator;
import net.minecraft.world.level.block.Block;

public class BlockStateUtil {

    public static <T extends Block> void simpleExistingBlockState(DataGenContext<Block, T> ctx, RegistrateBlockModelGenerator provider) {
        provider.create(ctx.get(), provider.modLoc("block/" + ctx.getName()));
    }
}
