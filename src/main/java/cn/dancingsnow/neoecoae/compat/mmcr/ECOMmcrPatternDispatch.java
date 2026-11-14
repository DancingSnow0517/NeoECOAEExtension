package cn.dancingsnow.neoecoae.compat.mmcr;

import static cn.dancingsnow.neoecoae.compat.processing.ProcessingReflection.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission.StopReason;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** MMCR's bound native batch protocol; ECO retains its task identity and multiplier policy. */
public final class ECOMmcrPatternDispatch {
    public static final String HOST = "cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity";
    public static final String PORT = "cn.howxu.mmcr.internal.tile.IOPortBlockEntity";
    public static final String CONTROLLER = "cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity";
    private static final String PATTERN = "cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchDetails";
    private static final String PLANNING = "cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning";
    private static final String SUPPORT = "cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchSupport";
    private static final ClassValue<Optional<Access>> ACCESS = new ClassValue<>() {
        @Override protected Optional<Access> computeValue(Class<?> type) {
            try { return Optional.of(new Access(type.getClassLoader())); }
            catch (ReflectiveOperationException | LinkageError unavailable) { return Optional.empty(); }
        }
    };

    private ECOMmcrPatternDispatch() {}

    @Nullable
    public static BlockEntity host(Object provider) {
        try {
            if (provider instanceof PatternProviderLogicAccessor accessor) {
                Object host = accessor.neoecoae$getHost();
                return host instanceof BlockEntity block && is(block, HOST) ? block : null;
            }
            Object logic = call(provider, "getLogic");
            return logic == provider || !(logic instanceof PatternProviderLogicAccessor) ? null : host(logic);
        } catch (RuntimeException | LinkageError unavailable) { return null; }
    }

    @Nullable
    public static Target processingRoot(Object provider) {
        var host = host(provider);
        return host == null || host.getLevel() == null ? null : target(host);
    }

    @Nullable
    public static Session open(Object provider, IPatternDetails pattern) {
        var host = host(provider);
        if (host == null || !(host.getLevel() instanceof ServerLevel)
                || pattern == null || !pattern.getClass().getName().equals(PATTERN)) return null;
        Object logic = provider instanceof PatternProviderLogic ? provider : call(provider, "getLogic");
        if (!(logic instanceof ICraftingProvider crafting)) return null;
        var access = ACCESS.get(host.getClass()).orElse(null);
        if (access == null || !access.pattern.isInstance(pattern)) return null;
        try {
            Object data = access.data.invoke(pattern);
            if (integer(call(data, "version")) != 1) return null;
            Object recipe = call(data, "recipeId"), pool = call(data, "recipePoolId");
            long multiplier = count(call(data, "multiplier"));
            long recipeCrafts = count(call(data, "recipeCraftsPerPattern"));
            if (!(recipe instanceof ResourceLocation recipeId) || !(pool instanceof ResourceLocation poolId)
                    || multiplier < 1 || recipeCrafts < 1) return null;
            return new Session(host, crafting, pattern, access, recipeId, poolId, multiplier, recipeCrafts);
        } catch (ReflectiveOperationException | RuntimeException incompatible) { return null; }
    }

    public static List<Target> linkedTargets(BlockEntity host) {
        if (!is(host, PORT) || host.getLevel() == null || host.isRemoved()) return List.of();
        var targets = new ArrayList<Target>();
        Object links = call(host, "linkedControllerPositions");
        if (links instanceof Iterable<?> positions) for (Object position : positions) {
            if (position instanceof BlockPos pos) targets.add(new Target(host.getLevel().dimension().location(), pos));
        }
        return List.copyOf(targets);
    }

    /** Reads the already published value, without invoking MMCR's lazy NBT restoration. */
    @Nullable
    public static Object publishedSnapshot(BlockEntity controller) {
        if (!is(controller, CONTROLLER) || controller.isRemoved()) return null;
        if (Boolean.TRUE.equals(field(controller, "loadingRuntime"))
                || field(controller, "pendingFactoryRuntimeInput") != null
                || field(controller, "pendingCraftingRuntimeInput") != null
                || field(controller, "pendingNormalPatternMemory") != null) return null;
        return field(field(controller, "runtime"), "publishedSnapshot");
    }

