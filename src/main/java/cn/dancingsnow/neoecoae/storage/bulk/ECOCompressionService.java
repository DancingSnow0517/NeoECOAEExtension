package cn.dancingsnow.neoecoae.storage.bulk;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.network.ECOCompressionSyncS2CPacket;
import appeng.menu.AutoCraftingMenu;
import cn.dancingsnow.neoecoae.network.ECONetwork;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.Level;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ECO-owned recognition: only verified reversible recipes, without arbitrary branch selection. */
public final class ECOCompressionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOCompressionService.class);
    private static volatile Snapshot snapshot = new Snapshot(0, List.of(), Map.of());
    private static volatile List<Edge> overrides = List.of();
    private static volatile Set<AEItemKey> disabled = Set.of();
    private ECOCompressionService() {}
    public record Edge(AEItemKey lower, AEItemKey higher, int ratio) {
        public Edge {
            if (lower == null || higher == null || lower.equals(higher) || ratio < 2)
                throw new IllegalArgumentException("Invalid compression edge");
        }
    }
    private record RecipeCandidate(AEItemKey input, int inputCount, AEItemKey output, int outputCount) {}
    private record Snapshot(long revision, List<BulkUnits> chains, Map<AEItemKey, BulkUnits> index) {}
    public static long revision() { return snapshot.revision(); }
    public static BulkUnits units(AEItemKey key) {
        BulkUnits found = snapshot.index().get(key);
        return found == null ? BulkUnits.single(key) : found;
    }
    public static boolean sameChain(AEItemKey first, AEItemKey second) { return first.equals(second) || units(first).contains(second); }
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> reload(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> install(List.of()));
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new Rules()));
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) reload(event.getPlayerList().getServer());
            List<BulkUnits> chains = snapshot.chains();
            int step = ECOCompressionSyncS2CPacket.CHAINS_PER_PACKET;
            for (int offset = 0; offset < Math.max(1, chains.size()); offset += step) {
                var packet = new ECOCompressionSyncS2CPacket(chains.size(), offset, chains.subList(offset, Math.min(offset + step, chains.size())));
                if (event.getPlayer() == null) ECONetwork.sendToAllPlayers(packet);
                else ECONetwork.sendToPlayer(event.getPlayer(), packet);
            }
        });
    }
    public static void install(List<BulkUnits> chains) {
        Map<AEItemKey, BulkUnits> index = new HashMap<>();
        for (BulkUnits chain : chains) for (AEItemKey key : chain.items()) {
            if (index.putIfAbsent(key, chain) != null) throw new IllegalArgumentException("Overlapping compression chains");
        }
        snapshot = new Snapshot(snapshot.revision() + 1, List.copyOf(chains), Map.copyOf(index));
    }
    private static void reload(MinecraftServer server) {
        var recipes = server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
            .sorted(Comparator.comparing(value -> value.getId().toString())).toList();
        List<Edge> edges = new ArrayList<>(recognizeRecipes(recipes, server.registryAccess(), server.overworld()));
        edges.removeIf(edge -> disabled.contains(edge.lower()) || disabled.contains(edge.higher())
            || overrides.stream().anyMatch(rule -> rule.lower().equals(edge.lower()) || rule.higher().equals(edge.higher())));
        for (Edge edge : overrides) if (!disabled.contains(edge.lower()) && !disabled.contains(edge.higher())) edges.add(edge);
        install(buildChains(edges));
        LOGGER.info("ECO compression: {} reversible chains", snapshot.chains().size());
    }
    static List<Edge> recognizeRecipes(List<CraftingRecipe> recipeList, RegistryAccess registries, Level level) {
        List<RecipeCandidate> candidates = new ArrayList<>();
        for (CraftingRecipe recipe : recipeList) {
            if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) || recipe.isSpecial()) continue;
            try { candidates.addAll(recognize(recipe, registries, level)); }
            catch (RuntimeException failure) { LOGGER.warn("Skipping ECO compression recipe {}: {}", recipe, failure.toString()); }
        }
        List<Edge> edges = new ArrayList<>();
        Set<RecipeCandidate> recipes = new HashSet<>(candidates);
        for (RecipeCandidate candidate : candidates) {
            if (candidate.outputCount() != 1 || candidate.inputCount() < 2) continue;
            if (recipes.contains(new RecipeCandidate(candidate.output(), 1, candidate.input(), candidate.inputCount())))
                edges.add(new Edge(candidate.input(), candidate.output(), candidate.inputCount()));
        }
        return List.copyOf(edges);
    }
    private static List<RecipeCandidate> recognize(CraftingRecipe recipe, RegistryAccess registries, Level level) {
        var ingredients = recipe.getIngredients();
        var nonempty = ingredients.stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        if (nonempty.isEmpty() || nonempty.stream().anyMatch(ingredient -> !ingredient.isSimple())) return List.of();
        var output = recipe.getResultItem(registries);
        if (output.isEmpty()) return List.of();
        Set<AEItemKey> possible = new HashSet<>();
        for (ItemStack stack : nonempty.get(0).getItems()) if (!stack.isEmpty()) possible.add(AEItemKey.of(stack));
        List<RecipeCandidate> result = new ArrayList<>();
        for (AEItemKey key : possible) {
            if (nonempty.stream().anyMatch(ingredient -> !ingredient.test(key.toStack()))) continue;
            var stacks = new ArrayList<ItemStack>();
            for (var ingredient : ingredients) stacks.add(ingredient.isEmpty() ? ItemStack.EMPTY : key.toStack());
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : ingredients.size();
            int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 1;
            var input = new TransientCraftingContainer(new AutoCraftingMenu(), width, height);
            for (int slot = 0; slot < stacks.size(); slot++) input.setItem(slot, stacks.get(slot));
            if (!recipe.matches(input, level)) continue;
            var actual = recipe.assemble(input, registries);
            if (!ItemStack.isSameItemSameTags(actual, output) || actual.getCount() != output.getCount()
                    || recipe.getRemainingItems(input).stream().anyMatch(stack -> !stack.isEmpty())) continue;
            result.add(new RecipeCandidate(key, nonempty.size(), AEItemKey.of(output), output.getCount()));
        }
        return result;
    }
    /** Reject the entire connected component for branches, cycles or conflicting ratios. */
    public static List<BulkUnits> buildChains(List<Edge> edges) {
        Map<AEItemKey, Set<Edge>> touching = new LinkedHashMap<>();
        for (Edge edge : edges) {
            touching.computeIfAbsent(edge.lower(), ignored -> new HashSet<>()).add(edge);
            touching.computeIfAbsent(edge.higher(), ignored -> new HashSet<>()).add(edge);
        }
        Set<AEItemKey> seen = new HashSet<>();
        List<BulkUnits> chains = new ArrayList<>();
        for (AEItemKey start : touching.keySet()) {
            if (seen.contains(start)) continue;
            List<AEItemKey> todo = new ArrayList<>(List.of(start));
            Set<AEItemKey> component = new HashSet<>();
            Set<Edge> componentEdges = new HashSet<>();
            for (int i = 0; i < todo.size(); i++) {
                AEItemKey key = todo.get(i);
                if (!component.add(key)) continue;
                for (Edge edge : touching.get(key)) { componentEdges.add(edge); todo.add(edge.lower()); todo.add(edge.higher()); }
            }
            seen.addAll(component);
            Map<AEItemKey, Edge> next = new HashMap<>(), previous = new HashMap<>();
            boolean valid = true;
            for (Edge edge : componentEdges)
                if (next.putIfAbsent(edge.lower(), edge) != null || previous.putIfAbsent(edge.higher(), edge) != null) valid = false;
            List<AEItemKey> roots = component.stream().filter(key -> !previous.containsKey(key)).toList();
            if (!valid || roots.size() != 1) continue;
            List<AEItemKey> items = new ArrayList<>(); List<BigInteger> factors = new ArrayList<>();
            AEItemKey key = roots.get(0); BigInteger factor = BigInteger.ONE;
            while (key != null && !items.contains(key)) {
                items.add(key); factors.add(factor);
                Edge edge = next.get(key);
                if (edge == null) key = null;
                else { factor = factor.multiply(BigInteger.valueOf(edge.ratio())); key = edge.higher(); }
            }
            if (items.size() == component.size() && key == null) chains.add(new BulkUnits(items, factors));
        }
        chains.sort(Comparator.comparing(chain -> chain.items().get(0).getId().toString()));
        return List.copyOf(chains);
    }
    /** data/<namespace>/eco_compression/*.json: explicit conversions and exclusions. */
    private static final class Rules extends SimpleJsonResourceReloadListener {
        Rules() { super(new Gson(), "eco_compression"); }
        @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            List<Edge> next = new ArrayList<>(); Set<AEItemKey> excluded = new HashSet<>();
            for (var file : files.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                try {
                    var root = file.getValue().getAsJsonObject();
                    if (root.has("disabled")) for (var value : root.getAsJsonArray("disabled")) excluded.add(key(value));
                    if (root.has("conversions")) for (var value : root.getAsJsonArray("conversions")) {
                        var edge = value.getAsJsonObject();
                        next.add(new Edge(key(edge.get("lower")), key(edge.get("higher")), edge.get("ratio").getAsInt()));
                    }
                } catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid ECO compression rules " + file.getKey(), invalid); }
            }
            overrides = List.copyOf(next); disabled = Set.copyOf(excluded);
        }
        /** An item id string, or Forge's {"item", "count", "nbt"} stack object. */
        private static AEItemKey key(JsonElement value) {
            ItemStack stack = value.isJsonPrimitive()
                ? CraftingHelper.getItemStack(GsonHelper.parse("{\"item\":" + value + "}"), false)
                : CraftingHelper.getItemStack(value.getAsJsonObject(), true);
            if (stack.isEmpty() || stack.getCount() != 1) throw new IllegalArgumentException("Compression rule keys require count 1");
            return AEItemKey.of(stack);
        }
    }
}
