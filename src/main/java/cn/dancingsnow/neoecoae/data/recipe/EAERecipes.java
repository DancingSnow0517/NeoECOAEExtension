package cn.dancingsnow.neoecoae.data.recipe;

import appeng.core.definitions.AEItems;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.platform.recipe.ForgeRecipeOutputs;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.glodblock.github.extendedae.recipe.CircuitCutterRecipeBuilder;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;

public class EAERecipes {
    public static void init(RegistrateRecipeProvider provider) {
        var extended = ForgeRecipeOutputs.withConditions(provider, new ModLoadedCondition("expatternprovider"));
        // ExtendedAE 1.20.1 has no crystal assembler. Keep main's exact contract in an ECO recipe type.
        IntegratedWorkingStationRecipe.builder()
            .require(NEItems.SUPERCONDUCTING_PROCESSOR_PRINT, 4)
            .require(NEItems.CRYSTAL_MATRIX, 4)
            .require(AEItems.SILICON_PRINT, 4)
            .itemOutput(NEItems.SUPERCONDUCTING_PROCESSOR, 4)
            .energy(2000)
            .saveWithSerializer(extended, NeoECOAE.id("crystal_assembler/superconducting_processor"),
                NERecipeTypes.EXTENDED_CRYSTAL_ASSEMBLER_SERIALIZER.get());

        CircuitCutterRecipeBuilder.cut(NEItems.SUPERCONDUCTING_PROCESSOR_PRINT, 9)
            .input(NEBlocks.ENERGIZED_SUPERCONDUCTIVE_BLOCK)
            .save(extended, NeoECOAE.id("circuit_cutter/superconducting_processor_print"));
    }
}
