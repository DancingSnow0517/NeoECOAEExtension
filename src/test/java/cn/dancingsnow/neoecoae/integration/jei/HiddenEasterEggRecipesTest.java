package cn.dancingsnow.neoecoae.integration.jei;

import cn.dancingsnow.neoecoae.integration.xei.HiddenEasterEggRecipes;
import java.util.List;
import java.util.stream.Stream;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HiddenEasterEggRecipesTest {
    @Test void hidesOnlyTheEasterEggWithoutRemovingOtherMachineRecipes() {
        IRecipeManager manager = mock(IRecipeManager.class);
        RecipeType<Recipe> type = new RecipeType<>(new ResourceLocation("mekanism", "metallurgic_infusing"), Recipe.class);
        IRecipeLookup<Recipe> lookup = mock(IRecipeLookup.class);
        Recipe<?> secret = mock(Recipe.class);
        Recipe<?> normal = mock(Recipe.class);
        when(secret.getId()).thenReturn(HiddenEasterEggRecipes.INFINITE_SAND);
        when(normal.getId()).thenReturn(new ResourceLocation("mekanism", "alloy/infused"));
        when(manager.createRecipeLookup(type)).thenReturn(lookup);
        when(lookup.includeHidden()).thenReturn(lookup);
        when(lookup.get()).thenReturn(Stream.of(secret, normal));
        HiddenJeiRecipes.hide(manager, type);
        verify(manager).hideRecipes(type, List.of(secret));
        assertFalse(HiddenEasterEggRecipes.isHidden(null));
        assertFalse(HiddenEasterEggRecipes.isHidden(normal.getId()));
        assertTrue(HiddenEasterEggRecipes.isHidden(new ResourceLocation("jei",
            "/neoecoae/compat/useless_stretcher/infinite_sand_cell")));
    }
}
