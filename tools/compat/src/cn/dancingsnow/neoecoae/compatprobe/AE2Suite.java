package cn.dancingsnow.neoecoae.compatprobe;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.google.gson.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.material.Fluids;

/** Identical AE2-only fixtures in both variants; contains no NeoECO API references. */
final class AE2Suite {
    private final MinecraftServer server;
    private final Path destination;
    private final JsonArray cases;
    private final JsonObject report;
    private final JsonArray tests;
    private JsonObject current;
    private ChestBlockEntity items;
    private ChestBlockEntity fluids;
    private PatternProviderBlockEntity provider;
    private AENetworkBlockEntity cpuBlock;
    private Future<ICraftingPlan> calculation;
    private ICraftingLink link;
    private ICraftingCPU selectedCpu;
    private AEKey target;
    private long requested;
    private final Map<AEKey, Long> input = new LinkedHashMap<>();
    private ItemStack held;
    private BlockPos origin;
    private int index;
    private int ticks;
    private int phase;
    private int since;
    private boolean finished;
    private final List<AE2Suite> concurrent = new ArrayList<>();
    private boolean worker;

    AE2Suite(MinecraftServer server, Path destination, JsonArray cases) throws Exception {
        this.server = server;
        this.destination = destination;
        this.cases = cases;
        if (Files.exists(destination)) {
            report = JsonParser.parseString(Files.readString(destination)).getAsJsonObject();
            tests = report.getAsJsonArray("tests");
            // The only resumed process is the NeoECO cold-restart test. Preserve completed AE2 evidence.
            if (tests.size() == cases.size() && report.has("complete") && report.get("complete").getAsBoolean()) finished = true;
            else throw new IllegalStateException("An incomplete AE2 report cannot be reused after restart");
        } else {
            report = new JsonObject(); tests = new JsonArray(); report.add("tests", tests);
            report.addProperty("group", "base_ae2");
            report.addProperty("scope", "Identical physical AE2 storage, pattern, CPU and molecular assembler fixtures in both variants");
            report.addProperty("execution", "concurrent");
            for (int ordinal = 0; ordinal < cases.size(); ordinal++)
                concurrent.add(new AE2Suite(this, ordinal));
            write();
        }
    }

    private AE2Suite(AE2Suite owner, int ordinal) {
        server = owner.server;
        destination = owner.destination;
        cases = owner.cases;
        report = owner.report;
        tests = owner.tests;
        index = ordinal;
        worker = true;
        next();
    }

    boolean finished() { return finished; }
    int activeCount() { return (int) concurrent.stream().filter(test -> !test.finished()).count(); }
    void stopping() {
        if (finished) return;
        report.addProperty("setupError", "Server stopped before all AE2 environments completed");
        for (var test : concurrent) {
            if (test.calculation != null) test.calculation.cancel(true);
            if (test.selectedCpu != null && test.selectedCpu.isBusy()) test.selectedCpu.cancelJob();
        }
        write();
    }
    private String operation() { return cases.get(index).getAsJsonObject().get("operation").getAsString(); }
    private IGrid grid() { return items.getMainNode().getGrid(); }
    private long amount(AEKey key) { return grid().getStorageService().getInventory().getAvailableStacks().get(key); }
    private void seed(AEKey key, long quantity) { check(grid().getStorageService().getInventory().insert(key, quantity, Actionable.MODULATE, IActionSource.empty()) == quantity, "AE2 rejected fixture material"); }

    private void next() {
        if (index == cases.size()) { finished = true; report.addProperty("complete", true); write(); return; }
        current = new JsonObject();
        current.addProperty("id", "ae2." + cases.get(index).getAsJsonObject().get("id").getAsString());
        current.addProperty("status", "running");
        current.addProperty("startedAtTick", server.getTickCount());
        tests.add(current);
        ticks = 0; phase = 0; calculation = null; link = null; selectedCpu = null; input.clear(); write();
    }

