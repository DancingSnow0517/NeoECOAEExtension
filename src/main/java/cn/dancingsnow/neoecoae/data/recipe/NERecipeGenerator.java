package cn.dancingsnow.neoecoae.data.recipe;

import com.tterrag.registrate.providers.RegistrateRecipeProvider;

public class NERecipeGenerator {
    public static void accept(RegistrateRecipeProvider provider) {
        EcoMachineRecipes.init(provider);
        CoolingRecipes.init(provider);

        MekanismRecipes.init(provider);
        EAERecipes.init(provider);
    }
}
