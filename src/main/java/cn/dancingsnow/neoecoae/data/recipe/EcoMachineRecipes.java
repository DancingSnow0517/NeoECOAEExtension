package cn.dancingsnow.neoecoae.data.recipe;

import appeng.core.definitions.AEBlocks;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

public class EcoMachineRecipes {
    public static void init(RegistrateRecipeProvider provider) {
        //ECO - CE4
        IntegratedWorkingStationRecipe.builder()
            .require(NEItems.ECO_CELL_COMPONENT_16M, 4)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 64)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 4)
            .require(NEItems.CRYSTAL_MATRIX)
            .itemOutput(NEItems.ECO_COMPUTATION_CELL_L4)
            .energy(64000)
            .save(provider);
        //ECO - CE6
        IntegratedWorkingStationRecipe.builder()
            .require(NEItems.ECO_CELL_COMPONENT_64M, 4)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 64)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 16)
            .require(NEItems.CRYSTAL_MATRIX)
            .itemOutput(NEItems.ECO_COMPUTATION_CELL_L6)
            .energy(256000)
            .save(provider);
        //ECO - CE9
        IntegratedWorkingStationRecipe.builder()
            .require(NEItems.ECO_CELL_COMPONENT_256M, 4)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 64)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 64)
            .require(NEItems.CRYSTAL_MATRIX)
            .itemOutput(NEItems.ECO_COMPUTATION_CELL_L9)
            .energy(1024000)
            .save(provider);

        // Storage System
        IntegratedWorkingStationRecipe.builder()
            .require(NEBlocks.STORAGE_CASING, 4)
            .require(AEBlocks.DRIVE, 4)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 16)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 16)
            .itemOutput(NEBlocks.STORAGE_SYSTEM_L4)
            .energy(16000)
            .save(provider);

        IntegratedWorkingStationRecipe.builder()
            .require(NEBlocks.STORAGE_SYSTEM_L4)
            .require(AEBlocks.DRIVE, 8)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 32)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 32)
            .itemOutput(NEBlocks.STORAGE_SYSTEM_L6)
            .energy(160000)
            .save(provider);

        IntegratedWorkingStationRecipe.builder()
            .require(NEBlocks.STORAGE_SYSTEM_L6)
            .require(AEBlocks.DRIVE, 16)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 64)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 64)
            .itemOutput(NEBlocks.STORAGE_SYSTEM_L9)
            .energy(640000)
            .save(provider);

        // computation system
        IntegratedWorkingStationRecipe.builder()
            .require(NEBlocks.COMPUTATION_CASING, 4)
            .require(NEBlocks.COMPUTATION_PARALLEL_CORE_L4, 2)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 16)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 16)
            .itemOutput(NEBlocks.COMPUTATION_SYSTEM_L4)
            .energy(16000)
            .save(provider);

        IntegratedWorkingStationRecipe.builder()
            .require(NEBlocks.COMPUTATION_SYSTEM_L4)
            .require(NEBlocks.COMPUTATION_PARALLEL_CORE_L6, 2)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 32)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 32)
            .itemOutput(NEBlocks.COMPUTATION_SYSTEM_L6)
            .energy(160000)
            .save(provider);

        IntegratedWorkingStationRecipe.builder()
            .require(NEBlocks.COMPUTATION_SYSTEM_L6)
            .require(NEBlocks.COMPUTATION_PARALLEL_CORE_L9, 2)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 64)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 64)
            .itemOutput(NEBlocks.COMPUTATION_SYSTEM_L9)
            .energy(640000)
            .save(provider);

        // computation threading core
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, NEBlocks.COMPUTATION_THREADING_CORE_L4)
            .pattern("ABA")
            .pattern("ACA")
            .pattern("ABA")
            .define('A', AEBlocks.CRAFTING_STORAGE_256K)
            .define('B', NEItems.SUPERCONDUCTING_PROCESSOR)
            .define('C', NEBlocks.COMPUTATION_CASING)
            .unlockedBy("has_computation_casing", RegistrateRecipeProvider.has(NEBlocks.COMPUTATION_CASING))
            .save(provider);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, NEBlocks.COMPUTATION_THREADING_CORE_L6)
            .pattern("ABA")
            .pattern("ABA")
            .pattern("ABA")
            .define('A', NEBlocks.COMPUTATION_THREADING_CORE_L4)
            .define('B', NEItems.SUPERCONDUCTING_PROCESSOR)
            .unlockedBy("has_computation_thread_core_l4", RegistrateRecipeProvider.has(NEBlocks.COMPUTATION_THREADING_CORE_L4))
            .save(provider);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, NEBlocks.COMPUTATION_THREADING_CORE_L9)
            .pattern("ABA")
            .pattern("ABA")
            .pattern("ABA")
            .define('A', NEBlocks.COMPUTATION_THREADING_CORE_L6)
            .define('B', NEItems.SUPERCONDUCTING_PROCESSOR)
            .unlockedBy("has_computation_thread_core_l6", RegistrateRecipeProvider.has(NEBlocks.COMPUTATION_THREADING_CORE_L6))
            .save(provider);

        // computation parallel core
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, NEBlocks.COMPUTATION_PARALLEL_CORE_L4)
            .pattern("ABA")
            .pattern("ACA")
            .pattern("ABA")
            .define('A', AEBlocks.CRAFTING_ACCELERATOR)
            .define('B', NEItems.SUPERCONDUCTING_PROCESSOR)
            .define('C', NEBlocks.COMPUTATION_CASING)
            .unlockedBy("has_computation_casing", RegistrateRecipeProvider.has(NEBlocks.COMPUTATION_CASING))
            .save(provider);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, NEBlocks.COMPUTATION_PARALLEL_CORE_L6)
            .pattern("ABA")
            .pattern("ABA")
            .pattern("ABA")
            .define('A', NEBlocks.COMPUTATION_PARALLEL_CORE_L4)
            .define('B', NEItems.SUPERCONDUCTING_PROCESSOR)
            .unlockedBy("has_computation_parallel_core_l4", RegistrateRecipeProvider.has(NEBlocks.COMPUTATION_PARALLEL_CORE_L4))
            .save(provider);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, NEBlocks.COMPUTATION_PARALLEL_CORE_L9)
            .pattern("ABA")
            .pattern("ABA")
            .pattern("ABA")
            .define('A', NEBlocks.COMPUTATION_PARALLEL_CORE_L6)
            .define('B', NEItems.SUPERCONDUCTING_PROCESSOR)
            .unlockedBy("has_computation_parallel_core_l6", RegistrateRecipeProvider.has(NEBlocks.COMPUTATION_PARALLEL_CORE_L6))
            .save(provider);

        // computation cooling core
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, NEBlocks.COMPUTATION_COOLING_CONTROLLER_L4)
            .pattern("ABA")
            .pattern("BCB")
            .pattern("ABA")
            .define('A', Items.BLUE_ICE)
            .define('B', NEBlocks.COMPUTATION_CASING)
            .define('C', NEItems.SUPERCONDUCTING_PROCESSOR)
            .unlockedBy("has_computation_casing", RegistrateRecipeProvider.has(NEBlocks.COMPUTATION_CASING))
            .save(provider);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, NEBlocks.COMPUTATION_COOLING_CONTROLLER_L6)
            .pattern("ABA")
            .pattern("DCD")
            .pattern("ABA")
            .define('A', NEItems.CRYOTHEUM_CRYSTAL)
            .define('B', NEItems.CRYSTAL_INGOT)
            .define('C', NEBlocks.COMPUTATION_COOLING_CONTROLLER_L4)
            .define('D', NEItems.SUPERCONDUCTING_PROCESSOR)
            .unlockedBy("has_computation_cooling_controller_l4", RegistrateRecipeProvider.has(NEBlocks.COMPUTATION_COOLING_CONTROLLER_L4))
            .save(provider);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, NEBlocks.COMPUTATION_COOLING_CONTROLLER_L9)
            .pattern("ABA")
            .pattern("DCD")
            .pattern("ABA")
            .define('A', NEItems.CRYOTHEUM_CRYSTAL)
            .define('B', NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT)
            .define('C', NEBlocks.COMPUTATION_COOLING_CONTROLLER_L6)
            .define('D', NEItems.SUPERCONDUCTING_PROCESSOR)
            .unlockedBy("has_computation_cooling_controller_l6", RegistrateRecipeProvider.has(NEBlocks.COMPUTATION_COOLING_CONTROLLER_L6))
            .save(provider);

        // crafting system
        IntegratedWorkingStationRecipe.builder()
            .require(NEBlocks.CRAFTING_CASING, 4)
            .require(NEBlocks.CRAFTING_PARALLEL_CORE_L4, 2)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 16)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 16)
            .itemOutput(NEBlocks.CRAFTING_SYSTEM_L4)
            .energy(16000)
            .save(provider);

        IntegratedWorkingStationRecipe.builder()
            .require(NEBlocks.CRAFTING_SYSTEM_L4)
            .require(NEBlocks.CRAFTING_PARALLEL_CORE_L6, 2)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 32)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 32)
            .itemOutput(NEBlocks.CRAFTING_SYSTEM_L6)
            .energy(160000)
            .save(provider);

        IntegratedWorkingStationRecipe.builder()
            .require(NEBlocks.CRAFTING_SYSTEM_L6)
            .require(NEBlocks.CRAFTING_PARALLEL_CORE_L9, 2)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 64)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 64)
            .itemOutput(NEBlocks.CRAFTING_SYSTEM_L9)
            .energy(640000)
            .save(provider);

        // crafting parallel core
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, NEBlocks.CRAFTING_PARALLEL_CORE_L4)
            .pattern("AAA")
            .pattern("ABA")
            .pattern("AAA")
            .define('A', AEBlocks.CRAFTING_ACCELERATOR)
            .define('B', NEBlocks.CRAFTING_CASING)
            .unlockedBy("has_crafting_casing", RegistrateRecipeProvider.has(NEBlocks.CRAFTING_CASING))
            .save(provider);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, NEBlocks.CRAFTING_PARALLEL_CORE_L6)
            .pattern("AAA")
            .pattern("ABA")
            .pattern("AAA")
            .define('A', NEBlocks.CRAFTING_PARALLEL_CORE_L4)
            .define('B', NEItems.SUPERCONDUCTING_PROCESSOR)
            .unlockedBy("has_crafting_parallel_core_l4", RegistrateRecipeProvider.has(NEBlocks.CRAFTING_PARALLEL_CORE_L4))
            .save(provider);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, NEBlocks.CRAFTING_PARALLEL_CORE_L9)
            .pattern("AAA")
            .pattern("ABA")
            .pattern("AAA")
            .define('A', NEBlocks.CRAFTING_PARALLEL_CORE_L6)
            .define('B', NEItems.SUPERCONDUCTING_PROCESSOR)
            .unlockedBy("has_crafting_parallel_core_l6", RegistrateRecipeProvider.has(NEBlocks.CRAFTING_PARALLEL_CORE_L6))
            .save(provider);
    }
}
