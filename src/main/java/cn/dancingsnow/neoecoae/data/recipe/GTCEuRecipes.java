package cn.dancingsnow.neoecoae.data.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;

public final class GTCEuRecipes {
    private static final String GTCEU = "gtceu";
    private static final String FORGE_CONDITIONAL_RECIPE = "forge:conditional";
    private static final String DATA_MODULE = gt("data_module");
    private static final String SOLDERING_ALLOY = gt("soldering_alloy");
    private static final String L9_CELL_COMPONENT_RESEARCH = ne("eco_cell_component_256m");

    private static final int HV = 512;
    private static final int IV = 8_192;
    private static final int LUV = 32_768;
    private static final int UHV = 2_097_152;

    private static final Tier HV_TIER = new Tier(HV, 576, 400);
    private static final Tier IV_TIER = new Tier(IV, 1_152, 600);
    private static final Tier LUV_TIER = new Tier(LUV, 2_304, 900);
    private static final Tier UHV_TIER = new Tier(UHV, 18_432, 7_200);

    private GTCEuRecipes() {}

    public static void init(RegistrateRecipeProvider provider) {
        saveBaseMaterialRecipes(provider);
        saveTieredCellComponentRecipes(provider);
        saveTieredComputationCellRecipes(provider);
        saveTieredMachineRecipes(provider);
        saveInfiniteStorageRecipe(provider);
    }

    private static void saveBaseMaterialRecipes(RegistrateRecipeProvider provider) {
        chemicalReactor("energized_crystal", HV, 200)
                .itemInput("ae2:charged_certus_quartz_crystal", 4)
                .itemInputTag(forge("dusts/energized_crystal"), 4)
                .fluidInput("minecraft:water", 250)
                .itemOutput(ne("energized_crystal"), 8)
                .save(provider);

        chemicalReactor("energized_fluix_crystal", HV, 200)
                .itemInputTag(forge("dusts/energized_crystal"), 8)
                .itemInputTag(forge("gems/fluix"), 8)
                .fluidInput("minecraft:water", 250)
                .itemOutput(ne("energized_fluix_crystal"), 8)
                .save(provider);

        mixer("aluminum_alloy_dust", HV, 160)
                .itemInputTag(forge("dusts/iron"), 1)
                .itemInputTag(forge("dusts/aluminium"), 1)
                .itemInputTag(forge("dusts/certus_quartz"), 2)
                .itemOutput(ne("aluminum_alloy_dust"), 1)
                .save(provider);

        mixer("black_tungsten_alloy_dust", HV, 240)
                .itemInputTag(forge("dusts/tungsten"), 1)
                .itemInputTag(forge("dusts/aluminum_alloy"), 1)
                .itemInputTag(forge("dusts/fluix"), 2)
                .itemOutput(ne("black_tungsten_alloy_dust"), 1)
                .save(provider);

        mixer("crystal_ingot", HV, 400)
                .itemInputTag(forge("dusts/certus_quartz"), 4)
                .itemInputTag(forge("dusts/fluix"), 4)
                .itemInputTag(forge("dusts/energized_crystal"), 4)
                .itemInputTag(ne("crystal_ingot_base"), 4)
                .fluidInput("minecraft:lava", 2_000)
                .itemOutput(ne("crystal_ingot"), 4)
                .save(provider);

        mixer("energized_superconductive_ingot", IV, 480)
                .itemInputTag(forge("dusts/energized_fluix_crystal"), 4)
                .itemInputTag(forge("dusts/aluminium"), 4)
                .itemInputTag(forge("silicon"), 4)
                .itemInputTag(ne("superconductive_ingot_base"), 4)
                .fluidInput("minecraft:lava", 2_000)
                .itemOutput(ne("energized_superconductive_ingot"), 4)
                .save(provider);

        mixer("cryotheum", HV, 200)
                .itemInput("minecraft:ice", 1)
                .itemInputTag(forge("dusts/certus_quartz"), 1)
                .itemInput("ae2:sky_dust", 1)
                .itemInput("minecraft:snowball", 1)
                .itemInputTag(forge("dusts/energized_crystal"), 4)
                .itemOutput(ne("cryotheum"), 1)
                .save(provider);

        mixer("cryotheum_solution", HV, 200)
                .itemInput(ne("cryotheum_crystal"), 4)
                .itemInput(ne("energized_crystal"), 2)
                .itemInputTag(forge("dusts/redstone"), 2)
                .fluidInput("minecraft:water", 1_000)
                .fluidOutput(ne("cryotheum_solution"), 1_000)
                .save(provider);
    }

