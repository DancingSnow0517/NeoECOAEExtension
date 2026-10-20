"""One-time migration of main's recipe catalog to actual Forge 1.20 recipe objects."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]
path = ROOT / "src/main/java/cn/dancingsnow/neoecoae/recipe/LargeWorkstationRecipes.java"
s = path.read_text(encoding="utf-8")
if "private static BooleanSupplier ignoreUpgradeCards" in s:
    raise SystemExit("Catalog migration is already applied")
start = s.index("    @SuppressWarnings")
end = s.index("    private record Cache(", start)
s = s[:start] + s[end:]
s = s.replace("import cn.dancingsnow.neoecoae.config.NEConfig;", """import cn.dancingsnow.neoecoae.mixins.minecraft.RecipeManagerAccessor;
import java.util.function.BooleanSupplier;
import java.util.Objects;""")
s = s.replace("import net.minecraft.core.HolderLookup;", "import net.minecraft.core.RegistryAccess;")
bad = {
    "import net.minecraft.core.component.DataComponentPredicate;",
    "import net.minecraft.world.item.crafting.RecipeHolder;",
    "import net.minecraftforge.common.crafting.DataComponentIngredient;",
    "import net.minecraftforge.fluids.crafting.DataComponentFluidIngredient;",
}
s = "\n".join(line for line in s.splitlines() if line not in bad) + "\n"
s = s.replace("HolderLookup.Provider", "RegistryAccess").replace("Collection<RecipeHolder<?>>", "Collection<Recipe<?>>")
s = s.replace("registries.createSerializationContext(JsonOps.INSTANCE)", "JsonOps.INSTANCE")
s = s.replace("NEConfig.largeWorkstationIgnoreUpgradeCards", "ignoreUpgradeCards.getAsBoolean()")
s = s.replace("    private static final Map<RecipeManager, Cache> CACHE", """    private static BooleanSupplier ignoreUpgradeCards = () -> false;

    public static void configure(BooleanSupplier supplier) { ignoreUpgradeCards = Objects.requireNonNull(supplier); }

    private static final Map<RecipeManager, Cache> CACHE""")
a = s.index("        // RecipeManager replaces")
b = s.index("        var result = deduplicate", a)
s = s[:a] + """        Map<ResourceLocation, Recipe<?>> sourceMap = manager instanceof RecipeManagerAccessor accessor
            ? accessor.neoecoae$recipesByName() : null;
        Collection<Recipe<?>> sources = sourceMap == null ? manager.getRecipes() : sourceMap.values();
        var previous = CACHE.get(manager);
        if (previous != null && (sourceMap != null ? previous.generation == sourceMap : sameSources(previous.sources, sources))) return previous.recipes;
        List<LargeWorkstationRecipe> recipes = new ArrayList<>();
        DynamicOps<JsonElement> ops = JsonOps.INSTANCE;
        for (var source : sources) {
            try {
                if (source instanceof ExtendedCrystalAssemblerRecipe extended) {
                    recipes.add(new LargeWorkstationRecipe(source.getId(), extended.display(), extended.display().energy(),
                        List.of(), ExtendedCrystalAssemblerRecipe.SOURCE_TYPE));
                } else if (source instanceof LargeIntegratedWorkingStationRecipe large) {
                    recipes.add(new LargeWorkstationRecipe(source.getId(), large.display(), large.display().energy(), List.of()));
                } else if (source instanceof IntegratedWorkingStationRecipe nativeRecipe) {
                    recipes.add(new LargeWorkstationRecipe(source.getId(), nativeRecipe, nativeRecipe.energy(), List.of()));
                } else {
                    String type = typeId(source.getType());
                    if (!COMPAT_TYPES.contains(type)) continue;
                    recipes.add(LargeWorkstationRecipeAdapters.adapt(source, type));
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("Cannot adapt large workstation recipe {}", source.getId(), failure);
            }
        }
""" + s[b:]
s = s.replace("new Cache(sources, result,", "new Cache(sourceMap, List.copyOf(sources), result,")
a = s.index("        if (ingredient.getCustomIngredient()")
b = s.index("    private record FluidInput", a)
s = s[:a] + """        // Unknown custom predicates retain their complete JSON rather than being merged by display stacks.
        return ingredient.toJson();
    }

    private static Object fluidPredicate(FluidIngredient ingredient, DynamicOps<JsonElement> ops) {
        if (ingredient.isSimple() && ingredient.getStacks().length > 0) {
            return Arrays.stream(ingredient.getStacks()).map(stack -> BuiltInRegistries.FLUID.getKey(stack.getFluid()))
                .collect(Collectors.toUnmodifiableSet());
        }
        return ingredient.toJson();
    }

    private static boolean sameSources(Collection<Recipe<?>> previous, Collection<Recipe<?>> current) {
        if (previous.size() != current.size()) return false;
        Map<ResourceLocation, Recipe<?>> byId = new HashMap<>();
        for (var recipe : previous) byId.put(recipe.getId(), recipe);
        return current.stream().allMatch(recipe -> byId.get(recipe.getId()) == recipe);
    }

""" + s[b:]
s = "\n".join(line for line in s.splitlines() if "private record ComponentPredicate" not in line) + "\n"
s = s.replace("private record Cache(Collection<Recipe<?>> sources,", "private record Cache(Object generation, Collection<Recipe<?>> sources,")
path.write_text(s, encoding="utf-8")
path = ROOT / "src/main/resources/neoecoae.mixins.json"
data = json.loads(path.read_text(encoding="utf-8"))
data["mixins"].append("minecraft.RecipeManagerAccessor")
path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
print("Adapted recipe objects, full predicates, and stable reload generation")
