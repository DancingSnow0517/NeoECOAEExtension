package cn.dancingsnow.neoecoae.integration.kubejs;

import cn.dancingsnow.neoecoae.integration.kubejs.recipe.IntegratedWorkingStationSchema;
import cn.dancingsnow.neoecoae.integration.kubejs.recipe.IntegratedWorkingStationSchema.IntegratedWorkingStationKubeRecipe;
import cn.dancingsnow.neoecoae.integration.kubejs.recipe.LargeIntegratedWorkingStationSchema;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeIntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.recipe.RecipeTypeFunction;
import dev.latvian.mods.kubejs.recipe.RecipesKubeEvent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaType;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeIntegratedWorkingStationSchemaTest {
    private static final ResourceLocation TYPE = ResourceLocation.parse("neoecoae:large_integrated_working_station");

    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName("cn.dancingsnow.neoecoae.NeoECOAE");
        }
    }

    @Test
    void pluginIsDiscoverableAndRegistersAllRecipeSchemas() throws Exception {
        try (var stream = getClass().getResourceAsStream("/kubejs.plugins.txt")) {
            assertNotNull(stream);
            assertEquals(NEKubeJSPlugin.class.getName() + " neoecoae",
                new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim());
        }
        var registry = mock(RecipeSchemaRegistry.class);
        new NEKubeJSPlugin().registerRecipeSchemas(registry);
        verify(registry).register(TYPE, LargeIntegratedWorkingStationSchema.SCHEMA);
        verify(registry).register(ResourceLocation.parse("neoecoae:integrated_working_station"), IntegratedWorkingStationSchema.SCHEMA);
        verify(registry).register(eq(ResourceLocation.parse("neoecoae:cooling")), any(dev.latvian.mods.kubejs.recipe.schema.RecipeSchema.class));
    }

    @Test
    void supportsEnergyOnlyAndPositionalConstructors() {
        var schema = LargeIntegratedWorkingStationSchema.SCHEMA;
        assertEquals(TYPE, schema.recipeFactory.id());
        assertEquals(1, schema.constructors().get(1).keys.size());
        assertEquals(5, schema.constructors().get(5).keys.size());
        assertEquals(6, schema.constructors().get(6).keys.size());
        assertEquals(IntegratedWorkingStationSchema.PROCESSING_TICKS, schema.constructors().get(6).keys.getLast());
    }

    @Test
    void fluentRecipeSerializesTwelveInputsAndBothOutputsForNativeCodec() throws Exception {
        var recipe = recipe().energy(10000).processingTicks(240)
            .requireFluid(SizedFluidIngredient.of(Fluids.WATER, 1000))
            .itemOutput(new ItemStack(Items.GOLD_INGOT, 2))
            .fluidOutput(new FluidStack(Fluids.LAVA, 250));
        for (int i = 0; i < 12; i++) recipe.require(SizedIngredient.of(Items.IRON_INGOT, 2));
        recipe.serialize();

        var decoded = decode(recipe.json).display();
        assertEquals(12, decoded.inputItems().size());
        assertTrue(decoded.inputItems().stream().allMatch(input -> input.count() == 2));
        assertEquals(10000, decoded.energy());
        assertEquals(240, decoded.processingTicks());
        assertEquals(1000, decoded.inputFluid().amount());
        assertTrue(decoded.inputFluid().test(new FluidStack(Fluids.WATER, 1000)));
        assertEquals(Items.GOLD_INGOT, decoded.itemOutput().getItem());
        assertEquals(2, decoded.itemOutput().getCount());
        assertEquals(Fluids.LAVA, decoded.fluidOutput().getFluid());
        assertEquals(250, decoded.fluidOutput().getAmount());
        assertEquals(TYPE.toString(), recipe.json.get("type").getAsString());
        assertTrue(IntegratedWorkingStationRecipe.Serializer.CODEC.codec()
            .parse(RegistryAccessContainer.BUILTIN.json(), recipe.json).error().isPresent());
    }

    @Test
    void itemOnlyRecipeUsesNativeDefaultsAndDoesNotShareInputLists() throws Exception {
        var first = recipe().energy(20).require(SizedIngredient.of(Items.IRON_INGOT, 1))
            .itemOutput(new ItemStack(Items.GOLD_INGOT));
        first.serialize();
        var decoded = decode(first.json).display();
        assertEquals(100, decoded.processingTicks());
        assertTrue(decoded.inputFluid().ingredient().isEmpty());
        assertTrue(decoded.fluidOutput().isEmpty());

        var second = recipe().energy(30).require(SizedIngredient.of(Items.REDSTONE, 3))
            .itemOutput(new ItemStack(Items.DIAMOND));
        second.serialize();
        assertEquals(1, decode(first.json).display().inputItems().size());
        assertEquals(1, decode(second.json).display().inputItems().size());
        assertTrue(decode(second.json).display().inputItems().getFirst().ingredient().test(new ItemStack(Items.REDSTONE)));
    }

    @Test
    void nativeCodecRejectsMoreThanTwelveInputsAndNonPositiveTime() throws Exception {
        var recipe = recipe().energy(1).itemOutput(new ItemStack(Items.GOLD_INGOT));
        for (int i = 0; i < 13; i++) recipe.require(SizedIngredient.of(Items.IRON_INGOT, 1));
        recipe.serialize();
        assertTrue(LargeIntegratedWorkingStationRecipe.Serializer.CODEC.codec()
            .parse(RegistryAccessContainer.BUILTIN.json(), recipe.json).error().isPresent());

        var valid = recipe().energy(1).require(SizedIngredient.of(Items.IRON_INGOT, 1))
            .itemOutput(new ItemStack(Items.GOLD_INGOT));
        valid.serialize();
        valid.json.addProperty("processingTicks", 0);
        assertTrue(LargeIntegratedWorkingStationRecipe.Serializer.CODEC.codec()
            .parse(RegistryAccessContainer.BUILTIN.json(), valid.json).error().isPresent());
    }

    private static IntegratedWorkingStationKubeRecipe recipe() throws Exception {
        // Supply registry ops normally provided by the server's recipe event, without starting a server.
        var event = mock(RecipesKubeEvent.class);
        var ops = RecipesKubeEvent.class.getField("ops");
        ops.setAccessible(true);
        ops.set(event, RegistryAccessContainer.BUILTIN);
        var schema = LargeIntegratedWorkingStationSchema.SCHEMA;
        var type = new RecipeTypeFunction(event, new RecipeSchemaType(null, TYPE, schema));
        var recipe = (IntegratedWorkingStationKubeRecipe) schema.recipeFactory.create(type, null, true);
        recipe.json = new JsonObject();
        recipe.json.addProperty("type", type.idString);
        return recipe;
    }

    private static LargeIntegratedWorkingStationRecipe decode(JsonObject json) {
        return LargeIntegratedWorkingStationRecipe.Serializer.CODEC.codec()
            .parse(RegistryAccessContainer.BUILTIN.json(), json).getOrThrow();
    }
}