    private static void saveTieredCellComponentRecipes(RegistrateRecipeProvider provider) {
        assembler("eco_cell_component_16m", HV_TIER)
                .itemInput("ae2:cell_component_256k", 12)
                .itemInput(ne("energized_superconductive_ingot"), 32)
                .itemInput(ne("superconducting_processor"), 4)
                .itemInput(ne("crystal_ingot"), 1)
                .solder(HV_TIER)
                .itemOutput(ne("eco_cell_component_16m"), 1)
                .save(provider);

        assembler("eco_cell_component_64m", IV_TIER)
                .itemInput(ne("eco_cell_component_16m"), 3)
                .itemInput(ne("energized_superconductive_ingot"), 48)
                .itemInput(ne("superconducting_processor"), 16)
                .itemInput(ne("crystal_ingot"), 1)
                .solder(IV_TIER)
                .itemOutput(ne("eco_cell_component_64m"), 1)
                .save(provider);

        assembler("eco_cell_component_256m", LUV_TIER)
                .itemInput(ne("eco_cell_component_64m"), 3)
                .itemInput(ne("energized_superconductive_ingot"), 64)
                .itemInput(ne("superconducting_processor"), 64)
                .itemInput(ne("crystal_ingot"), 1)
                .solder(LUV_TIER)
                .itemOutput(ne("eco_cell_component_256m"), 1)
                .save(provider);
    }

    private static void saveTieredComputationCellRecipes(RegistrateRecipeProvider provider) {
        assembler("eco_computation_cell_l4", HV_TIER)
                .itemInput(ne("eco_cell_component_16m"), 4)
                .itemInput(ne("energized_superconductive_ingot"), 64)
                .itemInput(ne("superconducting_processor"), 4)
                .itemInput(ne("crystal_matrix"), 1)
                .solder(HV_TIER)
                .itemOutput(ne("eco_computation_cell_l4"), 1)
                .save(provider);

        assembler("eco_computation_cell_l6", IV_TIER)
                .itemInput(ne("eco_cell_component_64m"), 4)
                .itemInput(ne("energized_superconductive_ingot"), 64)
                .itemInput(ne("superconducting_processor"), 16)
                .itemInput(ne("crystal_matrix"), 1)
                .solder(IV_TIER)
                .itemOutput(ne("eco_computation_cell_l6"), 1)
                .save(provider);

        assembler("eco_computation_cell_l9", LUV_TIER)
                .itemInput(ne("eco_cell_component_256m"), 4)
                .itemInput(ne("energized_superconductive_ingot"), 64)
                .itemInput(ne("superconducting_processor"), 64)
                .itemInput(ne("crystal_matrix"), 1)
                .solder(LUV_TIER)
                .itemOutput(ne("eco_computation_cell_l9"), 1)
                .save(provider);
    }

    private static void saveTieredMachineRecipes(RegistrateRecipeProvider provider) {
        saveStorageSystemRecipes(provider);
        saveComputationSystemRecipes(provider);
        saveCraftingSystemRecipes(provider);
    }