    void tick() {
        if (finished) return;
        if (!worker) {
            concurrent.forEach(AE2Suite::tick);
            if (concurrent.stream().allMatch(AE2Suite::finished)) {
                finished = true;
                report.addProperty("complete", true);
                write();
            }
            return;
        }
        try {
            check(++ticks < cases.get(index).getAsJsonObject().get("timeoutTicks").getAsInt(), "AE2 environment timed out in phase " + phase);
            var level = server.overworld();
            if (phase == 0) {
                origin = new BlockPos(index * 64, 100, -128);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setChunkForced((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z, true);
                level.setBlockAndUpdate(origin, AEBlocks.CHEST.block().defaultBlockState());
                level.setBlockAndUpdate(origin.north(), AEBlocks.CHEST.block().defaultBlockState());
                level.setBlockAndUpdate(origin.above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                level.setBlockAndUpdate(origin.east(), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
                level.setBlockAndUpdate(origin.east(2), AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
                level.setBlockAndUpdate(origin.west(), AEBlocks.CRAFTING_STORAGE_256K.block().defaultBlockState());
                level.setBlockAndUpdate(origin.west().above(), AEBlocks.CRAFTING_ACCELERATOR.block().defaultBlockState());
                items = (ChestBlockEntity) level.getBlockEntity(origin);
                fluids = (ChestBlockEntity) level.getBlockEntity(origin.north());
                provider = (PatternProviderBlockEntity) level.getBlockEntity(origin.east());
                cpuBlock = (AENetworkBlockEntity) level.getBlockEntity(origin.west());
                items.setCell(AEItems.ITEM_CELL_64K.stack()); fluids.setCell(AEItems.FLUID_CELL_64K.stack());
                phase = 1; return;
            }
            if (phase == 1) {
                if (ticks < 60 || grid() == null || !items.getMainNode().isOnline() || !fluids.getMainNode().isOnline()) return;
                switch (operation()) {
                    case "items" -> { roundTrip(AEItemKey.of(Items.IRON_INGOT), 4096); pass(); }
                    case "fluids" -> { roundTrip(AEFluidKey.of(Fluids.WATER), 128000); pass(); }
                    case "simulate" -> {
                        var key = AEItemKey.of(Items.IRON_INGOT);
                        check(grid().getStorageService().getInventory().insert(key, 64, Actionable.SIMULATE, IActionSource.empty()) == 64 && amount(key) == 0, "AE2 insert simulation changed storage");
                        seed(key, 64);
                        check(grid().getStorageService().getInventory().extract(key, 64, Actionable.SIMULATE, IActionSource.empty()) == 64 && amount(key) == 64, "AE2 extraction simulation changed storage"); pass();
                    }
                    case "nbt" -> {
                        var a = new ItemStack(Items.POTION); a.getOrCreateTag().putString("Potion", "minecraft:water");
                        var b = new ItemStack(Items.POTION); b.getOrCreateTag().putString("Potion", "minecraft:awkward");
                        var ka = AEItemKey.of(a); var kb = AEItemKey.of(b);
                        check(!ka.equals(kb), "AE2 collapsed different potion NBT"); seed(ka, 3); seed(kb, 7);
                        check(amount(ka) == 3 && amount(kb) == 7, "AE2 storage merged distinct NBT"); pass();
                    }
                    case "cell" -> { target = AEItemKey.of(Items.IRON_INGOT); seed(target, 123); held = items.getCell(); items.setCell(ItemStack.EMPTY); phase = 2; since = ticks; }
                    case "power" -> {
                        level.removeBlock(origin.above(), false);
                        items.extractAEPower(Double.MAX_VALUE, Actionable.MODULATE, PowerMultiplier.ONE);
                        fluids.extractAEPower(Double.MAX_VALUE, Actionable.MODULATE, PowerMultiplier.ONE);
                        grid().getEnergyService().extractAEPower(Double.MAX_VALUE, Actionable.MODULATE, PowerMultiplier.ONE);
                        phase = 2; since = ticks;
                    }
                    default -> { crafting(); phase = 3; since = ticks; }
                }
                return;
            }
            if (phase == 2 && ticks - since >= 30) {
                if (operation().equals("cell")) { check(amount(target) == 0, "Removed AE2 cell remained mounted"); items.setCell(held); }
                else { check(!grid().getEnergyService().isNetworkPowered(), "AE2 network stayed powered after its only source was removed"); level.setBlockAndUpdate(origin.above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState()); }
                phase = 7; since = ticks; return;
            }
            if (phase == 7 && ticks - since >= 40) {
                check(items.getMainNode().isOnline(), "AE2 storage did not recover");
                if (operation().equals("cell")) check(amount(target) == 123, "AE2 cell reinstall lost or duplicated contents");
                pass(); return;
            }
            if (phase == 3 && ticks - since >= 40) {
                check(grid().getCraftingService().isCraftable(target), "AE2 provider did not publish the real crafting pattern");
                if (operation().equals("publication")) { current.addProperty("stage", "pattern-publication"); pass(); return; }
                var requester = new ICraftingSimulationRequester() {
                    public IActionSource getActionSource() { return IActionSource.empty(); }
                    public appeng.api.networking.IGridNode getGridNode() { return provider.getMainNode().getNode(); }
                };
                calculation = grid().getCraftingService().beginCraftingCalculation(level, requester, target, requested, CalculationStrategy.REPORT_MISSING_ITEMS);
                phase = 4; return;
            }
            if (phase == 4 && calculation.isDone()) {
                var plan = calculation.get(); current.addProperty("planClass", plan.getClass().getName());
                check(plan.finalOutput().what().equals(target) && plan.finalOutput().amount() == requested, "AE2 plan changed the request");
                if (operation().equals("missing")) { check(plan.simulation() && !plan.missingItems().isEmpty(), "AE2 missing-input request became executable"); pass(); return; }
                check(!plan.simulation(), "Stocked AE2 fixture produced a simulation plan");
                var cpu = grid().getCraftingService().getCpus().stream()
                        .filter(candidate -> candidate.getClass().getName().startsWith("appeng.") && !candidate.isBusy()).findFirst().orElseThrow();
                current.addProperty("cpuClass", cpu.getClass().getName());
                selectedCpu = cpu;
                var result = grid().getCraftingService().submitJob(plan, null, cpu, false, IActionSource.empty());
                check(result.successful(), "Native AE2 CPU rejected the job: " + result.errorCode());
                link = result.link();
                check(cpu.isBusy(), "Successful standalone submission has no active native CPU job");
                if (operation().equals("cancel")) cpu.cancelJob();
                phase = 5; since = ticks; return;
            }
            if (phase == 5) {
                if (operation().equals("cancel")) {
                    if (ticks - since < 40) return;
                    check(!selectedCpu.isBusy() && amount(target) == 0, "AE2 canceled job executed or remained active");
                    for (var entry : input.entrySet()) check(amount(entry.getKey()) == entry.getValue() * 2, "AE2 cancel refund changed material custody");
                    pass(); return;
                }
                if (selectedCpu.isBusy() || link != null && !link.isDone()) return;
                check(amount(target) == requested, "Native AE2 CPU completed without delivering the requested output");
                for (var key : input.keySet()) check(amount(key) == 0, "Native AE2 craft did not consume the actual input");
                current.addProperty("stage", "order-complete"); current.addProperty("finalAmount", amount(target)); pass();
            }
        } catch (Throwable failure) {
            current.addProperty("status", "fail"); current.addProperty("phase", phase);
            var trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace)); current.addProperty("error", trace.toString()); advance();
        }
    }

    private void crafting() {
        target = AEItemKey.of(AEBlocks.CRAFTING_UNIT.stack());
        // Use the pack's final crafting-unit recipe, including KubeJS changes to GT ingredients.
        var recipe = server.getRecipeManager().getRecipes().stream().filter(source -> source instanceof CraftingRecipe)
                .map(source -> (CraftingRecipe) source).filter(source -> AEItemKey.of(source.getResultItem(server.registryAccess())) != null
                        && AEItemKey.of(source.getResultItem(server.registryAccess())).equals(target))
                .sorted(Comparator.comparing(source -> source.getId().toString())).findFirst().orElseThrow();
        ItemStack[] grid = new ItemStack[9]; Arrays.fill(grid, ItemStack.EMPTY);
        for (int slot = 0; slot < recipe.getIngredients().size(); slot++) {
            var ingredient = recipe.getIngredients().get(slot);
            if (ingredient.isEmpty()) continue;
            check(ingredient.getItems().length > 0, "Pack crafting ingredient has no actual alternatives");
            var stack = ingredient.getItems()[0].copy(); stack.setCount(1);
            int destination = recipe instanceof ShapedRecipe shaped ? slot / shaped.getWidth() * 3 + slot % shaped.getWidth() : slot;
            grid[destination] = stack; input.merge(AEItemKey.of(stack), 1L, Math::addExact);
        }
        ItemStack result = recipe.getResultItem(server.registryAccess()); requested = result.getCount() * 2L;
        provider.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeCraftingPattern(recipe, grid, result, true, false));
        provider.getLogic().updatePatterns();
        if (!operation().equals("missing")) input.forEach((key, amount) -> seed(key, amount * 2));
        current.addProperty("loadedRecipe", recipe.getId().toString());
    }

    private void roundTrip(AEKey key, long quantity) {
        seed(key, quantity); check(amount(key) == quantity, "AE2 inventory listing differs from insertion");
        long taken = grid().getStorageService().getInventory().extract(key, quantity / 2, Actionable.MODULATE, IActionSource.empty());
        check(taken == quantity / 2 && amount(key) == quantity - taken, "AE2 extraction changed the wrong amount");
    }
    private void pass() { current.addProperty("status", "pass"); advance(); }
    private void advance() {
        current.addProperty("completedAtTick", server.getTickCount());
        if (calculation != null && !calculation.isDone()) calculation.cancel(true);
        if (link != null && !link.isDone() && !link.isCanceled()) link.cancel();
        if (selectedCpu != null && selectedCpu.isBusy()) selectedCpu.cancelJob();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) server.overworld().setChunkForced((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z, false);
        finished = true;
        write();
    }
    private void write() {
        try {
            Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception failure) { throw new IllegalStateException("Cannot write AE2 fixture evidence", failure); }
    }
    private static void check(boolean condition, String detail) { if (!condition) throw new AssertionError(detail); }
}
