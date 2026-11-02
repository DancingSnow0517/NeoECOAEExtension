package cn.dancingsnow.neoecoae.integration.jei;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.integration.jei.categories.LargeWorkingStationCategory;
import cn.dancingsnow.neoecoae.integration.recipe.LargeWorkstationRecipeDisplay;
import cn.dancingsnow.neoecoae.mixins.client.jei.RecipeLayoutBuilderMixin;
import cn.dancingsnow.neoecoae.mixins.client.jei.RecipeLayoutMixin;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkstationRecipeHeightTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void ordinaryRecipesStayCompactAfterExpandedRecipesAndCardsRemainVisible() throws Exception {
        var category = mock(LargeWorkingStationCategory.class);
        var small = recipe(List.of(), List.of());
        var fluid = mock(SizedFluidIngredient.class);
        when(fluid.ingredient()).thenReturn(net.neoforged.neoforge.fluids.crafting.FluidIngredient.of(
            net.minecraft.world.level.material.Fluids.WATER));
        when(fluid.amount()).thenReturn(1000);
        var expanded = recipe(List.of(fluid), List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)));
        assertHeight(136, category, expanded);
        assertHeight(96, category, small);

        var provider = ResourceLocation.parse("neoecoae:height_test");
        LargeWorkstationRecipeDisplay.registerCardProvider(provider, ignored -> java.util.Collections.nCopies(5,
            new LargeWorkstationRecipeDisplay.DisplayCard(new ItemStack(Items.DIAMOND), List.of())));
        try {
            assertHeight(114, category, small);
            assertHeight(154, category, expanded);
        } finally {
            LargeWorkstationRecipeDisplay.registerCardProvider(provider, ignored -> List.of());
        }
        assertHeight(96, category, small);
        assertHeight(140, mock(IRecipeCategory.class), expanded);
        assertHeight(140, category, new Object());
    }

    private static LargeWorkstationRecipe recipe(List<SizedFluidIngredient> fluids, List<GenericStack> outputs) {
        var display = new cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe(List.of(),
            new SizedFluidIngredient(net.neoforged.neoforge.fluids.crafting.FluidIngredient.empty(), 1),
            ItemStack.EMPTY, net.neoforged.neoforge.fluids.FluidStack.EMPTY, 200_000);
        return new LargeWorkstationRecipe(ResourceLocation.parse("neoecoae:height_test"), display, 200_000,
            List.of(), LargeWorkstationRecipe.NATIVE_TYPE, fluids, outputs);
    }

    private static void assertHeight(int expected, IRecipeCategory<?> category, Object recipe) throws Exception {
        var layout = RecipeLayoutMixin.class.getDeclaredMethod("neoecoae$recipeHeight",
            IRecipeCategory.class, Operation.class, Object.class);
        layout.setAccessible(true);
        Operation<Integer> original = args -> {
            assertArrayEquals(new Object[] {category}, args);
            return 140;
        };
        assertEquals(expected, layout.invoke(new RecipeLayoutMixin() {}, category, original, recipe));
        var builder = new RecipeLayoutBuilderMixin() {};
        var recipeField = RecipeLayoutBuilderMixin.class.getDeclaredField("recipe");
        recipeField.setAccessible(true);
        recipeField.set(builder, recipe);
        var button = RecipeLayoutBuilderMixin.class.getDeclaredMethod("neoecoae$recipeHeight", int.class,
            IRecipeCategory.class);
        button.setAccessible(true);
        assertEquals(expected, button.invoke(builder, 140, category));
    }

    @Test void installedJeiRetainsBothHeightHooksAndLiveCapturedArguments() throws Exception {
        var layout = read("mezz/jei/library/gui/recipes/RecipeLayout");
        var constructor = layout.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        var args = Arrays.asList(Type.getArgumentTypes(constructor.desc));
        assertEquals(1, args.stream().filter(t -> t.equals(Type.getType(IRecipeCategory.class))).count());
        assertEquals(1, args.stream().filter(t -> t.equals(Type.getType(Object.class))).count());
        assertLiveCapturedArguments(layout, constructor, RecipeLayoutMixin.class);
        var builder = read("mezz/jei/library/gui/recipes/layout/builder/RecipeLayoutBuilder");
        assertTrue(builder.fields.stream().anyMatch(f -> f.name.equals("recipe") && f.desc.equals("Ljava/lang/Object;")));
        var button = builder.methods.stream().filter(m -> m.name.equals("getRecipeTransferButtonPosition"))
            .findFirst().orElseThrow();
        assertLiveCapturedArguments(builder, button, RecipeLayoutBuilderMixin.class);
    }

    private static void assertLiveCapturedArguments(ClassNode owner, MethodNode method, Class<?> mixin)
        throws Exception {
        var hook = Arrays.stream(mixin.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals("neoecoae$recipeHeight"))
            .findFirst().orElseThrow();
        var frames = new Analyzer<>(new BasicInterpreter()).analyze(owner.name, method);
        long calls = 0;
        for (int i = 0; i < method.instructions.size(); i++) {
            var instruction = method.instructions.get(i);
            if (instruction instanceof MethodInsnNode call
                && call.owner.equals("mezz/jei/api/recipe/category/IRecipeCategory")
                && call.name.equals("getHeight") && call.desc.equals("()I")) {
                calls++;
                // Constructor arguments may have been overwritten by JEI's bytecode optimizer.
                // Checking the descriptor alone does not prove a @Local capture is safe here.
                for (var parameter : hook.getParameters()) {
                    var local = parameter.getAnnotation(Local.class);
                    if (local == null) continue;
                    assertTrue(local.argsOnly(), "This check expects argument-only captures");
                    int slot = argumentSlot(method, parameter.getType());
                    assertTrue(frames[i].getLocal(slot).isReference(),
                        () -> hook + " captures a non-reference from argument slot " + slot);
                }
            }
        }
        assertEquals(1, calls, method.name);
    }

    private static int argumentSlot(MethodNode method, Class<?> capturedType) {
        int slot = 1;
        int match = -1;
        for (var type : Type.getArgumentTypes(method.desc)) {
            if (type.equals(Type.getType(capturedType))) {
                assertEquals(-1, match, "Ambiguous captured argument: " + capturedType);
                match = slot;
            }
            slot += type.getSize();
        }
        assertTrue(match >= 0, "Missing captured argument: " + capturedType);
        return match;
    }

    private static ClassNode read(String name) throws Exception {
        try (var input = WorkstationRecipeHeightTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
