package cn.dancingsnow.neoecoae.compatprobe;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerRequester;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.google.gson.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fluids.capability.IFluidHandler;
import static cn.dancingsnow.neoecoae.compatprobe.NeoMachineRig.*;

/** Thirty physical NeoECO environments in the unmodified GTL distribution. */
public final class NeoMachineSuite implements MachineSuite {
    private final MinecraftServer server;
    private final Path destination;
    private final Path checkpoint;
    private final JsonObject specification;
    private final JsonObject report;
    private final JsonArray tests;
    private final List<GtProcessor> processors = new ArrayList<>();
    private final List<PatternProviderBlockEntity> providers = new ArrayList<>();
    private final List<ICraftingLink> links = new ArrayList<>();
    private final List<ICraftingCPU> cpus = new ArrayList<>();
    private JsonObject current;
    private JsonObject spec;
    private NeoMachineRig rig;
    private MachineRecipes recipe;
    private GtlFixture gt;
    private ECOIntegratedWorkingStationBlockEntity processing;
    private AEKey target;
    private long requested;
    private long expected;
    private Future<ICraftingPlan> future;
    private int index;
    private int ticks;
    private int phase;
    private int phaseTick;
    private int pauseProgress;
    private boolean changed;
    private boolean resumed;
    private boolean finished;
    private long started;
    private final List<NeoMachineSuite> concurrent = new ArrayList<>();
    private boolean worker;
    private boolean restartAllowed;
    private boolean coldStarted;
    private int restartIndex = -1;

    public NeoMachineSuite(MinecraftServer server, Path destination, JsonObject specification) throws Exception {
        this.server = server;
        this.destination = destination;
        this.specification = specification;
        checkpoint = server.getWorldPath(LevelResource.ROOT).resolve("neoeco-compat-checkpoint.json");
        resumed = Files.isRegularFile(checkpoint);
        if (resumed) {
            var saved = JsonParser.parseString(Files.readString(checkpoint)).getAsJsonObject();
            index = saved.get("index").getAsInt();
            check(specification.getAsJsonArray("neoecoCases").get(index).getAsJsonObject().get("id").getAsString()
                    .equals(saved.get("id").getAsString()), "Cold restart changed its scenario identity");
            report = JsonParser.parseString(Files.readString(destination)).getAsJsonObject();
            tests = report.getAsJsonArray("tests");
            current = tests.get(tests.size() - 1).getAsJsonObject();
            report.addProperty("restartRequested", false);
            report.addProperty("coldRestartCount", 1);
            coldStarted = true;
            concurrent.add(new NeoMachineSuite(this, index, true));
        } else {
            report = new JsonObject();
            tests = new JsonArray();
            report.addProperty("schema", 2);
            report.addProperty("group", "neoeco");
            report.addProperty("scope", "NeoECO physical storage, CPUs, workers, workstations and real GT processing; native grid links and fixture inputs are supplied by the test harness");
            report.add("tests", tests);
            var mods = new JsonObject();
            net.minecraftforge.fml.ModList.get().getMods().forEach(mod -> mods.addProperty(mod.getModId(), mod.getVersion().toString()));
            report.add("mods", mods);
            report.addProperty("execution", "concurrent; cold restart runs after all ordinary scenarios");
            var cases = specification.getAsJsonArray("neoecoCases");
            for (int ordinal = 0; ordinal < cases.size(); ordinal++) {
                if (cases.get(ordinal).getAsJsonObject().get("operation").getAsString().equals("restart")) {
                    check(restartIndex == -1, "Only one exclusive cold-restart scenario is supported");
                    restartIndex = ordinal;
                } else concurrent.add(new NeoMachineSuite(this, ordinal, false));
            }
            check(restartIndex >= 0, "Missing cold-restart scenario");
            write();
        }
    }

    private NeoMachineSuite(NeoMachineSuite owner, int ordinal, boolean restore) {
        server = owner.server;
        destination = owner.destination;
        checkpoint = owner.checkpoint;
        specification = owner.specification;
        report = owner.report;
        tests = owner.tests;
        index = ordinal;
        worker = true;
        resumed = restore;
        if (restore) current = owner.current;
        begin(!restore);
    }

