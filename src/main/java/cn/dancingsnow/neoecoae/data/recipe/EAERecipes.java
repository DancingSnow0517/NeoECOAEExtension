package cn.dancingsnow.neoecoae.data.recipe;

import appeng.core.definitions.AEItems;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import com.glodblock.github.extendedae.recipe.CrystalAssemblerRecipeBuilder;
import com.glodblock.github.extendedae.recipe.CrystalFixerRecipeBuilder;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import net.minecraft.data.recipes.RecipeOutput;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

public class EAERecipes {
    public static void init(RegistrateRecipeProvider provider) {
        RecipeOutput extendedaeInstalled = provider.withConditions(new ModLoadedCondition("extendedae"));

        // 水晶修复
        CrystalFixerRecipeBuilder.fixer(NEBlocks.ENERGIZED_CRYSTAL_BLOCK.get(), NEBlocks.DAMAGED_BUDDING_ENERGIZED_CRYSTAL.get())
            .chance(0.8)
            .fuel(NETags.Items.ENERGIZED_CRYSTAL)
            .save(extendedaeInstalled, NeoECOAE.id("crystal_fixer/damaged_budding_energized_crystal"));

        CrystalFixerRecipeBuilder.fixer(NEBlocks.DAMAGED_BUDDING_ENERGIZED_CRYSTAL.get(), NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL.get())
            .chance(0.8)
            .fuel(NETags.Items.ENERGIZED_CRYSTAL)
            .save(extendedaeInstalled, NeoECOAE.id("crystal_fixer/chipped_budding_energized_crystal"));

        CrystalFixerRecipeBuilder.fixer(NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL.get(), NEBlocks.FLAWED_BUDDING_ENERGIZED_CRYSTAL.get())
            .chance(0.05)
            .fuel(NETags.Items.ENERGIZED_CRYSTAL)
            .save(extendedaeInstalled, NeoECOAE.id("crystal_fixer/flawed_budding_energized_crystal"));

        // 水晶装配器
        CrystalAssemblerRecipeBuilder.assemble(NEItems.SUPERCONDUCTING_PROCESSOR, 4)
            .input(NEItems.SUPERCONDUCTING_PROCESSOR_PRINT, 4)
            .input(NEItems.CRYSTAL_MATRIX, 4)
            .input(AEItems.SILICON_PRINT, 4)
            .save(extendedaeInstalled, NeoECOAE.id("crystal_assembler/superconducting_processor"));
    }
}