    /** Uses MMCR's own resource-family identities, including exact output components and Lightning/Mek keys. */
    public static boolean matchesOutput(BlockEntity controller, Object presentation, AEKey output) {
        var access = ACCESS.get(controller.getClass()).orElse(null);
        if (access == null || presentation == null || output == null) return false;
        try {
            Object totals = access.stackTotals.invoke(null, List.of(new GenericStack(output, 1L)));
            if (!(totals instanceof Map<?, ?> identities) || identities.isEmpty()) return false;
            Object values = call(presentation, "outputs");
            if (values instanceof Iterable<?> entries) for (Object entry : entries) {
                if (count(call(entry, "amount")) <= 0L) continue;
                Object nativeOutput = call(entry, "output");
                if (nativeOutput != null && identities.containsKey(access.outputIdentity.invoke(null, nativeOutput))) return true;
            }
        } catch (ReflectiveOperationException | RuntimeException unknownFamily) { /* No association proof. */ }
        return false;
    }

    private static Target target(BlockEntity block) {
        return new Target(block.getLevel().dimension().location(), block.getBlockPos());
    }

    /** All names are from MMCR 1.21.1's batch protocol; unknown versions stand down. */
    private static final class Access {
        final Class<?> pattern;
        final Method scaled, data, unit, original, capture, recipe, resolve, inspectRecipe, capturedFailure,
                moduleGate, stackTotals, outputIdentity;
        final Object resolver;
        Access(ClassLoader loader) throws ReflectiveOperationException {
            pattern = Class.forName(PATTERN, false, loader);
            scaled = pattern.getMethod("scaled", long.class);
            data = pattern.getMethod("data");
            unit = pattern.getMethod("unitPattern");
            original = pattern.getMethod("original");
            capture = Class.forName(PLANNING, false, loader).getMethod("capture", IGrid.class);
            Class<?> support = Class.forName(SUPPORT, false, loader);
            Class<?> recipeType = Class.forName("cn.howxu.mmcr.api.recipe.MachineRecipe", false, loader);
            recipe = Class.forName("cn.howxu.mmcr.api.recipe.RecipeRegistry", false, loader)
                    .getMethod("getRecipe", ResourceLocation.class);
            Class<?> snapshot = Class.forName("cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot", false, loader);
            Class<?> resolverType = Class.forName("cn.howxu.mmcr.api.recipe.EffectiveRecipeResolver", false, loader);
            resolver = resolverType.getConstructor().newInstance();
            resolve = resolverType.getMethod("resolve", recipeType, snapshot, List.class);
            capturedFailure = Class.forName("cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner", false, loader)
                    .getMethod("capturedRequirementFailure", snapshot, recipeType);
            moduleGate = Class.forName("cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus", false, loader)
                    .getMethod("canRunRecipe", java.util.Set.class);
            inspectRecipe = support.getMethod("inspectRecipe", recipeType,
                    Class.forName("cn.howxu.mmcr.api.recipe.EffectiveRecipe", false, loader), IPatternDetails.class);
            stackTotals = support.getDeclaredMethod("stackTotals", List.class);
            outputIdentity = support.getDeclaredMethod("outputIdentity",
                    Class.forName("cn.howxu.mmcr.api.recipe.MachineOutput", false, loader));
            if (!stackTotals.trySetAccessible() || !outputIdentity.trySetAccessible()) {
                throw new IllegalAccessException("MMCR output identity contract is unavailable");
            }
        }
    }

    public static final class Session {
        private final BlockEntity host;
        private final ICraftingProvider provider;
        private final IPatternDetails pattern;
        private final Access access;
        private final ResourceLocation recipeId, poolId;
        private final long multiplier, recipeCrafts;
        private List<Target> startedTargets = List.of();