    @Override public boolean finished() { return finished; }
    @Override public int activeCount() { return (int) concurrent.stream().filter(test -> !test.finished).count(); }
    @Override public void allowRestart() { restartAllowed = true; }

    private void begin(boolean append) {
        spec = specification.getAsJsonArray("neoecoCases").get(index).getAsJsonObject();
        if (append) {
            current = new JsonObject();
            current.addProperty("id", "machine." + spec.get("id").getAsString());
            current.addProperty("status", "running");
            current.addProperty("startedAtTick", server.getTickCount());
            current.add("environment", spec.deepCopy());
            tests.add(current);
        }
        ticks = 0;
        phase = 0;
        phaseTick = 0;
        changed = false;
        future = null;
        recipe = null;
        gt = null;
        processing = null;
        rig = null;
        processors.clear();
        providers.clear();
        links.clear();
        cpus.clear();
        started = System.nanoTime();
        write();
    }

    private String operation() { return spec.get("operation").getAsString(); }
    private String category() { return spec.get("category").getAsString(); }

    @Override public void tick() {
        if (finished) return;
        if (!worker) {
            concurrent.forEach(NeoMachineSuite::tick);
            if (report.has("restartRequested") && report.get("restartRequested").getAsBoolean()) return;
            if (concurrent.stream().allMatch(test -> test.finished)) {
                if (!coldStarted && restartAllowed) {
                    concurrent.add(new NeoMachineSuite(this, restartIndex, false));
                    coldStarted = true;
                } else if (coldStarted) {
                    finished = true;
                    report.addProperty("complete", true);
                    write();
                }
            }
            return;
        }
        try {
            check(++ticks < spec.get("timeoutTicks").getAsInt(), "Environment timed out in phase " + phase);
            current.addProperty("phase", phase);
            if (phase == 0) {
                rig = new NeoMachineRig(server.overworld(), new BlockPos(index * 128, 100, 128), resumed);
                current.addProperty("storageHost", rig.storageHost.getClass().getName());
                current.addProperty("computationHost", rig.computation.getClass().getName());
                current.addProperty("craftingHost", rig.crafting.getClass().getName());
                phase = 1;
                return;
            }
            if (phase == 1) {
                if (ticks < 60 || !rig.ready()) return;
                current.addProperty("formed", true);
                if (resumed) {
                    recipe = new MachineRecipes(rig, spec.get("target").getAsString(), false);
                    processing = rig.workstation;
                    target = recipe.target;
                    expected = recipe.output;
                    current.addProperty("coldRestartResumed", true);
                    check(processing.getTask() != null || rig.amount(target) == expected, "Workstation lost its pending recipe across server restart");
                    resumed = false;
                    changed = true;
                    phase = 5;
                    phaseTick = ticks;
                    return;
                }
                if (category().equals("storage")) { storage(); complete(); return; }
                setup();
                phaseTick = ticks;
                return;
            }
            if (rig.power.getMainNode().getNode() != null) rig.connect(rig.power.getMainNode());
            for (var provider : providers) rig.connect(provider.getMainNode());
            if (gt != null && gt.node != null) rig.connect(gt.node);
            for (var processor : processors) processor.tick();
            if (processing != null && !operation().equals("blocked")) rig.collect(processing);
            if (phase == 2) {
                if (ticks - phaseTick < 40) return;
                check(rig.grid().getCraftingService().isCraftable(target), "Real provider did not advertise the requested output");
                startCalculation();
                phase = 3;
                return;
            }
            if (phase == 3) { plan(); return; }
            if (phase == 4) { order(); return; }
            if (phase == 5) { processing(); return; }
            if (phase == 6 && ticks - phaseTick >= 20) {
                check(rig.amount(target) == expected, "Output count changed after settlement");
                for (var processor : processors) processor.verifyTools();
                current.addProperty("finalAmount", rig.amount(target));
                current.addProperty("gtSuppliedEU", processors.stream().mapToLong(processor -> processor.suppliedEU).sum());
                complete();
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private void storage() {
        var inventory = rig.inventory();
        String operation = operation();
        if (operation.equals("items")) {
            for (String id : List.of("gtceu:certus_quartz_dust", "gtceu:steel_plate", "gtceu:fine_red_alloy_wire")) roundTrip(item(id), 4096);
        } else if (operation.equals("fluids")) {
            for (String id : List.of("gtceu:distilled_water", "gtceu:argon", "gtceu:glass", "gtceu:soldering_alloy")) roundTrip(fluid(id), 128000);
        } else if (operation.equals("nbt")) {
            var a = item("gtceu:programmed_circuit").toStack(); a.getOrCreateTag().putInt("Configuration", 1);
            var b = item("gtceu:programmed_circuit").toStack(); b.getOrCreateTag().putInt("Configuration", 24);
            var ka = AEItemKey.of(a); var kb = AEItemKey.of(b);
            check(!ka.equals(kb), "GTL circuit NBT collapsed before storage");
            rig.seed(ka, 17); rig.seed(kb, 29);
            check(inventory.extract(ka, 7, Actionable.MODULATE, IActionSource.empty()) == 7 && rig.amount(ka) == 10 && rig.amount(kb) == 29,
                    "NeoECO storage mixed circuit configurations");
        } else if (operation.equals("tags")) {
            var tag = TagKey.create(Registries.ITEM, new ResourceLocation("gtceu:circuits/mv"));
            var variants = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(tag).orElseThrow().stream().limit(2).toList();
            check(variants.size() == 2, "GTL MV circuit tag has fewer than two actual alternatives");
            roundTrip(AEItemKey.of(variants.get(0).value()), 31);
            roundTrip(AEItemKey.of(variants.get(1).value()), 47);
        } else if (operation.equals("long")) roundTrip(item("gtceu:certus_quartz_dust"), 2147483684L);
        else if (operation.equals("capacity")) {
            var key = item("gtceu:certus_quartz_dust");
            long simulated = inventory.insert(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
            check(simulated > 0 && rig.amount(key) == 0, "Capacity simulation mutated NeoECO storage");
            long accepted = inventory.insert(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
            check(accepted == simulated && rig.amount(key) == accepted, "Capacity admission differed from simulation");
            check(inventory.insert(key, 1, Actionable.MODULATE, IActionSource.empty()) == 0, "Full NeoECO storage accepted an extra item");
            check(inventory.extract(key, accepted, Actionable.SIMULATE, IActionSource.empty()) == accepted && rig.amount(key) == accepted,
                    "Extraction simulation mutated a full cell");
            current.addProperty("capacityAccepted", accepted);
        } else throw new AssertionError("Unknown storage operation " + operation);
        long mounted = rig.storageHost.getCluster().getDrives().stream().filter(drive -> drive.isMounted()).count();
        check(mounted == 3, "Storage test did not use the three NeoECO drives");
        current.addProperty("mountedNeoDrives", mounted);
    }

    private void roundTrip(AEKey key, long amount) {
        rig.seed(key, amount);
        check(rig.amount(key) == amount, "ME listing truncated or merged a GTL material");
        long physical = rig.storageHost.getCluster().getDrives().stream().map(drive -> drive.getCellInventory())
                .filter(Objects::nonNull).mapToLong(cell -> cell.getAvailableStacks().get(key)).sum();
        check(physical == amount, "ME listing and actual NeoECO cells disagree");
        long taken = amount / 3;
        check(rig.inventory().extract(key, taken, Actionable.MODULATE, IActionSource.empty()) == taken && rig.amount(key) == amount - taken,
                "NeoECO extraction changed the wrong quantity");
    }

    private JsonObject reference(String id) {
        for (var candidate : specification.getAsJsonObject("baseCases").getAsJsonArray("gtl"))
            if (candidate.getAsJsonObject().get("id").getAsString().equals(id)) return candidate.getAsJsonObject().deepCopy();
        throw new AssertionError("Missing real GTL recipe reference " + id);
    }

    private void setup() throws Exception {
        String op = operation();
        if (Set.of("workbench", "plain", "request", "concurrent").contains(op)) {
            recipe = new MachineRecipes(rig, "ae2:crafting_unit", true);
            var bus = rig.crafting.getCluster().getPatternBuses().get(0);
            bus.getPatternSlotInventory().setItemDirect(0, recipe.encoded);
            target = recipe.target;
            requested = recipe.output * 4;
            expected = requested;
            recipe.scaled(op.equals("concurrent") ? 6 : 4).forEach(rig::seed);
            current.addProperty("loadedRecipe", recipe.id);
            phase = 2;
        } else if (op.equals("buffer") || op.equals("cancel")) {
            gt = new GtlFixture(reference(op.equals("cancel") ? "fluix_water_autoclave" : spec.get("reference").getAsString()), rig.level);
            gt.place(rig.level, rig.origin.offset(96, 0, 0));
            gt.stock.forEach(rig::seed);
            target = gt.target; requested = gt.amount; expected = requested;
            phase = 2;
        } else if (spec.has("reference")) {
            var source = reference(spec.get("reference").getAsString());
            if (op.equals("alternatives")) {
                source.getAsJsonArray("recipes").add(reference("fluix_water_autoclave").getAsJsonArray("recipes").get(0).deepCopy());
                source.addProperty("amount", 4);
                source.remove("expectInputs");
            }
            gt = new GtlFixture(source, rig.level);
            gt.stock.forEach((key, amount) -> { if (!op.equals("alternatives") || !key.equals(fluid("minecraft:water"))) rig.seed(key, amount); });
            target = gt.target; requested = source.has("amount") ? source.get("amount").getAsLong() : gt.amount; expected = requested;
            for (int slot = 0; slot < source.getAsJsonArray("recipes").size(); slot++) {
                String type = source.getAsJsonArray("recipes").get(slot).getAsJsonObject().get("type").getAsString();
                processors.add(new GtProcessor(rig, gt, rig.origin.offset(8 + slot * 4, 0, 12), type, !op.equals("tools"), slot));
            }
            current.add("loadedRecipes", gt.recipes);
            phase = 2;
        } else {
            recipe = new MachineRecipes(rig, spec.get("target").getAsString(), false);
            target = recipe.target; requested = recipe.output; expected = requested;
            current.addProperty("loadedRecipe", recipe.id);
            if (op.equals("cross")) {
                var source = reference("lightning_fluix");
                gt = new GtlFixture(source, rig.level);
                gt.stock.forEach(rig::seed);
                recipe.inputs.forEach((key, amount) -> { if (!key.equals(gt.target)) rig.seed(key, amount); });
                processors.add(new GtProcessor(rig, gt, rig.origin.offset(8, 0, 12), "lightning_processor", true));
                processing = rig.workstation;
                addWorkstationProvider(false);
                phase = 2;
            } else {
                processing = op.equals("large") ? rig.large : rig.workstation;
                recipe.inputs.forEach(rig::seed);
                if (op.equals("large")) { addWorkstationProvider(true); phase = 2; }
                else {
                    rig.feed(processing, recipe.inputs);
                    if (op.equals("blocked")) {
                        check(target instanceof AEItemKey, "Blocked-output test requires an item result");
                        rig.seed(target, 64);
                        check(rig.inventory().extract(target, 64, Actionable.MODULATE, IActionSource.empty()) == 64, "Cannot own output blocker");
                        processing.getInternalInventory().setItemDirect(9, ((AEItemKey) target).toStack(64));
                        expected += 64;
                        current.addProperty("fixtureOutputBlocker", 64);
                    }
                    if (op.equals("power")) { pauseProgress = processing.getProcessingTime(); rig.powerOff(); }
                    phase = 5;
                }
            }
        }
    }

    private void addWorkstationProvider(boolean large) throws Exception {
        if (large) {
            var communication = (ECOLargeIntegratedWorkingStationInterfaceBlockEntity) rig.large.getCluster().getCommunication();
            communication.getPatternSlotInventory().setItemDirect(0, recipe.encoded);
            communication.refreshAdvertisedPatterns();
            rig.connect(communication.getMainNode());
        } else {
            var position = rig.origin.offset(47, 0, 0);
            rig.place(position, AEBlocks.PATTERN_PROVIDER.block());
            var provider = (PatternProviderBlockEntity) rig.level.getBlockEntity(position);
            provider.getLogic().getPatternInv().setItemDirect(0, recipe.encoded);
            provider.getLogic().updatePatterns();
            providers.add(provider);
        }
    }

    private void startCalculation() {
        if (gt != null && gt.node != null) {
            try { rig.connect(gt.node); } catch (Exception failure) { throw new IllegalStateException(failure); }
        }
        var requester = new ICraftingSimulationRequester() {
            public IActionSource getActionSource() { return IActionSource.empty(); }
            public appeng.api.networking.IGridNode getGridNode() { return rig.computation.getMainNode().getNode(); }
        };
        var options = new ECOPlannerOptions(true, false, Set.of());
        if (operation().equals("plain")) future = rig.grid().getCraftingService().beginCraftingCalculation(rig.level, requester, target, requested, CalculationStrategy.REPORT_MISSING_ITEMS);
        else if (operation().equals("request")) future = rig.grid().getCraftingService().beginCraftingCalculation(rig.level, new ECOPlannerRequester(requester, options), target, requested, CalculationStrategy.REPORT_MISSING_ITEMS);
        else future = ECOPlanningService.begin(rig.level, rig.grid(), IActionSource.empty(), target, requested, CalculationStrategy.REPORT_MISSING_ITEMS, options);
        current.addProperty("requestedAmount", requested);
    }

    private void plan() throws Exception {
        if (!future.isDone()) return;
        ICraftingPlan plan = future.get();
        current.addProperty("planClass", plan.getClass().getName());
        check(plan.finalOutput().what().equals(target) && plan.finalOutput().amount() == requested, "Planner changed the requested output");
        if (Set.of("missing", "long").contains(operation())) {
            check(plan.simulation() && !plan.missingItems().isEmpty(), "Missing-input GTL order did not report a simulation plan");
            current.addProperty("missing", plan.missingItems().toString());
            current.addProperty("stage", "planning");
            complete(); return;
        }
        check(!plan.simulation(), "Physical machine environment has no executable plan");
        var result = rig.grid().getCraftingService().submitJob(plan, null, rig.computation.getCluster().getFakeCPU(), false, IActionSource.empty());
        current.addProperty("submitResult", String.valueOf(result.errorCode()));
        check(result.successful(), "NeoECO CPU rejected the plan: " + result.errorCode() + "; " + result.errorDetail());
        if (result.link() != null) links.add(result.link());
        check(!rig.computation.getCluster().getActiveCPUs().isEmpty(), "No physical NeoECO threading core owns the submitted job");
        cpus.addAll(rig.computation.getCluster().getActiveCPUs());
        current.addProperty("activeNeoCPUs", rig.computation.getCluster().getActiveCPUs().size());
        if (operation().equals("concurrent")) {
            // A second independently calculated order competes for the same remaining inventory.
            future = ECOPlanningService.begin(rig.level, rig.grid(), IActionSource.empty(), target, requested, CalculationStrategy.REPORT_MISSING_ITEMS, new ECOPlannerOptions(true, false, Set.of()));
        }
        phase = 4;
        phaseTick = ticks;
    }

    private void order() throws Exception {
        String op = operation();
        if (op.equals("buffer")) {
            var held = gt.custody();
            if (!held.equals(gt.stock)) return;
            for (var input : gt.stock.keySet()) check(rig.amount(input) == 0, "ECO batch dispatch duplicated an input in storage");
            check(rig.amount(target) == 0, "A buffer with no processing controller fabricated an output");
            current.addProperty("admittedRuns", 64);
            current.addProperty("bufferCustody", held.toString());
            current.addProperty("stage", "eco-cpu-to-gtl-buffer-admission");
            complete(); return;
        }
        if (op.equals("tools") && !changed) {
            if (ticks - phaseTick < 60) { check(rig.amount(target) == 0, "GT machine produced without its reusable press"); return; }
            processors.forEach(GtProcessor::installTools);
            changed = true;
        }
        if (op.equals("cancel") && !changed) {
            if (gt.custody().isEmpty()) return;
            links.forEach(ICraftingLink::cancel);
            cpus.forEach(ICraftingCPU::cancelJob);
            changed = true;
            phaseTick = ticks;
            return;
        }
        if (op.equals("cancel")) {
            if (ticks - phaseTick < 60) return;
            check(links.stream().allMatch(ICraftingLink::isCanceled) && cpus.stream().noneMatch(ICraftingCPU::isBusy), "Canceled NeoECO order remains live");
            check(rig.amount(target) == 0, "Canceled buffer-only order fabricated machine output");
            var held = gt.custody();
            for (var input : gt.stock.entrySet()) {
                long remaining = Math.addExact(rig.amount(input.getKey()), held.getOrDefault(input.getKey(), 0L));
                check(remaining == input.getValue(), "Cancellation lost or duplicated an in-flight GTL material: " + input.getKey());
            }
            current.addProperty("bufferCustodyAfterCancel", held.toString());
            current.addProperty("stage", "cancel-refund");
            complete(); return;
        }
        if (op.equals("concurrent") && !changed && future.isDone()) {
            var other = future.get();
            if (!other.simulation()) {
                var submitted = rig.grid().getCraftingService().submitJob(other, null, rig.computation.getCluster().getFakeCPU(), false, IActionSource.empty());
                current.addProperty("secondSubmit", String.valueOf(submitted.errorCode()));
                check(!submitted.successful(), "Competing full-size order overcommitted a six-run ingredient budget");
            }
            changed = true;
        }
        if (cpus.stream().anyMatch(ICraftingCPU::isBusy) || !links.stream().allMatch(ICraftingLink::isDone) || op.equals("concurrent") && !changed) return;
        check(rig.amount(target) == expected, "Completed NeoECO order has an incorrect final quantity");
        if (op.equals("concurrent")) for (var entry : recipe.inputs.entrySet())
            check(rig.amount(entry.getKey()) == entry.getValue() * 2, "Concurrent orders changed the remaining ingredient budget");
        if (op.equals("alternatives")) check(rig.amount(fluid("gtceu:distilled_water")) == 0, "Autoclave did not consume its stocked fluid alternative");
        if (op.equals("cross")) check(processors.stream().mapToLong(processor -> processor.produced).sum() == 8,
                "NeoECO workstation did not obtain eight real GT-produced crystals");
        current.addProperty("stage", "order-complete");
        phase = 6; phaseTick = ticks;
    }

    private void processing() throws Exception {
        String op = operation();
        if (op.equals("blocked") && !changed) {
            if (ticks - phaseTick < 100) { check(processing.getInternalInventory().getStackInSlot(9).getCount() == 64, "Blocked workstation overwrote its output"); return; }
            rig.collect(processing); changed = true;
        }
        if (op.equals("blocked")) rig.collect(processing);
        if (op.equals("power") && !changed) {
            if (ticks - phaseTick < 60) { check(processing.getProcessingTime() == pauseProgress && rig.amount(target) == 0, "Unpowered NeoECO workstation advanced"); return; }
            rig.powerOn(); changed = true;
        }
        if (op.equals("restart") && !changed && ticks - phaseTick >= 5 && rig.amount(target) == 0) {
            check(processing.getTask() != null, "Restart test has no pending machine recipe");
            var saved = new JsonObject(); saved.addProperty("index", index);
            saved.addProperty("id", spec.get("id").getAsString());
            Files.writeString(checkpoint, saved.toString());
            report.addProperty("restartRequested", true);
            current.addProperty("stage", "awaiting-cold-restart");
            write();
            server.saveEverything(true, true, true);
            finished = true;
            server.halt(false);
            return;
        }
        if (rig.amount(target) != expected) return;
        if (op.equals("coolant")) {
            check(target instanceof AEFluidKey, "Coolant processing did not produce a fluid");
            var stack = ((AEFluidKey) target).toStack(Math.toIntExact(expected));
            var cooling = rig.level.getRecipeManager().getRecipes().stream().filter(source -> source instanceof CoolingRecipe)
                    .map(source -> (CoolingRecipe) source).filter(source -> source.input().test(stack)).findFirst().orElseThrow();
            check(rig.inventory().extract(target, cooling.inputAmount(), Actionable.MODULATE, IActionSource.empty()) == cooling.inputAmount(), "Cannot withdraw real produced coolant");
            stack.setAmount(cooling.inputAmount());
            check(rig.crafting.getCluster().getInputHatch().tank.fill(stack, IFluidHandler.FluidAction.EXECUTE) == cooling.inputAmount(), "NeoECO coolant hatch rejected produced fluid");
            rig.crafting.applyNetworkActiveCooling(true);
            check(rig.crafting.tryConsumeLocalCoolant(1, 0), "NeoECO controller did not consume its produced coolant");
            check(rig.crafting.getCluster().getInputHatch().tank.getFluidAmount() < cooling.inputAmount(), "Coolant recipe did not draw fluid from the real hatch");
            expected -= cooling.inputAmount();
            current.addProperty("coolingRecipe", cooling.id().toString());
        }
        for (var input : recipe.inputs.keySet()) check(rig.amount(input) == 0, "Workstation input remained duplicated in ME storage");
        current.addProperty("stage", "machine-output");
        phase = 6; phaseTick = ticks;
    }

    private void complete() {
        current.addProperty("status", "pass");
        advance();
    }
    private void fail(Throwable failure) {
        current.addProperty("status", "fail");
        var trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace));
        current.addProperty("error", trace.toString());
        try {
            if (rig != null && rig.grid() != null) {
                current.addProperty("inventoryAtFailure", rig.inventory().getAvailableStacks().toString());
                var machines = new JsonArray();
                for (var processor : processors) {
                    var state = new JsonObject();
                    state.addProperty("position", processor.position.toShortString());
                    state.addProperty("suppliedEU", processor.suppliedEU);
                    state.addProperty("produced", processor.produced);
                    state.addProperty("nativeState", rig.level.getBlockEntity(processor.position).saveWithFullMetadata().toString());
                    machines.add(state);
                }
                current.add("gtMachinesAtFailure", machines);
                if (processing != null) current.addProperty("workstationAtFailure", processing.saveWithFullMetadata().toString());
            }
        } catch (Exception unavailable) { current.addProperty("observationError", unavailable.toString()); }
        advance();
    }
    private void advance() {
        if (future != null && !future.isDone()) future.cancel(true);
        links.stream().filter(link -> !link.isDone() && !link.isCanceled()).forEach(ICraftingLink::cancel);
        cpus.stream().filter(ICraftingCPU::isBusy).forEach(ICraftingCPU::cancelJob);
        current.addProperty("ms", (System.nanoTime() - started) / 1_000_000);
        current.addProperty("completedAtTick", server.getTickCount());
        if (rig != null) for (int x = -1; x <= 6; x++) for (int z = -1; z <= 2; z++)
            rig.level.setChunkForced((rig.origin.getX() >> 4) + x, (rig.origin.getZ() >> 4) + z, false);
        finished = true;
        write();
    }
    private void write() {
        try {
            var temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception failure) { throw new IllegalStateException("Cannot persist machine test evidence", failure); }
    }
    @Override public void stopping() {
        if (finished || report.has("restartRequested") && report.get("restartRequested").getAsBoolean()) return;
        for (var test : concurrent) if (test.future != null) test.future.cancel(true);
        report.addProperty("setupError", "Server stopped before all NeoECO machine environments completed");
        write();
    }
}
