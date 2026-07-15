package cn.dancingsnow.neoecoae.data.recipe;

import cn.dancingsnow.neoecoae.datagen.EAERecipeData;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;

public class EAERecipes {
    public static void init(RegistrateRecipeProvider provider) {
        EAERecipeData.init(provider);
    }
}
