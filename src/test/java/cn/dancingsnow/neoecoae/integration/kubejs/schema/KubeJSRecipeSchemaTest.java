package cn.dancingsnow.neoecoae.integration.kubejs.schema;

import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.fluid.InputFluid;
import dev.latvian.mods.kubejs.item.InputItem;
import dev.latvian.mods.kubejs.recipe.ItemMatch;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.RecipeTypeFunction;
import dev.latvian.mods.kubejs.recipe.ReplacementMatch;
import dev.latvian.mods.kubejs.recipe.component.ComponentValueMap;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KubeJSRecipeSchemaTest {
    private static RecipeJS recipe(RecipeSchema schema, String name) {
        var type = new RecipeSchemaType(null, new ResourceLocation("neoecoae", name), schema);
        var constructor = schema.constructors().get(0);
        return constructor.factory().create(new RecipeTypeFunction(null, type), type,
                constructor.keys(), new ComponentValueMap(0));
    }

    @Test void noArgumentConstructorWritesDefaultEnergyForEveryWorkstationType() {
        for (String name : new String[] {"integrated_working_station", "large_integrated_working_station", "extended_crystal_assembler"}) {
            var recipe = recipe(NecoIntegratedWorkingStationSchema.SCHEMA, name);
            recipe.serialize();
            assertEquals(1000, recipe.json.get("energy").getAsInt(), name);
        }
    }

    @Test void explicitEnergyOverridesTheDefault() {
        var recipe = (IntegratedWorkingStationRecipeJS) recipe(NecoIntegratedWorkingStationSchema.SCHEMA, "integrated_working_station");
        recipe.energy(2500);
        recipe.serialize();
        assertEquals(2500, recipe.json.get("energy").getAsInt());
    }

    @Test void workstationInputFluidParticipatesInRecipeFiltering() {
        var recipe = recipe(NecoIntegratedWorkingStationSchema.SCHEMA, "integrated_working_station");
        InputFluid fluid = () -> 1000L;
        recipe.setValue(NecoIntegratedWorkingStationSchema.INPUT_FLUID, fluid);
        assertTrue(recipe.hasInput(fluid));
        assertFalse(recipe.hasInput(mock(ReplacementMatch.class)));
    }

    @Test void coolingInputFluidParticipatesInRecipeFiltering() {
        InputFluid fluid = () -> 1000L;
        assertTrue(NecoCoolingSchema.INPUT.component.isInput(null, fluid, fluid));
        assertFalse(NecoCoolingSchema.INPUT.component.isInput(null, fluid, mock(ReplacementMatch.class)));
    }

    @Test void sizedItemsParticipateInFilteringAndReplacementWithoutLosingTheirCount() {
        // Ingredient matching capability is normally supplied by KubeJS launcher mixins.
        var ingredient = mock(Ingredient.class);
        var input = spy(InputItem.of(ingredient, 4));
        doReturn(true).when(input).validForMatching();
        var match = mock(ItemMatch.class);
        when(match.contains(ingredient)).thenReturn(true);
        var recipe = recipe(NecoIntegratedWorkingStationSchema.SCHEMA, "integrated_working_station");
        recipe.setValue(NecoIntegratedWorkingStationSchema.INPUT_ITEMS, new InputItem[] {input});
        assertTrue(recipe.hasInput(match));
        assertFalse(recipe.hasInput(mock(ItemMatch.class)));

        var replacementIngredient = mock(Ingredient.class);
        assertTrue(recipe.replaceInput(match, InputItem.of(replacementIngredient, 1)));
        var replaced = recipe.getValue(NecoIntegratedWorkingStationSchema.INPUT_ITEMS)[0];
        assertSame(replacementIngredient, replaced.ingredient);
        assertEquals(4, replaced.count);
    }

    @Test void customFluidTagSerializationIsPreserved() {
        var fluid = new TagInputFluid(new ResourceLocation("forge", "water"), 250);
        var recipe = recipe(NecoIntegratedWorkingStationSchema.SCHEMA, "integrated_working_station");
        JsonObject json = NecoRecipeComponents.ID_COMPAT_INPUT_FLUID.write(recipe, fluid).getAsJsonObject();
        assertEquals("forge:water", json.get("tag").getAsString());
        assertEquals(250, json.get("amount").getAsInt());
        assertEquals(fluid, NecoRecipeComponents.ID_COMPAT_INPUT_FLUID.read(recipe, json));
    }
}