        private Session(BlockEntity host, ICraftingProvider provider, IPatternDetails pattern, Access access,
                ResourceLocation recipeId, ResourceLocation poolId, long multiplier, long recipeCrafts) {
            this.host = host;
            this.provider = provider;
            this.pattern = pattern;
            this.access = access;
            this.recipeId = recipeId;
            this.poolId = poolId;
            this.multiplier = multiplier;
            this.recipeCrafts = recipeCrafts;
        }

        public Target root() { return target(host); }
        public List<Target> startedTargets() { return startedTargets; }

        /** The publication is immutable and refreshed by MMCR, not by a recipe scan in the CPU. */
        public long maxBatchSize() {
            try {
                Object node = call(host, "getMainNode");
                if (!(node instanceof IManagedGridNode managed) || managed.getGrid() == null) return 0L;
                Object snapshot = access.capture.invoke(null, managed.getGrid());
                Object limits = call(snapshot, "limits");
                var unit = (IPatternDetails) access.unit.invoke(pattern);
                Object limit = limits instanceof Map<?, ?> entries ? entries.get(unit.getDefinition()) : null;
                long maximum = "PROVEN_LINEAR".equals(String.valueOf(call(limit, "safety")))
                        ? count(call(limit, "maxMultiplier")) : limit == null ? 0L : 1L;
                return Math.max(0L, maximum / multiplier);
            } catch (ReflectiveOperationException | RuntimeException incompatible) { return 0L; }
        }

