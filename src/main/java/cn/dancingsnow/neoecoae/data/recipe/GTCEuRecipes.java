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

public final class GTCEuRecipes {
    private static final String GTCEU = "gtceu";
    private static final String DATA_MODULE = gt("data_module");
    private static final String EUROPIUM = gt("europium");
    private static final String NEUTRONIUM = gt("neutronium");
    private static final String TRITANIUM = gt("tritanium");
    private static final String SOLDERING_ALLOY = gt("soldering_alloy");
    private static final String L9_CELL_COMPONENT_RESEARCH = ne("eco_cell_component_256m");

    private static final int HV = 512;
    private static final int IV = 8_192;
    private static final int LUV = 32_768;
    private static final int UV = 524_288;

    private static final Tier HV_TIER = new Tier(HV, 576, 400);
    private static final Tier IV_TIER = new Tier(IV, 1_152, 600);
    private static final Tier LUV_TIER = new Tier(LUV, 2_304, 900);
    private static final Tier UV_TIER = new Tier(UV, 9_216, 3_600);

    private GTCEuRecipes() {}

    public static void init(RegistrateRecipeProvider provider) {
        saveBaseMaterialRecipes(provider);
        saveInscriberGtMachineRecipes(provider);
        saveTieredCellComponentRecipes(provider);
        saveTieredComputationCellRecipes(provider);
        saveTieredMachineRecipes(provider);
        saveInfiniteStorageRecipe(provider);
        saveInfiniteStorageResearchRecipe(provider);
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

        mixer("energized_superconductive_ingot", HV, 480)
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

    private static void saveInscriberGtMachineRecipes(RegistrateRecipeProvider provider) {
        macerator("iron_dust", HV, 60)
                .itemInputTag(forge("ingots/iron"), 1)
                .itemOutput(ne("iron_dust"), 1)
                .save(provider);

        macerator("aluminum_dust", HV, 60)
                .itemInputTag(forge("ingots/aluminum"), 1)
                .itemOutput(ne("aluminum_dust"), 1)
                .save(provider);

        macerator("tungsten_dust", HV, 80)
                .itemInputTag(forge("ingots/tungsten"), 1)
                .itemOutput(ne("tungsten_dust"), 1)
                .save(provider);

        macerator("aluminum_alloy_dust", HV, 80)
                .itemInputTag(forge("ingots/aluminum_alloy"), 1)
                .itemOutput(ne("aluminum_alloy_dust"), 1)
                .save(provider);

        macerator("black_tungsten_alloy_dust", HV, 100)
                .itemInputTag(forge("ingots/black_tungsten_alloy"), 1)
                .itemOutput(ne("black_tungsten_alloy_dust"), 1)
                .save(provider);

        macerator("energized_crystal_dust", HV, 80)
                .itemInputTag(forge("gems/energized_crystal"), 1)
                .itemOutput(ne("energized_crystal_dust"), 1)
                .save(provider);

        macerator("energized_fluix_crystal_dust", HV, 100)
                .itemInputTag(forge("gems/energized_fluix_crystal"), 1)
                .itemOutput(ne("energized_fluix_crystal_dust"), 1)
                .save(provider);

        formingPress("superconducting_processor_press", HV, 200)
                .itemInputTag(forge("storage_blocks/iron"), 1)
                .notConsumableItem(ne("superconducting_processor_press"))
                .itemOutput(ne("superconducting_processor_press"), 1)
                .save(provider);

        formingPress("superconducting_processor_print", HV, 200)
                .itemInput(ne("energized_superconductive_ingot"), 1)
                .notConsumableItem(ne("superconducting_processor_press"))
                .itemOutput(ne("superconducting_processor_print"), 1)
                .save(provider);

        formingPress("superconducting_processor", HV, 200)
                .itemInput(ne("crystal_matrix"), 1)
                .itemInput(ne("superconducting_processor_print"), 1)
                .itemInput("ae2:printed_silicon", 1)
                .itemOutput(ne("superconducting_processor"), 1)
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
        assembler("crafting_worker", HV_TIER)
                .itemInput("ae2:256k_crafting_storage", 4)
                .itemInput("ae2:interface", 1)
                .itemInput(ne("crafting_casing"), 3)
                .itemInput(ne("crafting_vent"), 1)
                .solder(HV_TIER)
                .itemOutput(ne("crafting_worker"), 1)
                .save(provider);

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
        assemblyLine("eco_infinite_cell_component", UV_TIER)
                .itemInput(ne("eco_cell_component_256m"), 8)
                .itemInput(ne("eco_computation_cell_l9"), 1)
                .itemInput(ne("eco_item_storage_cell_256m"), 1)
                .itemInput(ne("storage_system_l9"), 1)
                .itemInput(ne("computation_system_l9"), 1)
                .itemInput(ne("crafting_system_l9"), 1)
                .itemInput(ne("crystal_matrix"), 32)
                .itemInput(ne("energized_superconductive_ingot"), 64)
                .itemInput(gt("uv_electric_motor"), 2)
                .itemInput(gt("uv_electric_piston"), 2)
                .itemInput(gt("uv_electric_pump"), 2)
                .itemInput(gt("uv_conveyor_module"), 2)
                .itemInput(gt("uv_robot_arm"), 2)
                .itemInput(gt("uv_emitter"), 2)
                .itemInput(gt("uv_sensor"), 2)
                .itemInput(gt("uv_field_generator"), 2)
                .fluidInput(SOLDERING_ALLOY, UV_TIER.solderAmount())
                .fluidInput(ne("cryotheum_solution"), 8_000)
                .fluidInput(EUROPIUM, 4_000)
                .fluidInput(TRITANIUM, 4_000)
                .fluidInput(NEUTRONIUM, 1_000)
                .research(L9_CELL_COMPONENT_RESEARCH, DATA_MODULE)
                .itemOutput(ne("eco_infinite_cell_component"), 1)
                .save(provider);
    }

    private static void saveInfiniteStorageResearchRecipe(RegistrateRecipeProvider provider) {
        machineRecipe("research_station", "eco_infinite_cell_component", LUV, 2_000)
                .itemInput(DATA_MODULE, 1)
                .itemInput(ne("eco_cell_component_256m"), 1)
                .cwu(64)
                .itemOutputDataModule(L9_CELL_COMPONENT_RESEARCH, "gtceu:assembly_line")
                .save(provider);
    }

    private static GTRecipe assembler(String name, Tier tier) {
        return machineRecipe("assembler", name, tier.eu(), tier.duration());
    }

    private static GTRecipe assemblyLine(String name, Tier tier) {
        return machineRecipe("assembly_line", name, tier.eu(), tier.duration());
    }

    private static GTRecipe formingPress(String name, int eu, int duration) {
        return machineRecipe("forming_press", name, eu, duration);
    }

    private static GTRecipe macerator(String name, int eu, int duration) {
        return machineRecipe("macerator", name, eu, duration);
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
        private final ResourceLocation id;
        private final String type;
        private final int eu;
        private final int duration;
        private int cwu;
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

        private GTRecipe notConsumableItem(String item) {
            itemInputs.add(content(sizedIngredient(itemIngredient(item), 1), 0));
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

        private GTRecipe itemOutputDataModule(String researchId, String researchType) {
            itemOutputs.add(content(
                    sizedIngredient(strictNbtIngredient(DATA_MODULE, researchNbt(researchId, researchType)), 1)));
            return this;
        }

        private GTRecipe cwu(int cwu) {
            this.cwu = cwu;
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
            writeGTRecipe(json);
            return json;
        }

        @Override
        public void serializeRecipeData(JsonObject json) {
            writeGTRecipe(json);
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
                if (cwu > 0) {
                    JsonArray cwuInputs = new JsonArray();
                    JsonObject cwuContent = new JsonObject();
                    cwuContent.addProperty("content", cwu);
                    cwuInputs.add(cwuContent);
                    tickInputs.add("cwu", cwuInputs);
                }
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
            return content(value, -1);
        }

        private static JsonObject content(JsonObject value, int chance) {
            JsonObject json = new JsonObject();
            json.add("content", value);
            if (chance >= 0) {
                json.addProperty("chance", chance);
                json.addProperty("maxChance", 10_000);
                json.addProperty("tierChanceBoost", 0);
            }
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

        private static JsonObject strictNbtIngredient(String item, String nbt) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "forge:nbt");
            json.addProperty("item", item);
            json.addProperty("nbt", nbt);
            return json;
        }

        private static String researchNbt(String researchId, String researchType) {
            return "{assembly_line_research:{research_id:\"%s\",research_type:\"%s\"}}"
                    .formatted(researchId, researchType);
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
