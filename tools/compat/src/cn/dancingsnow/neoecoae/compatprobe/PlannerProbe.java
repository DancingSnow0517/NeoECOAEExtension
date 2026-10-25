package cn.dancingsnow.neoecoae.compatprobe;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Loads final GTL recipes into physical GTL buffers and compares actual planner routes.
 * Planning and native buffer admission are observed; no machine output is fabricated.
 */
@Mod("neoeco_compat")
public final class PlannerProbe {
    private final JsonObject report = new JsonObject();
    private final JsonArray results = new JsonArray();
    private final List<Case> cases = new ArrayList<>();
    private MinecraftServer server;
    private Path destination;
    private int index;
    private int ticks;
    private int stage;
    private boolean finished;
    private Boolean graphEnabled;
    private GtlFixture fixture;
    private ChestBlockEntity chest;
    private Future<ICraftingPlan> future;
    private JsonObject current;
    private long started;
    private record Case(String route, JsonObject spec) {
        String id() { return route + "." + spec.get("id").getAsString(); }
    }

    public PlannerProbe() {
        if (System.getProperty("neoeco.compat.variant", "").isBlank()) return;
        MinecraftForge.EVENT_BUS.addListener(this::start);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::stopping);
    }

    private void start(ServerStartedEvent event) {
        server = event.getServer();
        destination = Path.of(System.getProperty("neoeco.compat.report"));
        report.addProperty("schema", 1);
        report.add("tests", results);
        var mods = new JsonObject();
        ModList.get().getMods().forEach(mod -> mods.addProperty(mod.getModId(), mod.getVersion().toString()));
        report.add("mods", mods);
        report.addProperty("scope", "Loaded GTL recipe conversion, physical GTL pattern buffers and planner routes; no machine processing");
        try {
            var variant = System.getProperty("neoeco.compat.variant");
            require(variant.equals("baseline") || variant.equals("coexist"), "Unknown variant");
            require(ModList.get().isLoaded("neoecoae") == variant.equals("coexist"), "Unexpected NeoECO presence");
            try {
                graphEnabled = (Boolean) Class.forName("org.gtlcore.gtlcore.integration.ae2.graph.CraftingEngineRouter")
                        .getMethod("useGraph").invoke(null);
                report.addProperty("gtlGraphEnabled", graphEnabled);
            } catch (ReflectiveOperationException failure) {
                report.addProperty("gtlGraphInspectionError", trace(failure));
            }
            try (var stream = getClass().getResourceAsStream("/neoeco-compat-scenarios.json")) {
                require(stream != null, "Missing scenario specification");
                var specification = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var route : specification.getAsJsonArray("routes")) {
                    if (variant.equals("baseline") && !route.getAsString().equals("plain")) continue;
                    for (var spec : specification.getAsJsonArray("cases")) cases.add(new Case(route.getAsString(), spec.getAsJsonObject()));
                }
            }
            require(!cases.isEmpty(), "Empty test selection");
            next();
        } catch (Throwable failure) {
            report.addProperty("setupError", trace(failure));
            finish();
        }
    }

    private void next() {
        if (index == cases.size()) { finish(); return; }
        current = new JsonObject();
        current.addProperty("id", cases.get(index).id());
        current.addProperty("status", "running");
        current.add("fixture", cases.get(index).spec.deepCopy());
        results.add(current);
        ticks = 0;
        stage = 0;
        future = null;
        started = System.nanoTime();
        write();
    }

    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null || finished) return;
        try {
            require(++ticks < 1200, "Case exceeded 1200 server ticks at stage " + stage);
            var test = cases.get(index);
            var level = server.overworld();
            // Every case gets a fresh, isolated physical network. No cache or inventory is reused across routes.
            var origin = new BlockPos(index * 32, 100, 0);
            if (stage == 0) {
                level.setChunkForced(origin.getX() >> 4, 0, true);
                level.setBlockAndUpdate(origin, AEBlocks.CHEST.block().defaultBlockState());
                level.setBlockAndUpdate(origin.above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                level.setBlockAndUpdate(origin.north(), AEBlocks.CHEST.block().defaultBlockState());
                chest = (ChestBlockEntity) level.getBlockEntity(origin);
                var fluids = (ChestBlockEntity) level.getBlockEntity(origin.north());
                require(chest != null && fluids != null, "Fixture storage block entities not created");
                chest.setCell(AEItems.ITEM_CELL_64K.stack());
                fluids.setCell(AEItems.FLUID_CELL_64K.stack());
                fixture = new GtlFixture(test.spec, level);
                current.add("loadedRecipes", fixture.recipes);
                fixture.place(level, origin.east());
                stage = 1;
                return;
            }
            IGrid grid = chest.getMainNode().getGrid();
            if (stage == 1) {
                if (ticks < 40 || grid == null || !chest.getMainNode().isOnline()
                        || fixture.node.getGrid() != grid || !fixture.node.isOnline()) return;
                var storage = grid.getStorageService().getInventory();
                for (var entry : fixture.stock.entrySet()) {
                    long amount = entry.getValue();
                    require(storage.insert(entry.getKey(), amount, Actionable.MODULATE, IActionSource.empty()) == amount,
                            "Fixture storage rejected input " + entry.getKey());
                }
                stage = 2;
                return;
            }
            if (stage == 2) {
                if (ticks < 60) return;
                current.add("inventoryBefore", amounts(grid.getStorageService().getInventory().getAvailableStacks()));
                fixture.inspect(current);
                var patterns = fixture.provider.getAvailablePatterns();
                var patternClasses = new JsonArray();
                patterns.forEach(pattern -> patternClasses.add(pattern.getClass().getName()));
                current.add("patternClasses", patternClasses);
                current.addProperty("stage", "planning");
                future = calculate(test, level, grid);
                current.addProperty("futureClass", future.getClass().getName());
                stage = 3;
                return;
            }
            if (!future.isDone()) return;
            var plan = future.get();
            current.addProperty("planClass", plan.getClass().getName());
            current.addProperty("simulation", plan.simulation());
            current.addProperty("bytes", plan.bytes());
            current.add("used", amounts(plan.usedItems()));
            current.add("missing", amounts(plan.missingItems()));
            current.add("emitted", amounts(plan.emittedItems()));
            var vector = new JsonArray();
            long operations = 0;
            for (var entry : plan.patternTimes().entrySet()) {
                var row = new JsonObject();
                row.addProperty("pattern", entry.getKey().getDefinition().toString());
                row.addProperty("count", entry.getValue());
                vector.add(row);
                operations = Math.addExact(operations, entry.getValue());
            }
            current.add("patternTimes", vector);
            current.addProperty("operations", operations);
            boolean eco = ecoOwned(plan);
            boolean gtl = plan.getClass().getName().equals("org.gtlcore.gtlcore.integration.ae2.graph.AeGraphPlan");
            current.addProperty("ecoOwned", eco);
            current.addProperty("gtlGraphOwned", gtl);
            var after = amounts(grid.getStorageService().getInventory().getAvailableStacks());
            current.add("inventoryAfter", after);
            require(after.equals(current.get("inventoryBefore")), "Planning mutated physical storage");
            require(plan.finalOutput().what().equals(fixture.target)
                    && plan.finalOutput().amount() == fixture.amount, "Plan changed requested output");
            if (!test.route.equals("plain")) require(eco, "Explicit ECO request was not planned by ECO");
            else {
                require(graphEnabled != null, "Cannot establish GTL planner routing: router API unavailable");
                require(!eco && gtl == graphEnabled, "Plain request planner does not match GTL engine configuration");
            }
            boolean feasible = test.spec.get("feasible").getAsBoolean();
            require(plan.simulation() != feasible, "Unexpected feasibility; expected executable=" + feasible);
            if (feasible) require(operations == fixture.operations,
                    "Unexpected operation count: " + operations + "; expected " + fixture.operations);
            if (test.spec.has("dispatchRuns")) fixture.admit(test.spec.get("dispatchRuns").getAsLong(), current);
            current.addProperty("status", "pass");
            complete();
        } catch (Throwable failure) {
            current.addProperty("status", "fail");
            current.addProperty("stage", stage);
            current.addProperty("error", trace(failure));
            complete();
        }
    }

    @SuppressWarnings("unchecked")
    private Future<ICraftingPlan> calculate(Case test, ServerLevel level, IGrid grid) throws ReflectiveOperationException {
        AEKey target = fixture.target;
        long amount = fixture.amount;
        IGridNode requestNode = chest.getMainNode().getNode();
        ICraftingSimulationRequester requester = new ICraftingSimulationRequester() {
            public IActionSource getActionSource() { return IActionSource.empty(); }
            public IGridNode getGridNode() { return requestNode; }
        };
        if (test.route.equals("plain")) return grid.getCraftingService().beginCraftingCalculation(
                level, requester, target, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
        Class<?> optionsType = Class.forName("cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions");
        Object options = optionsType.getConstructor(boolean.class, boolean.class, Set.class).newInstance(true, false, Set.of());
        if (test.route.equals("eco_request")) {
            requester = (ICraftingSimulationRequester) Class.forName("cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerRequester")
                    .getConstructor(ICraftingSimulationRequester.class, optionsType).newInstance(requester, options);
            return grid.getCraftingService().beginCraftingCalculation(level, requester, target, amount,
                    CalculationStrategy.REPORT_MISSING_ITEMS);
        }
        return (Future<ICraftingPlan>) Class.forName("cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService")
                .getMethod("begin", Level.class, IGrid.class, IActionSource.class, AEKey.class, long.class,
                        CalculationStrategy.class, optionsType)
                .invoke(null, level, grid, IActionSource.empty(), target, amount, CalculationStrategy.REPORT_MISSING_ITEMS, options);
    }

    private static boolean ecoOwned(ICraftingPlan plan) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("neoecoae")) return false;
        return (Boolean) Class.forName("cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry")
                .getMethod("isECOOwnedPlan", ICraftingPlan.class).invoke(null, plan);
    }

    private void complete() {
        if (future != null && !future.isDone()) future.cancel(true);
        current.addProperty("ms", (System.nanoTime() - started) / 1_000_000);
        index++;
        next();
    }

    private void stopping(ServerStoppingEvent event) {
        if (finished || server == null) return;
        report.addProperty("setupError", "Server stopped before all cases completed");
        if (future != null) future.cancel(true);
        write();
    }

    private void finish() {
        finished = true;
        write();
        server.halt(false);
    }

    private void write() {
        try {
            var temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot write compatibility evidence", failure);
        }
    }

    private static JsonObject amounts(KeyCounter values) {
        var result = new JsonObject();
        for (var entry : values) if (entry.getLongValue() != 0) result.addProperty(entry.getKey().toString(), entry.getLongValue());
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String trace(Throwable failure) {
        var output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }
}
