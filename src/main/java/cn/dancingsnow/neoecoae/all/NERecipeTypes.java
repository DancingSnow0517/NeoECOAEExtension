package cn.dancingsnow.neoecoae.all;


import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class NERecipeTypes {
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPE = DeferredRegister.create(Registries.RECIPE_TYPE, NeoECOAE.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZER = DeferredRegister.create(Registries.RECIPE_SERIALIZER, NeoECOAE.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<CoolingRecipe>> COOLING = registerRecipe("cooling");
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CoolingRecipe>> COOLING_SERIALIZER = RECIPE_SERIALIZER.register(
        "cooling",
        CoolingRecipe.Serializer::new
    );

    private static <T extends Recipe<?>> DeferredHolder<RecipeType<?>, RecipeType<T>> registerRecipe(String name) {
        return RECIPE_TYPE.register(name, () -> new RecipeType<>() {
            @Override
            public String toString() {
                return NeoECOAE.MOD_ID + ":" + name;
            }
        });
    }

    public static void register(IEventBus bus) {
        RECIPE_TYPE.register(bus);
        RECIPE_SERIALIZER.register(bus);
    }
}
