package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.config.NEConfig;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-thread, budgeted pre-validation for deterministic molecular-assembler patterns. */
public final class ECOFastPathWarmupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int MAX_TASKS_PER_TICK = 8;
    private static final long MAX_NANOS_PER_TICK = 750_000L;
    private static final Map<MinecraftServer, ServerState> STATES = new WeakHashMap<>();

    private ECOFastPathWarmupService() {
    }

    public static void enqueue(ECOCraftingPatternBusBlockEntity patternBus) {
        if (!(patternBus.getLevel() instanceof ServerLevel level)
            || level.isClientSide
            || !NEConfig.ecoAe2FastPathEnabled) {
            return;
        }

        ECOCraftingFastPathCache cache = patternBus.getFastPathCache();
        if (cache == null) {
            return;
        }

        MinecraftServer server = level.getServer();
        ServerState state = STATES.computeIfAbsent(server, ignored -> new ServerState());
        long generation = AE2PatternIntrospection.getReloadGeneration();
        int patternRevision = patternBus.getPatternContentRevision();
        for (IPatternDetails details : patternBus.getLocalAvailablePatterns()) {
            Optional<ECOExtractedPatternExecution> candidate = buildCandidate(details, level);
            if (candidate.isEmpty()) {
                continue;
            }

            ECOExtractedPatternExecution execution = candidate.get();
            ECOFastPathKey key = execution.key();
            if (key == null) {
                continue;
            }

            // A pattern bus update is a validation boundary. Replacing the entry makes an old
            // positive or negative result unable to survive a same-definition pattern mutation.
            cache.invalidate(key);
            WarmupTask task = new WarmupTask(
                level,
                patternBus,
                patternRevision,
                generation,
                cache,
                execution,
                key,
                patternBus.getBlockPos()
            );
            PendingKey pendingKey = new PendingKey(cache, key);
            state.pending.put(pendingKey, task);
            state.queue.add(task);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.hasTime() || !NEConfig.ecoAe2FastPathEnabled) {
            return;
        }
        ServerState state = STATES.get(event.getServer());
        if (state == null || state.queue.isEmpty()) {
            return;
        }

        long startNanos = System.nanoTime();
        int processed = 0;
        while (processed < MAX_TASKS_PER_TICK
            && System.nanoTime() - startNanos < MAX_NANOS_PER_TICK) {
            WarmupTask task = state.queue.poll();
            if (task == null) {
                break;
            }
            PendingKey pendingKey = new PendingKey(task.cache(), task.key());
            if (state.pending.get(pendingKey) != task) {
                state.staleTasks++;
                continue;
            }
            state.pending.remove(pendingKey);
            processed++;

            if (task.generation() != AE2PatternIntrospection.getReloadGeneration()
                || task.patternBus().isRemoved()
                || task.patternBus().getPatternContentRevision() != task.patternRevision()) {
                state.staleTasks++;
                continue;
            }
            if (task.cache().peek(task.key()) != null) {
                state.alreadyCached++;
                continue;
            }

            try {
                Optional<ECOFastPathValidator.CraftingOutcome> crafted =
                    ECOFastPathValidator.craft(task.execution(), task.level());
                if (crafted.isEmpty()) {
                    // Keep this unknown. The normal request path will retain its existing behavior
                    // if a recipe cannot assemble from the representative input.
                    state.craftSkipped++;
                    continue;
                }

                ECOFastPathValidator.ValidationResult validation =
                    ECOFastPathValidator.validate(task.execution(), crafted.get());
                if (!validation.applicable()) {
                    state.notApplicable++;
                    continue;
                }
                if (validation.accepted()) {
                    if (task.cache().putPositive(
                            task.key(), validation.outputs(), validation.remaining(), validation.inputs(),
                            currentTick(task.level()))) {
                        state.positive++;
                    } else {
                        state.negative++;
                    }
                    continue;
                }

                ECOFastPathFallbackReason reason = validation.rejectionReason();
                if (isDeterministicFailure(reason)) {
                    task.cache().putNegative(task.key(), reason, currentTick(task.level()));
                    state.negative++;
                    ECOFastPathDiagnostics.logFailure(
                        task.execution(),
                        reason,
                        ECOFastPathStage.CACHE_VERIFY,
                        task.ownerPos(),
                        currentTick(task.level()),
                        "warmup_validation_rejected"
                    );
                } else {
                    state.craftSkipped++;
                }
            } catch (RuntimeException | LinkageError failure) {
                state.exceptions++;
                LOGGER.debug(
                    "ECO FastPath warmup skipped pattern at {} after an exception",
                    task.ownerPos(),
                    failure
                );
            }
        }

        state.maybeLog(event.getServer().getTickCount(), startNanos);
    }

    public static void onRecipeReloadOrServerReload() {
        for (ServerState state : STATES.values()) {
            state.clearQueue();
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        STATES.remove(server);
    }

    private static long currentTick(ServerLevel level) {
        return appeng.hooks.ticking.TickHandler.instance().getCurrentTick();
    }

    private static boolean isDeterministicFailure(ECOFastPathFallbackReason reason) {
        return reason == ECOFastPathFallbackReason.OUTPUT_MISMATCH
            || reason == ECOFastPathFallbackReason.CONTAINER_MISMATCH
            || reason == ECOFastPathFallbackReason.INPUT_MISMATCH
            || reason == ECOFastPathFallbackReason.CACHE_VALIDATION_REJECTED;
    }

    private static Optional<ECOExtractedPatternExecution> buildCandidate(
        IPatternDetails details,
        ServerLevel level
    ) {
        if (!AE2PatternIntrospection.isDeterministicWarmupPattern(details)) {
            return Optional.empty();
        }

        try {
            IPatternDetails.IInput[] patternInputs = details.getInputs();
            if (patternInputs == null) {
                return Optional.empty();
            }

            KeyCounter[] inputHolder = new KeyCounter[patternInputs.length];
            KeyCounter expectedOutputs = new KeyCounter();
            KeyCounter expectedContainerItems = new KeyCounter();
            for (int index = 0; index < patternInputs.length; index++) {
                IPatternDetails.IInput input = patternInputs[index];
                if (input == null || input.getMultiplier() <= 0L) {
                    return Optional.empty();
                }
                GenericStack[] possibleInputs = input.getPossibleInputs();
                // A representative input is only safe when the pattern has no substitution choice.
                if (possibleInputs == null || possibleInputs.length != 1 || possibleInputs[0] == null) {
                    return Optional.empty();
                }
                GenericStack possible = possibleInputs[0];
                if (possible.what() == null
                    || possible.amount() <= 0L
                    || !input.isValid(possible.what(), level)) {
                    return Optional.empty();
                }

                long amount = Math.multiplyExact(possible.amount(), input.getMultiplier());
                KeyCounter slot = inputHolder[index] = new KeyCounter();
                slot.add(possible.what(), amount);
                var remainingKey = input.getRemainingKey(possible.what());
                if (remainingKey != null) {
                    expectedContainerItems.add(remainingKey, input.getMultiplier());
                }
            }

            for (GenericStack output : details.getOutputs()) {
                if (output == null || output.what() == null || output.amount() <= 0L) {
                    return Optional.empty();
                }
                expectedOutputs.add(output.what(), output.amount());
            }

            ECOExtractedPatternExecution execution = ECOExtractedPatternExecution.create(
                details,
                inputHolder,
                expectedOutputs,
                expectedContainerItems,
                level,
                true
            );
            return execution.fastPathEligible() && execution.key() != null
                ? Optional.of(execution)
                : Optional.empty();
        } catch (RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static final class ServerState {
        private final ArrayDeque<WarmupTask> queue = new ArrayDeque<>();
        private final Map<PendingKey, WarmupTask> pending = new HashMap<>();
        private long positive;
        private long negative;
        private long staleTasks;
        private long alreadyCached;
        private long craftSkipped;
        private long notApplicable;
        private long exceptions;
        private long lastLogTick = Long.MIN_VALUE;

        private void clearQueue() {
            queue.clear();
            pending.clear();
        }

        private void maybeLog(long tick, long startNanos) {
            if (!NEConfig.debugEcoFastPath || lastLogTick != Long.MIN_VALUE && tick - lastLogTick < 100L) {
                return;
            }
            lastLogTick = tick;
            LOGGER.debug(
                "ECO FastPath warmup: queue={} positive={} negative={} stale={} alreadyCached={} "
                    + "craftSkipped={} notApplicable={} exceptions={} elapsedNanos={}",
                queue.size(),
                positive,
                negative,
                staleTasks,
                alreadyCached,
                craftSkipped,
                notApplicable,
                exceptions,
                System.nanoTime() - startNanos
            );
        }
    }

    private record PendingKey(ECOCraftingFastPathCache cache, ECOFastPathKey key) {
    }

    private record WarmupTask(
        ServerLevel level,
        ECOCraftingPatternBusBlockEntity patternBus,
        int patternRevision,
        long generation,
        ECOCraftingFastPathCache cache,
        ECOExtractedPatternExecution execution,
        ECOFastPathKey key,
        BlockPos ownerPos
    ) {
    }
}