    private static void saveStorageSystemRecipes(RegistrateRecipeProvider provider) {
        assembler("storage_system_l4", HV_TIER)
                .itemInput(ne("storage_casing"), 4)
                .itemInput("ae2:drive", 4)
                .itemInput(ne("energized_superconductive_ingot"), 16)
                .itemInput(ne("superconducting_processor"), 16)
                .solder(HV_TIER)
                .itemOutput(ne("storage_system_l4"), 1)
                .save(provider);

        assembler("storage_system_l6", IV_TIER)
                .itemInput(ne("storage_system_l4"), 1)
                .itemInput("ae2:drive", 8)
                .itemInput(ne("energized_superconductive_ingot"), 32)
                .itemInput(ne("superconducting_processor"), 32)
                .solder(IV_TIER)
                .itemOutput(ne("storage_system_l6"), 1)
                .save(provider);

        assembler("storage_system_l9", LUV_TIER)
                .itemInput(ne("storage_system_l6"), 1)
                .itemInput("ae2:drive", 16)
                .itemInput(ne("energized_superconductive_ingot"), 64)
                .itemInput(ne("superconducting_processor"), 64)
                .solder(LUV_TIER)
                .itemOutput(ne("storage_system_l9"), 1)
                .save(provider);
    }

    private static void saveComputationSystemRecipes(RegistrateRecipeProvider provider) {
        assembler("computation_system_l4", HV_TIER)
                .itemInput(ne("computation_casing"), 4)
                .itemInput(ne("computation_parallel_core_l4"), 2)
                .itemInput(ne("energized_superconductive_ingot"), 16)
                .itemInput(ne("superconducting_processor"), 16)
                .solder(HV_TIER)
                .itemOutput(ne("computation_system_l4"), 1)
                .save(provider);

        assembler("computation_system_l6", IV_TIER)
                .itemInput(ne("computation_system_l4"), 1)
                .itemInput(ne("computation_parallel_core_l6"), 2)
                .itemInput(ne("energized_superconductive_ingot"), 32)
                .itemInput(ne("superconducting_processor"), 32)
                .solder(IV_TIER)
                .itemOutput(ne("computation_system_l6"), 1)
                .save(provider);

        assembler("computation_system_l9", LUV_TIER)
                .itemInput(ne("computation_system_l6"), 1)
                .itemInput(ne("computation_parallel_core_l9"), 2)
                .itemInput(ne("energized_superconductive_ingot"), 64)
                .itemInput(ne("superconducting_processor"), 64)
                .solder(LUV_TIER)
                .itemOutput(ne("computation_system_l9"), 1)
                .save(provider);
    }

    private static void saveCraftingSystemRecipes(RegistrateRecipeProvider provider) {
        assembler("crafting_system_l4", HV_TIER)
                .itemInput(ne("crafting_casing"), 4)
                .itemInput(ne("crafting_parallel_core_l4"), 2)
                .itemInput(ne("energized_superconductive_ingot"), 16)
                .itemInput(ne("superconducting_processor"), 16)
                .solder(HV_TIER)
                .itemOutput(ne("crafting_system_l4"), 1)
                .save(provider);

        assembler("crafting_system_l6", IV_TIER)
                .itemInput(ne("crafting_system_l4"), 1)
                .itemInput(ne("crafting_parallel_core_l6"), 2)
                .itemInput(ne("energized_superconductive_ingot"), 32)
                .itemInput(ne("superconducting_processor"), 32)
                .solder(IV_TIER)
                .itemOutput(ne("crafting_system_l6"), 1)
                .save(provider);

        assembler("crafting_system_l9", LUV_TIER)
                .itemInput(ne("crafting_system_l6"), 1)
                .itemInput(ne("crafting_parallel_core_l9"), 2)
                .itemInput(ne("energized_superconductive_ingot"), 64)
                .itemInput(ne("superconducting_processor"), 64)
                .solder(LUV_TIER)
                .itemOutput(ne("crafting_system_l9"), 1)
                .save(provider);
    }

