package cn.dancingsnow.neoecoae.data.recipe;

import appeng.core.definitions.AEItems;
import appeng.datagen.providers.tags.ConventionTags;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import cn.dancingsnow.neoecoae.platform.recipe.ForgeRecipeOutputs;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipeBuilder;

public class AAERecipes {
    public static void init(RegistrateRecipeProvider provider) {
        var advancedAeInstalled = ForgeRecipeOutputs.withConditions(provider, new ModLoadedCondition("advanced_ae"));

        ReactionChamberRecipeBuilder.react(NEItems.ENERGIZED_CRYSTAL, 64, 500000)
            .input(AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED, 32)
            .input(NETags.Items.ENERGIZED_CRYSTAL_DUST, 32)
            .fluid(Fluids.WATER, 500)
            .save(advancedAeInstalled, NeoECOAE.id("reaction_chamber/energized_crystal"));

        ReactionChamberRecipeBuilder.react(NEItems.ENERGIZED_FLUIX_CRYSTAL, 64, 500000)
            .input(NETags.Items.ENERGIZED_CRYSTAL_DUST, 64)
            .input(ConventionTags.FLUIX_CRYSTAL, 64)
            .fluid(Fluids.WATER, 500)
            .save(advancedAeInstalled, NeoECOAE.id("reaction_chamber/energized_fluix_crystal"));
    }
}
