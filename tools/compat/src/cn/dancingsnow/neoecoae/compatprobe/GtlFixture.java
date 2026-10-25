package cn.dancingsnow.neoecoae.compatprobe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternContainer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Uses the installed pack's final GT recipes and its own recipe-to-pattern converter. */
final class GtlFixture {
    final List<IPatternDetails> patterns = new ArrayList<>();
    final Map<AEKey, Long> stock = new LinkedHashMap<>();
    final JsonArray recipes = new JsonArray();
    final AEKey target;
    final long amount;
    final long operations;
    Object machine;
    ICraftingProvider provider;
    IManagedGridNode node;

    GtlFixture(JsonObject spec, ServerLevel level) throws ReflectiveOperationException {
        var recipeClass = Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipe");
        var converter = Class.forName("org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper")
                .getMethod("createPattern", recipeClass, Level.class);
        long count = 0;
        AEKey output = null;
        long outputAmount = 0;
        for (var entry : spec.getAsJsonArray("recipes")) {
            var selector = entry.getAsJsonObject();
            String type = selector.get("type").getAsString();
            var id = new ResourceLocation(selector.get("id").getAsString());
            var matches = new ArrayList<Object>();
            var candidates = new ArrayList<String>();
            for (var recipe : level.getRecipeManager().getRecipes()) {
                if (!recipeClass.isInstance(recipe)) continue;
                var actualId = (ResourceLocation) recipeClass.getField("id").get(recipe);
                Object recipeType = recipeClass.getField("recipeType").get(recipe);
                var actualType = (ResourceLocation) recipeType.getClass().getField("registryName").get(recipeType);
                if (!actualType.getPath().equals(type)) continue;
                if (actualId.getNamespace().equals(id.getNamespace())) candidates.add(actualId.toString());
                if (actualId.getNamespace().equals(id.getNamespace()) && (actualId.getPath().equals(id.getPath())
                        || actualId.getPath().equals(type + "/" + id.getPath()))) matches.add(recipe);
            }
            require(matches.size() == 1, "Expected one loaded GT recipe for " + selector + "; matches="
                    + matches.size() + "; same-type candidates=" + candidates);
            Object recipe = matches.get(0);
            Object converted = converter.invoke(null, recipe, level);
            require(converted != null, "GTL rejected recipe-to-pattern conversion: " + selector);
            var record = converted.getClass();
            IPatternDetails pattern = (IPatternDetails) record.getMethod("pattern").invoke(converted);
            require(!(Boolean) record.getMethod("chancedInput").invoke(converted), "Probabilistic input cannot be a deterministic fixture");
            patterns.add(pattern);
            long runs = selector.get("runs").getAsLong();
            count = Math.addExact(count, runs);
            var input = new JsonObject();
            for (var material : pattern.getInputs()) {
                var stack = material.getPossibleInputs()[0];
                long quantity = Math.multiplyExact(stack.amount(), material.getMultiplier());
                input.addProperty(label(stack.what()), quantity);
                stock.merge(stack.what(), Math.multiplyExact(quantity, runs), Math::addExact);
            }
            var outputs = new JsonObject();
            for (var stack : pattern.getOutputs()) {
                outputs.addProperty(label(stack.what()), stack.amount());
                stock.merge(stack.what(), -Math.multiplyExact(stack.amount(), runs), Math::addExact);
                if (label(stack.what()).equals(spec.get("target").getAsString())
                        || stack.what().getId().toString().equals(spec.get("target").getAsString())) {
                    output = stack.what();
                    outputAmount = Math.addExact(outputAmount, Math.multiplyExact(stack.amount(), runs));
                }
            }
            var evidence = new JsonObject();
            evidence.addProperty("id", record.getMethod("id").invoke(converted).toString());
            evidence.addProperty("recipeClass", recipe.getClass().getName());
            evidence.addProperty("circuit", (Integer) record.getMethod("circuit").invoke(converted));
            evidence.addProperty("nonConsumedInputs", record.getMethod("nonConsumedInputs").invoke(converted).toString());
            evidence.addProperty("conditions", recipeClass.getField("conditions").get(recipe).toString());
            evidence.addProperty("duration", recipeClass.getField("duration").getInt(recipe));
            evidence.addProperty("runs", runs);
            evidence.add("inputs", input);
            evidence.add("outputs", outputs);
            recipes.add(evidence);
            if (spec.has("expectInputs")) for (var expected : spec.getAsJsonObject("expectInputs").entrySet()) {
                require(input.has(expected.getKey()) && input.get(expected.getKey()).getAsLong() == expected.getValue().getAsLong(),
                        "Pack recipe input differs from its documented GTL contract: " + expected + "; actual=" + input);
            }
            if (spec.has("expectOutputAmount")) require(pattern.getOutputs()[0].amount() == spec.get("expectOutputAmount").getAsLong(),
                    "Pack recipe output amount changed");
            if (spec.has("expectCircuit")) require(evidence.get("circuit").getAsInt() == spec.get("expectCircuit").getAsInt(),
                    "Pack recipe circuit configuration changed");
            if (spec.has("expectReusableInputs")) require(((List<?>) record.getMethod("nonConsumedInputs").invoke(converted)).size()
                    == spec.get("expectReusableInputs").getAsInt(), "Pack recipe reusable tool requirements changed");
        }
        require(output != null, "Requested target is not an output of the loaded GT recipes");
        target = output;
        amount = spec.has("amount") ? spec.get("amount").getAsLong() : outputAmount;
        operations = count;
        stock.entrySet().removeIf(entry -> entry.getValue() <= 0);
        // A deliberate negative fixture removes a real recipe input, without changing the recipe.
        if (spec.has("missing")) {
            String missing = spec.get("missing").getAsString();
            require(stock.keySet().removeIf(key -> label(key).equals(missing)), "Missing-input selector did not match " + missing);
        }
    }

