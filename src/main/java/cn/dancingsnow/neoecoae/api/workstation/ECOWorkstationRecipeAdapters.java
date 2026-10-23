package cn.dancingsnow.neoecoae.api.workstation;

import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

/** Adapts complete external recipe contracts, including energy and extra resource costs. */
public final class ECOWorkstationRecipeAdapters {
    private static final ECOExtensionRegistry<Registration> REGISTRY = new ECOExtensionRegistry<>();
    private ECOWorkstationRecipeAdapters() {}
    @FunctionalInterface public interface Adapter {
        LargeWorkstationRecipe adapt(RecipeHolder<?> recipe, HolderLookup.Provider registries);
    }
    private record Registration(ResourceLocation sourceType, Adapter adapter) {}
    public static void register(ResourceLocation id, int priority, ResourceLocation sourceType, Adapter adapter) {
        REGISTRY.register(id, priority, new Registration(Objects.requireNonNull(sourceType), Objects.requireNonNull(adapter)));
    }
    public static boolean unregister(ResourceLocation id) { return REGISTRY.unregister(id); }
    public static long revision() { return REGISTRY.revision(); }
    @Nullable public static LargeWorkstationRecipe adapt(ResourceLocation sourceType, RecipeHolder<?> recipe,
                                                        HolderLookup.Provider registries) {
        for (var entry : REGISTRY.entries()) {
            if (!entry.extension().sourceType().equals(sourceType)) continue;
            var result = Objects.requireNonNull(entry.extension().adapter().adapt(recipe, registries), "Adapted recipe");
            if (!recipe.id().equals(result.id()) || !sourceType.equals(result.sourceType()))
                throw new IllegalArgumentException("Adapter changed recipe identity or source type: " + recipe.id());
            return result;
        }
        return null;
    }
}