    private static void saveInfiniteStorageRecipe(RegistrateRecipeProvider provider) {
        assemblyLine("eco_infinite_cell_component", UHV_TIER)
                .itemInput(ne("eco_cell_component_256m"), 64)
                .itemInput(ne("eco_computation_cell_l9"), 16)
                .itemInput(ne("eco_item_storage_cell_256m"), 16)
                .itemInput(ne("storage_system_l9"), 4)
                .itemInput(ne("computation_system_l9"), 4)
                .itemInput(ne("crafting_system_l9"), 4)
                .itemInput(ne("crystal_matrix"), 64)
                .itemInput(ne("energized_superconductive_ingot"), 64)
                .itemInput("ae2:singularity", 64)
                .itemInput(gt("uhv_electric_motor"), 4)
                .itemInput(gt("uhv_electric_piston"), 4)
                .itemInput(gt("uhv_electric_pump"), 4)
                .itemInput(gt("uhv_conveyor_module"), 4)
                .itemInput(gt("uhv_robot_arm"), 4)
                .itemInput(gt("uhv_emitter"), 4)
                .itemInput(gt("uhv_sensor"), 4)
                .itemInput(gt("uhv_field_generator"), 4)
                .fluidInput(SOLDERING_ALLOY, UHV_TIER.solderAmount())
                .fluidInput(ne("cryotheum_solution"), 16_000)
                .research(L9_CELL_COMPONENT_RESEARCH, DATA_MODULE)
                .itemOutput(ne("eco_infinite_cell_component"), 1)
                .save(provider);
    }

    private static GTRecipe assembler(String name, Tier tier) {
        return machineRecipe("assembler", name, tier.eu(), tier.duration());
    }

    private static GTRecipe assemblyLine(String name, Tier tier) {
        return machineRecipe("assembly_line", name, tier.eu(), tier.duration());
    }

    private static GTRecipe chemicalReactor(String name, int eu, int duration) {
        return machineRecipe("chemical_reactor", name, eu, duration);
    }

    private static GTRecipe mixer(String name, int eu, int duration) {
        return machineRecipe("mixer", name, eu, duration);
    }

    private static GTRecipe machineRecipe(String machine, String name, int eu, int duration) {
        return new GTRecipe(NeoECOAE.id(machine + "/" + name), gt(machine), eu, duration);
    }

    private static String ne(String path) {
        return NeoECOAE.MOD_ID + ":" + path;
    }

    private static String gt(String path) {
        return GTCEU + ":" + path;
    }

    private static String forge(String path) {
        return "forge:" + path;
    }

    private record Tier(int eu, int solderAmount, int duration) {}

    private static final class GTRecipe implements FinishedRecipe {
        private static final ModLoadedCondition GTCEU_LOADED = new ModLoadedCondition(GTCEU);

        private final ResourceLocation id;
        private final String type;
        private final int eu;
        private final int duration;
        private final List<JsonObject> itemInputs = new ArrayList<>();
        private final List<JsonObject> fluidInputs = new ArrayList<>();
        private final List<JsonObject> itemOutputs = new ArrayList<>();
        private final List<JsonObject> fluidOutputs = new ArrayList<>();
        private final List<JsonObject> recipeConditions = new ArrayList<>();

        private GTRecipe(ResourceLocation id, String type, int eu, int duration) {
            this.id = id;
            this.type = type;
            this.eu = eu;
            this.duration = duration;
        }

        private GTRecipe itemInput(String item, int count) {
            itemInputs.add(content(sizedIngredient(itemIngredient(item), count)));
            return this;
        }

        private GTRecipe itemInputTag(String tag, int count) {
            itemInputs.add(content(sizedIngredient(tagIngredient(tag), count)));
            return this;
        }

        private GTRecipe fluidInput(String fluid, int amount) {
            fluidInputs.add(content(fluidIngredient(fluidValue("fluid", fluid), amount)));
            return this;
        }

        private GTRecipe itemOutput(String item, int count) {
            itemOutputs.add(content(sizedIngredient(itemIngredient(item), count)));
            return this;
        }

        private GTRecipe fluidOutput(String fluid, int amount) {
            fluidOutputs.add(content(fluidIngredient(fluidValue("fluid", fluid), amount)));
            return this;
        }

        private GTRecipe solder(Tier tier) {
            return fluidInput(SOLDERING_ALLOY, tier.solderAmount());
        }

        private GTRecipe research(String researchId, String dataItem) {
            JsonObject entry = new JsonObject();
            entry.addProperty("researchId", researchId);
            entry.add("dataItem", itemStack(dataItem, 1));

            JsonArray research = new JsonArray();
            research.add(entry);

            JsonObject condition = new JsonObject();
            condition.addProperty("type", gt("research"));
            condition.add("research", research);
            recipeConditions.add(condition);
            return this;
        }

