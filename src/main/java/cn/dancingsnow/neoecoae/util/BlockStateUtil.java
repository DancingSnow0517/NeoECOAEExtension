package cn.dancingsnow.neoecoae.util;

import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;

public class BlockStateUtil {

    public static <T extends Block> void simpleExistingBlockState(
            DataGenContext<Block, T> ctx, RegistrateBlockstateProvider provider) {
        ModelFile model = provider.models().getExistingFile(provider.modLoc("block/" + ctx.getName()));
        provider.getVariantBuilder(ctx.get())
                .forAllStates(
                        state -> ConfiguredModel.builder().modelFile(model).build());
    }
}