        /** A native packet occupies one idle controller/lane; capacities of separate lanes are not added. */
        public long availableBatchSize() {
            if (!(host.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) return 0L;
            try { return availableCapacity(captureControllers(level)); }
            catch (RuntimeException unavailable) { return 0L; }
        }

        public ECOBatchAdmission submit(KeyCounter[] totalInputs, long copies) {
            startedTargets = List.of();
            if (!(host.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()
                    || host.isRemoved() || provider.isBusy()) return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
            long maximum = maxBatchSize();
            if (maximum <= 0L) return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
            if (copies <= 0L || copies > maximum) return ECOBatchAdmission.rejected(StopReason.CAPACITY);
            Map<BlockEntity, Object> before;
            IPatternDetails scaled;
            long expectedParallel;
            try {
                before = captureControllers(level);
                long available = availableCapacity(before);
                if (available <= 0L) return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
                if (copies > available) return ECOBatchAdmission.rejected(StopReason.CAPACITY);
                long absoluteMultiplier = Math.multiplyExact(multiplier, copies);
                expectedParallel = Math.multiplyExact(absoluteMultiplier, recipeCrafts);
                scaled = (IPatternDetails) access.scaled.invoke(pattern, absoluteMultiplier);
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
            }
            var borrowed = new KeyCounter[totalInputs.length];
            for (int i = 0; i < borrowed.length; i++) {
                borrowed[i] = new KeyCounter();
                borrowed[i].addAll(totalInputs[i]);
            }
            // The native call owns the complete packet on true. It restores the complete holders on false.
            boolean accepted = provider.pushPattern(scaled, totalInputs);
            if (!accepted) {
                for (int i = 0; i < borrowed.length; i++) {
                    if (!same(borrowed[i], totalInputs[i])) return ECOBatchAdmission.indeterminate();
                }
                // MMCR's boolean does not distinguish a condition/resource refusal from saturation.
                return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
            }
            for (var inputs : totalInputs) if (!inputs.isEmpty()) return ECOBatchAdmission.indeterminate();
            try {
                var after = captureControllers(level);
                var started = new ArrayList<Target>();
                for (var entry : after.entrySet()) {
                    if (newStart(before.get(entry.getKey()), entry.getValue(), expectedParallel)) started.add(target(entry.getKey()));
                }
                startedTargets = List.copyOf(started);
                boolean complete = started.size() == 1;
                // A paid/paused callback failure is ownership, not a capacity proof. It cannot authorize a refund/retry.
                return ECOBatchAdmission.accepted(copies, complete ? copies : 0L,
                        complete && availableCapacity(after) > 0L ? StopReason.NONE : StopReason.TARGET_UNAVAILABLE);
            } catch (RuntimeException unobservable) {
                return ECOBatchAdmission.accepted(copies, 0L, StopReason.TARGET_UNAVAILABLE);
            }
        }

        private Map<BlockEntity, Object> captureControllers(ServerLevel level) {
            var snapshots = new LinkedHashMap<BlockEntity, Object>();
            for (Target target : linkedTargets(host)) {
                if (!level.hasChunkAt(target.position())) continue;
                var controller = level.getBlockEntity(target.position());
                if (!ready(controller) || !poolId.equals(field(controller, "selectedRecipePoolId"))
                        || !poolId.equals(field(controller, "validatedRecipePoolId"))) continue;
                Object snapshot = call(controller, "currentRuntimeSnapshot");
                Object ports = call(snapshot, "linkedPortPositions");
                if (ports instanceof java.util.Set<?> links && links.contains(host.getBlockPos())) snapshots.put(controller, snapshot);
            }
            return snapshots;
        }

        private long availableCapacity(Map<BlockEntity, Object> snapshots) {
            Object recipe, original;
            try {
                recipe = access.recipe.invoke(null, recipeId);
                original = access.original.invoke(pattern);
            } catch (ReflectiveOperationException incompatible) { return 0L; }
            if (recipe == null) return 0L;
            long maximum = 0L;
            for (var entry : snapshots.entrySet()) {
                Object snapshot = entry.getValue(), structure = call(snapshot, "structure");
                if (!Boolean.TRUE.equals(call(structure, "formed"))
                        || !Boolean.TRUE.equals(call(structure, "structureAreaLoaded"))
                        || Boolean.TRUE.equals(call(entry.getKey(), "isRedstonePaused"))) continue;
                Object effective, profile;
                try {
                    if (!Boolean.TRUE.equals(access.moduleGate.invoke(call(snapshot, "moduleConnectionStatus"),
                            call(recipe, "requiredHostIds"))) || access.capturedFailure.invoke(null, snapshot, recipe) != null) continue;
                    effective = access.resolve.invoke(access.resolver, recipe, snapshot,
                            call(call(entry.getKey(), "componentRuntime"), "modifierList"));
                    profile = access.inspectRecipe.invoke(null, recipe, effective, original);
                } catch (ReflectiveOperationException incompatible) { continue; }
                Object binding = call(profile, "binding");
                if (!poolId.equals(call(binding, "recipePoolId")) || !recipeId.equals(call(binding, "recipeId"))
                        || recipeCrafts != count(call(binding, "recipeCraftsPerPattern"))) continue;
                long capacity = "PROVEN_LINEAR".equals(String.valueOf(call(profile, "safety")))
                        ? count(call(profile, "maxMultiplier")) / multiplier : 1L / multiplier;
                if (capacity <= 0L) continue;
                if (Boolean.TRUE.equals(call(snapshot, "factoryControllerPresent"))) {
                    Object factory = call(snapshot, "factory");
                    int activeForRecipe = 0;
                    Object lanes = call(factory, "presentationLanes");
                    if (lanes instanceof Iterable<?> values) for (Object lane : values) {
                        if (Boolean.TRUE.equals(call(lane, "active"))
                                && recipeId.toString().equals(String.valueOf(call(lane, "recipeId")))) activeForRecipe++;
                    }
                    Object nativeFactory = field(field(entry.getKey(), "runtime"), "factoryRuntime");
                    Object reserved = field(nativeFactory, "patternRecipeCounts");
                    if (reserved instanceof Map<?, ?> counts) activeForRecipe += integer(counts.get(recipeId));
                    Object preparing = field(nativeFactory, "patternPreparations");
                    if (preparing instanceof Map<?, ?> preparations) for (Object value : preparations.values()) {
                        if (recipeId.equals(value)) activeForRecipe++;
                    }
                    int laneLimit = Math.min(integer(call(factory, "laneLimit")), integer(call(effective, "factoryThreadLimit")));
                    if (!Boolean.TRUE.equals(call(factory, "paused"))
                            && integer(call(factory, "activeLaneCount")) < laneLimit
                            && activeForRecipe < integer(call(effective, "recipeThreadLimit"))
                            && factorySlotAvailable(entry.getKey(), recipe, laneLimit)) maximum = Math.max(maximum, capacity);
                } else if (call(call(snapshot, "crafting"), "recipeId") == null
                        && !Boolean.TRUE.equals(call(field(entry.getKey(), "normalRecipeThread"), "isStartPending"))
                        && !Boolean.TRUE.equals(call(field(entry.getKey(), "normalRecipeThread"), "hasPendingAsyncSearch"))) {
                    maximum = Math.max(maximum, capacity);
                }
            }
            return Math.min(maximum, maxBatchSize());
        }

        private boolean factorySlotAvailable(BlockEntity controller, Object recipe, int laneLimit) {
            Object factory = field(field(controller, "runtime"), "factoryRuntime");
            Object value = field(factory, "lanes");
            if (!(value instanceof List<?> lanes)) return false;
            Object reserved = field(factory, "patternStartReservations");
            Object preparing = field(factory, "patternPreparations");
            for (int i = 0; i < Math.min(laneLimit, lanes.size()); i++) {
                Object lane = lanes.get(i);
                if (!Boolean.TRUE.equals(call(lane, "isIdle"))
                        || reserved instanceof java.util.Set<?> reservations && reservations.contains(lane)
                        || preparing instanceof Map<?, ?> preparations && preparations.containsKey(lane)) continue;
                if (!Boolean.TRUE.equals(call(lane, "isCoreThread"))) return true;
                Object recipes = call(lane, "recipeSet");
                if (recipes instanceof Iterable<?> allowed) for (Object candidate : allowed) {
                    if (recipeId.equals(call(candidate, "id")) && poolId.equals(call(candidate, "recipePoolId"))) return true;
                }
            }
            // MMCR may create a simple lane inside the existing factory limit during the actual start.
            return lanes.size() < laneLimit;
        }

        private boolean newStart(Object before, Object after, long parallelism) {
            if (before == null || after == null) return false;
            if (Boolean.TRUE.equals(call(after, "factoryControllerPresent"))) {
                var activeBefore = new java.util.HashSet<String>();
                Object old = call(call(before, "factory"), "presentationLanes");
                if (old instanceof Iterable<?> lanes) for (Object lane : lanes) {
                    if (Boolean.TRUE.equals(call(lane, "active"))) activeBefore.add(String.valueOf(call(lane, "laneId")));
                }
                Object next = call(call(after, "factory"), "presentationLanes");
                if (next instanceof Iterable<?> lanes) for (Object lane : lanes) {
                    if (Boolean.TRUE.equals(call(lane, "active")) && call(lane, "failure") == null
                            && !activeBefore.contains(String.valueOf(call(lane, "laneId")))
                            && recipeId.toString().equals(String.valueOf(call(lane, "recipeId")))
                            && count(call(lane, "parallelism")) == parallelism) return true;
                }
                return false;
            }
            Object previous = call(before, "crafting"), current = call(after, "crafting");
            return call(previous, "recipeId") == null && recipeId.equals(call(current, "recipeId"))
                    && "CRAFTING".equals(String.valueOf(call(call(current, "status"), "getStatus")))
                    && call(current, "failure") == null && count(call(current, "parallelism")) == parallelism;
        }
    }

    private static boolean ready(@Nullable BlockEntity controller) {
        // runtimeRestorationComplete() itself loads NBT, so do not invoke it from admission or display probes.
        return controller != null && is(controller, CONTROLLER) && !controller.isRemoved()
                && Boolean.TRUE.equals(field(controller, "runtimeTopologyReady"))
                && Boolean.FALSE.equals(field(controller, "loadingRuntime"))
                && field(controller, "pendingFactoryRuntimeInput") == null
                && field(controller, "pendingCraftingRuntimeInput") == null
                && field(controller, "pendingNormalPatternMemory") == null;
    }

    private static boolean same(KeyCounter expected, KeyCounter actual) {
        if (actual == null) return false;
        for (var entry : expected) if (actual.get(entry.getKey()) != entry.getLongValue()) return false;
        for (var entry : actual) if (expected.get(entry.getKey()) != entry.getLongValue()) return false;
        return true;
    }
}
