package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationOptionalIntegrationTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void ordinaryRecipeSourcesLoadAndRemainUsableWithoutLightningTechClasses() throws Exception {
        var recipe = recipe("neoecoae:integrated_working_station");
        var sources = sourcesWithoutLightningTech();
        var direct = method(sources, "direct", LargeWorkstationRecipe.class);

        var variant = (LargeWorkstationRecipeGroup.Variant) direct.invoke(null, recipe);

        assertSame(recipe, variant.recipe());
        assertTrue(variant.unrestricted());
        assertFalse(variant.borrowed());
    }

    @Test void absentLightningTechSkipsBorrowingBeforeInspectingAnExternalRecipe() throws Exception {
        try (var mods = mockStatic(ModList.class)) {
            var sources = sourcesWithoutLightningTech();
            var borrowed = method(sources, "borrowed", RecipeHolder.class, LargeWorkstationRecipe.class);
            var holder = mock(RecipeHolder.class);

            assertNull(borrowed.invoke(null, holder, recipe("advanced_ae:reaction")));
            verifyNoInteractions(holder);
        }
    }

    private static Class<?> sourcesWithoutLightningTech() throws ClassNotFoundException {
        String owner = "cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipeSources";
        var parent = LargeWorkstationOptionalIntegrationTest.class.getClassLoader();
        var loader = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.moakiee.ae2lt.")) throw new ClassNotFoundException(name);
                if (!name.equals(owner) && !name.startsWith(owner + "$")) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    try (var stream = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (stream == null) throw new ClassNotFoundException(name);
                        byte[] bytes = stream.readAllBytes();
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        };
        return Class.forName(owner, true, loader);
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws NoSuchMethodException {
        var method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static LargeWorkstationRecipe recipe(String type) {
        var display = new IntegratedWorkingStationRecipe(
            List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), new ItemStack(Items.GOLD_INGOT), FluidStack.EMPTY, 100);
        return new LargeWorkstationRecipe(ResourceLocation.parse("test:optional_integration"), display, 100,
            List.of(), ResourceLocation.parse(type));
    }
}
