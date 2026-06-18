package cn.dancingsnow.neoecoae.integration.jei;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiBlockInfoWrapper;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;

public class NEJeiRecipeType {
    public static final IRecipeType<MultiBlockInfoWrapper> MULTIBLOCK =
        IRecipeType.create(NeoECOAE.id("multiblock"), MultiBlockInfoWrapper.class);

    public static final IRecipeHolderType<CoolingRecipe> COOLING = IRecipeHolderType.create(NERecipeTypes.COOLING.get());

    public static final IRecipeHolderType<IntegratedWorkingStationRecipe> INTEGRATED_WORKING_STATION = IRecipeHolderType.create(NERecipeTypes.INTEGRATED_WORKING_STATION.get());
}