        private void save(RegistrateRecipeProvider provider) {
            provider.accept(this);
        }

        @Override
        public JsonObject serializeRecipe() {
            JsonObject json = new JsonObject();
            writeConditionalRecipe(json);
            return json;
        }

        @Override
        public void serializeRecipeData(JsonObject json) {
            writeConditionalRecipeData(json);
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return RecipeSerializer.SHAPELESS_RECIPE;
        }

        @Override
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Override
        public ResourceLocation getAdvancementId() {
            return null;
        }

        private void writeConditionalRecipe(JsonObject json) {
            json.addProperty("type", FORGE_CONDITIONAL_RECIPE);
            writeConditionalRecipeData(json);
        }

        private void writeConditionalRecipeData(JsonObject json) {
            JsonObject holder = new JsonObject();
            JsonArray conditions = new JsonArray();
            conditions.add(CraftingHelper.serialize(GTCEU_LOADED));
            holder.add("conditions", conditions);

            JsonObject recipe = new JsonObject();
            writeGTRecipe(recipe);
            holder.add("recipe", recipe);

            JsonArray recipes = new JsonArray();
            recipes.add(holder);
            json.add("recipes", recipes);
        }

        private void writeGTRecipe(JsonObject json) {
            json.addProperty("type", type);
            json.addProperty("duration", duration);
            addConditions(json);

            JsonObject inputs = new JsonObject();
            addCapability(inputs, "item", itemInputs);
            addCapability(inputs, "fluid", fluidInputs);
            if (inputs.size() > 0) {
                json.add("inputs", inputs);
            }

            JsonObject outputs = new JsonObject();
            addCapability(outputs, "item", itemOutputs);
            addCapability(outputs, "fluid", fluidOutputs);
            if (outputs.size() > 0) {
                json.add("outputs", outputs);
            }

            if (eu > 0) {
                JsonObject tickInputs = new JsonObject();
                JsonArray euInputs = new JsonArray();
                JsonObject euContent = new JsonObject();
                euContent.addProperty("content", eu);
                euInputs.add(euContent);
                tickInputs.add("eu", euInputs);
                json.add("tickInputs", tickInputs);
            }
        }

        private void addConditions(JsonObject json) {
            if (recipeConditions.isEmpty()) {
                return;
            }

            JsonArray conditions = new JsonArray();
            recipeConditions.forEach(conditions::add);
            json.add("recipeConditions", conditions);
        }

        private static void addCapability(JsonObject target, String name, List<JsonObject> contents) {
            if (contents.isEmpty()) {
                return;
            }

            JsonArray array = new JsonArray();
            contents.forEach(array::add);
            target.add(name, array);
        }

        private static JsonObject content(JsonObject value) {
            JsonObject json = new JsonObject();
            json.add("content", value);
            return json;
        }

        private static JsonObject sizedIngredient(JsonObject ingredient, int count) {
            JsonObject json = new JsonObject();
            json.addProperty("type", gt("sized"));
            json.addProperty("count", count);
            json.add("ingredient", ingredient);
            return json;
        }

        private static JsonObject itemIngredient(String item) {
            JsonObject json = new JsonObject();
            json.addProperty("item", item);
            return json;
        }

        private static JsonObject itemStack(String item, int count) {
            JsonObject json = new JsonObject();
            json.addProperty("id", item);
            json.addProperty("Count", count);
            return json;
        }

        private static JsonObject tagIngredient(String tag) {
            JsonObject json = new JsonObject();
            json.addProperty("tag", tag);
            return json;
        }

        private static JsonObject fluidIngredient(JsonObject value, int amount) {
            JsonObject json = new JsonObject();
            json.addProperty("amount", amount);
            json.add("value", value);
            return json;
        }

        private static JsonObject fluidValue(String key, String value) {
            JsonObject json = new JsonObject();
            json.addProperty(key, value);
            return json;
        }
    }
}