    void place(ServerLevel level, BlockPos position) throws ReflectiveOperationException {
        var blockId = new ResourceLocation("gtceu:me_extend_pattern_buffer");
        require(BuiltInRegistries.BLOCK.containsKey(blockId), "GTL pattern buffer block is absent");
        var state = BuiltInRegistries.BLOCK.get(blockId).defaultBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) state = state.setValue(BlockStateProperties.FACING, Direction.WEST);
        level.setBlockAndUpdate(position, state);
        machine = Class.forName("com.gregtechceu.gtceu.api.machine.MetaMachine")
                .getMethod("getMachine", net.minecraft.world.level.BlockGetter.class, BlockPos.class).invoke(null, level, position);
        require(machine != null && machine.getClass().getName().startsWith("org.gtlcore."), "Expected a real GTL pattern buffer");
        machine.getClass().getMethod("setFrontFacing", Direction.class).invoke(machine, Direction.WEST);
        provider = (ICraftingProvider) machine;
        node = (IManagedGridNode) machine.getClass().getMethod("getMainNode").invoke(machine);
        // Use the public copy/paste format to enable byproducts, as the in-game buffer UI does.
        var settings = new net.minecraft.nbt.CompoundTag();
        settings.putBoolean("keepByProduct", true);
        settings.put("patterns", new net.minecraft.nbt.ListTag());
        machine.getClass().getMethod("pasteFromTag", net.minecraft.nbt.CompoundTag.class).invoke(machine, settings);
        var inventory = ((PatternContainer) machine).getTerminalPatternInventory();
        for (int slot = 0; slot < patterns.size(); slot++) {
            ItemStack encoded = patterns.get(slot).getDefinition().toStack();
            int circuit = recipes.get(slot).getAsJsonObject().get("circuit").getAsInt();
            if (circuit > 0) {
                Object helper = machine.getClass().getMethod("getRealPatternHelper").invoke(machine);
                encoded = (ItemStack) helper.getClass().getMethod("createPatternWithCircuit", ItemStack.class, int.class,
                        boolean.class, Level.class).invoke(helper, encoded, circuit, true, level);
            }
            inventory.setItemDirect(slot, encoded);
            if (circuit > 0) {
                var installed = (ItemStack) machine.getClass().getMethod("getCircuitForRecipe", int.class).invoke(machine, slot);
                require(installed.hasTag() && installed.getTag().getInt("Configuration") == circuit,
                        "GTL buffer lost the recipe's circuit configuration " + circuit);
            }
        }
    }

    void inspect(JsonObject evidence) throws ReflectiveOperationException {
        evidence.addProperty("providerClass", machine.getClass().getName());
        evidence.add("loadedRecipes", recipes);
        var available = provider.getAvailablePatterns();
        require(available.size() == patterns.size(), "GTL buffer changed the number of published recipes");
        var capacity = machine.getClass().getMethod("gtlcore$graphCapacity", IPatternDetails.class, Map.class, long.class);
        var details = new JsonArray();
        for (var pattern : available) {
            var perRun = new LinkedHashMap<AEKey, Long>();
            for (var input : pattern.getInputs()) perRun.merge(input.getPossibleInputs()[0].what(),
                    Math.multiplyExact(input.getPossibleInputs()[0].amount(), input.getMultiplier()), Math::addExact);
            long admitted = ((Number) capacity.invoke(machine, pattern, perRun, 64L)).longValue();
            require(admitted == 64, "Empty GTL buffer did not admit 64 operations: " + admitted);
            var row = new JsonObject();
            row.addProperty("pattern", pattern.getDefinition().toString());
            row.addProperty("capacity", admitted);
            row.addProperty("diagnostic", machine.getClass().getMethod("gtlcore$graphDiagnostic", IPatternDetails.class).invoke(machine, pattern).toString());
            details.add(row);
        }
        evidence.add("bufferPatterns", details);
    }

    void admit(long runs, JsonObject evidence) throws ReflectiveOperationException {
        require(patterns.size() == 1, "Native admission requires a single-recipe environment");
        var pattern = provider.getAvailablePatterns().get(0);
        var inputs = new KeyCounter[pattern.getInputs().length];
        var expected = new LinkedHashMap<AEKey, Long>();
        var storage = node.getGrid().getStorageService().getInventory();
        for (int slot = 0; slot < inputs.length; slot++) {
            var input = pattern.getInputs()[slot];
            var sample = input.getPossibleInputs()[0];
            long amount = Math.multiplyExact(Math.multiplyExact(sample.amount(), input.getMultiplier()), runs);
            long extracted = storage.extract(sample.what(), amount, appeng.api.config.Actionable.MODULATE,
                    appeng.api.networking.security.IActionSource.empty());
            require(extracted == amount, "Native admission fixture did not own the requested input");
            inputs[slot] = new KeyCounter();
            inputs[slot].add(sample.what(), amount);
            expected.merge(sample.what(), amount, Math::addExact);
        }
        require(provider.pushPattern(pattern, inputs), "Real GTL buffer rejected the batch");
        Object[] slots = (Object[]) machine.getClass().getMethod("getInternalInventory").invoke(machine);
        var held = new LinkedHashMap<AEKey, Long>();
        for (Object slot : slots) for (String methodName : List.of("getItemInventory", "getFluidInventory")) {
            var method = slot.getClass().getMethod(methodName);
            require(method.trySetAccessible(), "Cannot inspect GTL buffer custody");
            Map<?, ?> inventory = (Map<?, ?>) method.invoke(slot);
            inventory.forEach((key, amount) -> held.merge((AEKey) key, ((Number) amount).longValue(), Math::addExact));
        }
        require(held.equals(expected), "GTL batch changed item/fluid custody: expected=" + expected + "; held=" + held);
        for (var key : expected.keySet()) require(storage.getAvailableStacks().get(key) == 0,
                "GTL admission left a duplicate material in ME storage: " + key);
        evidence.addProperty("admittedRuns", runs);
        evidence.addProperty("bufferCustody", held.toString());
    }

    static String label(AEKey key) {
        return (key instanceof AEFluidKey ? "fluid:" : "") + key.getId();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
