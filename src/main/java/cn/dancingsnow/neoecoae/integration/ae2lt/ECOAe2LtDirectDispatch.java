package cn.dancingsnow.neoecoae.integration.ae2lt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission.StopReason;
import cn.dancingsnow.neoecoae.integration.common.processing.ProcessingReflection;
import cn.dancingsnow.neoecoae.integration.ae2.ECOUnboundedInputCapacity;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** ECO-only transport adapter. Never calls getBatchCapacity, pushBatch or either AE2LT ramp. */
public final class ECOAe2LtDirectDispatch {
    private static final String LOGIC = "com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic";
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) {
            try { return new Access(type); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                throw incompatible(type, "shared", failure);
            }
        }
    };
    private static final ClassValue<NormalAccess> NORMAL = new ClassValue<>() {
        @Override protected NormalAccess computeValue(Class<?> type) {
            try { return new NormalAccess(type); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                throw incompatible(type, "normal", failure);
            }
        }
    };
    private static final ClassValue<WirelessAccess> WIRELESS = new ClassValue<>() {
        @Override protected WirelessAccess computeValue(Class<?> type) {
            try { return new WirelessAccess(type, ACCESS.get(type)); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                throw incompatible(type, "wireless", failure);
            }
        }
    };
    private ECOAe2LtDirectDispatch() {}

    @Nullable public static Session open(Object provider) {
        Object logic = logic(provider);
        if (logic == null) return null;
        Class<?> type = logic.getClass();
        var access = ACCESS.get(type);
        String mode;
        try {
            mode = ((Enum<?>) access.mode.invoke(access.host.get(logic))).name();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw incompatible(type, "provider mode", failure);
        }
        TransportAccess transport = switch (mode) {
            case "NORMAL" -> NORMAL.get(type);
            case "WIRELESS" -> WIRELESS.get(type);
            default -> throw new IllegalStateException("Unsupported AE2LT provider mode " + mode);
        };
        return new Session(logic, access, transport);
    }

    private static IllegalStateException incompatible(Class<?> type, String contract, Throwable failure) {
        return new IllegalStateException("Incompatible AE2LT " + contract + " dispatch contract on " + type.getName(), failure);
    }

    /** Only the complete native transport can preserve same-pattern blocking for an ECO batch. */
    public static boolean canBatchWhileBlocking(Object provider) {
        var session = open(provider);
        return session != null && session.canBatchWhileBlocking();
    }
    @Nullable private static Object logic(Object provider) {
        return logic(provider, new ReferenceOpenHashSet<>());
    }
    @Nullable private static Object logic(Object provider, ReferenceSet<Object> visited) {
        if (provider == null) return null;
        if (!visited.add(provider)) return null;
        for (Class<?> type = provider.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(LOGIC)) return provider;
        }
        try {
            Method accessor = ProcessingReflection.publicNoArgMethod(provider.getClass(), "getLogic");
            if (accessor == null) return null;
            Object value = accessor.invoke(provider);
            return logic(value, visited);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    public static final class Session {
        private final Object logic;
        private final Access a;
        private final TransportAccess transport;
        private Session(Object logic, Access a, TransportAccess transport) {
            this.logic = logic;
            this.a = a;
            this.transport = transport;
        }

        public boolean canBatchWhileBlocking() {
            try {
                if (!((PatternProviderLogic) logic).isBlocking()) return false;
                Object mode = a.blockingMode.invoke(a.host.get(logic));
                return mode instanceof Enum<?> value && value.name().equals("SAME_PATTERN");
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return false;
            }
        }

        private boolean allowsBatch() {
            try {
                return !((PatternProviderLogic) logic).isBlocking() || canBatchWhileBlocking();
            } catch (RuntimeException unavailable) {
                return false;
            }
        }

        public long maxBatchSize(IPatternDetails pattern) {
            // Each routed copy is a separate native transaction and must consume an ECO attempt.
            return directional(pattern) ? 1L : Integer.MAX_VALUE;
        }

        public boolean hasUnboundedInputCapacity(IPatternDetails pattern, KeyCounter[] inputs) {
            if (directional(pattern) || !allowsBatch()) return false;
            try {
                Object host = a.host.get(logic);
                if (!(a.level.invoke(host) instanceof ServerLevel level)) return false;
                if (transport instanceof WirelessAccess wireless) {
                    for (Object address : (List<?>) wireless.connections.invoke(logic, level, level.getGameTime())) {
                        Target target = wirelessTarget(wireless, level, address, pattern);
                        if (target != null && unbounded(target, pattern, inputs)) return true;
                    }
                } else if (transport instanceof NormalAccess normal) {
                    for (Object direction : (List<?>) normal.directions.invoke(logic)) {
                        Target target = normalTarget(normal, level, direction, pattern);
                        if (target != null && unbounded(target, pattern, inputs)) return true;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException unavailable) { /* Keep ordinary learning. */ }
            return false;
        }

        private boolean unbounded(Target target, IPatternDetails pattern, KeyCounter[] inputs)
                throws ReflectiveOperationException {
            if (!(boolean) a.supportsBatch.invoke(target.address, target.level, pattern)) return false;
            return ECOUnboundedInputCapacity.forTarget(target.level,
                    (net.minecraft.core.BlockPos) a.pos.invoke(target.address),
                    (Direction) a.face.invoke(target.address), inputs);
        }

        public ECOBatchAdmission submit(IPatternDetails pattern, KeyCounter[] prototype, long copies,
                long finiteTargetLimit) {
            if (copies <= 0) return ECOBatchAdmission.rejected(StopReason.TRANSPORT_LIMIT);
            if (!allowsBatch()) return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
            try {
                // Flush old custody before accepting anything new, just like the native entry point.
                a.flush.invoke(logic);
                a.flushLocal.invoke(logic);
                var node = (IManagedGridNode) a.node.get(logic);
                if (!node.isActive() || !a.lock.invoke(logic).toString().equals("NONE")) {
                    return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
                }
                if ((boolean) a.localOverflow.invoke(logic) || !((List<?>) a.sendList.invoke(logic)).isEmpty()) {
                    return ECOBatchAdmission.rejected(StopReason.BUFFERED);
                }
                Object canonical = a.resolve.invoke(a.catalog.get(logic), pattern);
                if (canonical == null) return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
                Object host = a.host.get(logic);
                if (!(a.level.invoke(host) instanceof ServerLevel level)) {
                    return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
                }
                Object overflow = a.overflow.get(logic);
                a.refresh.invoke(overflow);
                if (transport instanceof WirelessAccess wireless && (boolean) wireless.backpressure.invoke(overflow)) {
                    return ECOBatchAdmission.rejected(StopReason.BUFFERED);
                }
                // Directional inventory transactions have per-face overflow ownership. Reuse the
                // existing one-copy transaction rather than multiplying its routed buffers.
                if (directional(pattern)) {
                    long accepted = 0;
                    long inserted = 0;
                    StopReason reason = StopReason.NONE;
                    while (accepted < copies) {
                        if (!allowsBatch()) { reason = StopReason.TARGET_UNAVAILABLE; break; }
                        if (!((ICraftingProvider) logic).pushPattern(pattern, copy(prototype))) {
                            reason = StopReason.CAPACITY;
                            break;
                        }
                        accepted++;
                        if (!drained()) { reason = StopReason.BUFFERED; break; }
                        inserted++;
                    }
                    if (accepted > 0) a.save.invoke(logic);
                    return receipt(accepted, inserted, reason);
                }
                if (transport instanceof NormalAccess normal) {
                    List<?> targets = (List<?>) normal.directions.invoke(logic);
                    double cost = ((Number) a.cost.invoke(null, (Object) prototype)).doubleValue();
                    if (targets.isEmpty()) return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
                    var resolved = new ArrayList<Target>();
                    for (Object direction : (List<?>) normal.order.invoke(normal.dispatch.get(logic), targets)) {
                        Target target = normalTarget(normal, level, direction, pattern);
                        if (target != null) resolved.add(target);
                    }
                    return submitDistributed(pattern, canonical, prototype, copies, finiteTargetLimit, cost, level,
                            resolved, null, null);
                }
                var wireless = (WirelessAccess) transport;
                List<?> targets = (List<?>) wireless.connections.invoke(logic, level, level.getGameTime());
                double cost = ((Number) a.cost.invoke(null, (Object) prototype)).doubleValue();
                if (targets.isEmpty()) return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
                Object scheduler = wireless.dispatch.get(logic);
                boolean fast = wireless.speed.invoke(host).toString().equals("FAST");
                Object mode = wireless.dispatchMode.invoke(host);
                wireless.prepare.invoke(scheduler, targets, level.getGameTime(), fast);
                if (mode.toString().equals("SINGLE_TARGET")) {
                    return submitSingleTarget(wireless, pattern, canonical, prototype, copies, finiteTargetLimit, cost, level,
                            scheduler, mode, fast, targets.size());
                }
                // Reuse provider-owned, pattern-scoped fairness across CPU visits. ECO still owns
                // batch growth; only physical target selection and accepted-copy counts are shared.
                Object pass = wireless.fairPass.invoke(scheduler, canonical, level.getGameTime());
                try {
                    return submitDistributed(pattern, canonical, prototype, copies, finiteTargetLimit, cost, level,
                            List.of(), wireless, pass);
                } finally {
                    wireless.closePass.invoke(pass);
                }
            } catch (ReflectiveOperationException failure) {
                // A callback/save can fail after insertion; never refund or replay unknown custody.
                throw new IllegalStateException("AE2LT direct batch custody is unknown", failure);
            }
        }

        private ECOBatchAdmission submitDistributed(IPatternDetails pattern, Object canonical, KeyCounter[] prototype,
                long copies, long finiteTargetLimit, double cost, ServerLevel level, List<Target> targets,
                @Nullable WirelessAccess wireless, @Nullable Object pass)
                throws ReflectiveOperationException {
            int targetCount = wireless == null ? targets.size() : ((Number) wireless.fairSize.invoke(pass)).intValue();
            if (targetCount == 0) return ECOBatchAdmission.rejected(
                    wireless == null ? StopReason.TARGET_UNAVAILABLE : StopReason.FAIRNESS);
            long accepted = 0;
            long inserted = 0;
            StopReason reason = StopReason.NONE;
            for (int i = 0; i < targetCount && accepted < copies; i++) {
                Target target;
                Object connection = null;
                long remaining = copies - accepted;
                long share = 1L + (remaining - 1L) / (targetCount - i);
                if (wireless == null) {
                    target = targets.get(i);
                } else {
                    connection = wireless.fairPoll.invoke(pass);
                    if (connection == null) { reason = StopReason.FAIRNESS; break; }
                    target = wirelessTarget(wireless, level, connection, pattern);
                    if (target == null) {
                        if ((boolean) wireless.contains.invoke(a.overflow.get(logic), connection)) {
                            wireless.pauseTarget.invoke(wireless.dispatch.get(logic), connection);
                        } else {
                            wireless.fairCooldown.invoke(pass, connection, level.getGameTime() + 1L);
                        }
                        reason = StopReason.TARGET_UNAVAILABLE;
                        continue;
                    }
                    // Let under-served machines catch up, while splitting a fresh batch among
                    // all ready targets instead of handing the complete offer to its first target.
                    share = Math.min(remaining, Math.max(share,
                            ((Number) wireless.fairAllowance.invoke(pass, connection)).longValue()));
                    wireless.fairRaise.invoke(pass, connection, share);
                }
                Chunk chunk = pushChunk(target, pattern, canonical, prototype, share, finiteTargetLimit, cost);
                if (!chunk.valid()) return ECOBatchAdmission.indeterminate();
                accepted += chunk.accepted;
                if (chunk.inserted) inserted += chunk.accepted;
                if (wireless != null) {
                    if (chunk.accepted > 0) wireless.fairSuccess.invoke(pass, connection, chunk.accepted);
                    else if (chunk.reason != StopReason.RESOURCES) {
                        wireless.fairCooldown.invoke(pass, connection, level.getGameTime() + 1L);
                    }
                    // Native flushing resumes this target after its owned bucket drains.
                    if ((boolean) wireless.contains.invoke(a.overflow.get(logic), connection)) {
                        wireless.pauseTarget.invoke(wireless.dispatch.get(logic), connection);
                    }
                }
                if (chunk.reason == StopReason.RESOURCES) { reason = StopReason.RESOURCES; break; }
                // In particular, a local send list belongs to the last target's send direction.
                // Never switch that direction while the provider still owns buffered inputs.
                // Wireless buckets belong to their own targets. An older bucket must not
                // stop the other targets from completing this request's equal shares.
                if (chunk.reason == StopReason.BUFFERED || !localDrained()) { reason = StopReason.BUFFERED; break; }
                if (chunk.reason == StopReason.TARGET_UNAVAILABLE) reason = StopReason.TARGET_UNAVAILABLE;
                else if (chunk.reason == StopReason.CAPACITY && reason != StopReason.TARGET_UNAVAILABLE) {
                    reason = StopReason.CAPACITY;
                } else if (chunk.reason == StopReason.TRANSPORT_LIMIT && reason == StopReason.NONE) {
                    reason = StopReason.TRANSPORT_LIMIT;
                }
            }
            // A failed target cannot veto a complete physical proof from the other targets.
            // Old buffers can still pause this visit without erasing that proof.
            if (accepted == copies && inserted == copies && reason != StopReason.BUFFERED
                    && reason != StopReason.RESOURCES) reason = drained() ? StopReason.NONE : StopReason.BUFFERED;
            return receipt(accepted, inserted, reason);
        }

        private ECOBatchAdmission submitSingleTarget(WirelessAccess wireless, IPatternDetails pattern, Object canonical, KeyCounter[] prototype,
                long copies, long finiteTargetLimit, double cost, ServerLevel level, Object scheduler, Object mode, boolean fast, int targets)
                throws ReflectiveOperationException {
            var receipt = new Chunk[1];
            Class<?> callback = wireless.single.getParameterTypes()[5];
            Object attempt = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback},
                    (proxy, method, arguments) -> {
                        Target target = wirelessTarget(wireless, level, arguments[0], pattern);
                        if (target == null) {
                            receipt[0] = new Chunk(0, 0, false, StopReason.TARGET_UNAVAILABLE);
                            return wireless.hardFail;
                        }
                        Chunk chunk = pushChunk(target, pattern, canonical, prototype, copies, finiteTargetLimit, cost);
                        receipt[0] = chunk;
                        if (!chunk.valid()) return wireless.globalAbort;
                        return chunk.accepted > 0 ? wireless.success
                                : chunk.reason == StopReason.RESOURCES ? wireless.globalAbort : wireless.softFail;
                    });
            java.util.function.Predicate<Object> alive = connection -> {
                try {
                    var targetLevel = level.getServer().getLevel(
                            (net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>) wireless.dimension.invoke(connection));
                    return targetLevel != null && (boolean) wireless.alive.invoke(connection, targetLevel);
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            };
            java.util.function.Consumer<Object> removed = connection -> {
                try { wireless.connectionsDirty.setBoolean(logic, true); }
                catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
            };
            wireless.single.invoke(scheduler, mode, canonical, level.getGameTime(), fast, targets, attempt, alive, removed);
            Chunk chunk = receipt[0];
            if (chunk == null) return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
            if (!chunk.valid()) return ECOBatchAdmission.indeterminate();
            StopReason reason = chunk.reason;
            if (chunk.accepted > 0 && !drained()) reason = StopReason.BUFFERED;
            return receipt(chunk.accepted, chunk.inserted ? chunk.accepted : 0L, reason);
        }

        @Nullable private Target normalTarget(NormalAccess normal, ServerLevel level, Object direction, IPatternDetails pattern)
                throws ReflectiveOperationException {
            Object context = normal.target.invoke(logic, level, direction, pattern);
            return context == null ? null : new Target(level, a.contextTarget.invoke(context), context);
        }

        @Nullable private Target wirelessTarget(WirelessAccess wireless, ServerLevel level, Object connection, IPatternDetails pattern)
                throws ReflectiveOperationException {
            if ((boolean) wireless.contains.invoke(a.overflow.get(logic), connection)) return null;
            var targetLevel = level.getServer().getLevel(
                    (net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>) wireless.dimension.invoke(connection));
            if (targetLevel == null || !targetLevel.isLoaded((net.minecraft.core.BlockPos) a.pos.invoke(connection))
                    || !(boolean) wireless.canAccept.invoke(connection, targetLevel, pattern, wireless.source.get(logic))) return null;
            return new Target(targetLevel, connection, wireless.context.newInstance(targetLevel, connection));
        }

        private Chunk pushChunk(Target target, IPatternDetails pattern, Object canonical, KeyCounter[] prototype,
                long copies, long finiteTargetLimit, double cost) throws ReflectiveOperationException {
            int count = (int) Math.min(Integer.MAX_VALUE, copies);
            count = (int) Math.min(count,
                    ((Number) a.batchLimit.invoke(target.address, target.level)).longValue());
            if (!allowsBatch() || (boolean) a.blocked.invoke(logic, target.context, canonical)) {
                return new Chunk(count, 0, false, StopReason.TARGET_UNAVAILABLE);
            }
            a.before.invoke(a.autoReturn.get(logic), target.level, target.address);
            // A machine lacking a counted transport can only own one physical transaction.
            if (!(boolean) a.supportsBatch.invoke(target.address, target.level, pattern)) count = 1;
            else if (!unbounded(target, pattern, prototype)) count = (int) Math.min(count, finiteTargetLimit);
            a.setBypass.invoke(null, true);
            try {
                Object receipt = a.chunk.invoke(logic, target.context, pattern, canonical,
                        copy(prototype), count, cost, a.complete);
                long accepted = ((Number) a.owned.invoke(receipt)).longValue();
                boolean inserted = (boolean) a.inserted.invoke(receipt);
                StopReason reason = (boolean) a.abort.invoke(receipt) ? StopReason.RESOURCES
                        : accepted < count ? StopReason.CAPACITY
                        : !inserted ? StopReason.BUFFERED
                        : count < copies ? StopReason.TRANSPORT_LIMIT : StopReason.NONE;
                var chunk = new Chunk(count, accepted, inserted, reason);
                if (chunk.valid() && accepted > 0) {
                    if (transport instanceof NormalAccess normal) {
                        normal.sendDirection.invoke(logic, ((Direction) a.face.invoke(target.address)).getOpposite());
                        normal.send.invoke(logic);
                    }
                    a.wake.invoke(logic);
                    a.save.invoke(logic);
                }
                return chunk;
            } finally {
                a.setBypass.invoke(null, false);
            }
        }

        private record Target(ServerLevel level, Object address, Object context) {}
        private record Chunk(int offered, long accepted, boolean inserted, StopReason reason) {
            boolean valid() { return accepted >= 0 && accepted <= offered; }
        }

        private static ECOBatchAdmission receipt(long accepted, long inserted, StopReason reason) {
            return accepted == 0 ? ECOBatchAdmission.rejected(reason)
                    : ECOBatchAdmission.accepted(accepted, inserted, reason);
        }

        private boolean drained() throws ReflectiveOperationException {
            return localDrained() && (boolean) a.empty.invoke(a.overflow.get(logic));
        }

        private boolean localDrained() throws ReflectiveOperationException {
            return ((List<?>) a.sendList.invoke(logic)).isEmpty()
                    && !(boolean) a.localOverflow.invoke(logic);
        }
    }

    private static boolean directional(IPatternDetails pattern) {
        pattern = cn.dancingsnow.neoecoae.integration.ae2.ECOProviderPatternIntrospection.unwrap(pattern);
        if (pattern == null) return false;
        try {
            Method accessor = ProcessingReflection.publicNoArgMethod(pattern.getClass(), "directionalInputsSet");
            return accessor != null && (boolean) accessor.invoke(pattern);
        }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static KeyCounter[] copy(KeyCounter[] inputs) {
        var result = new KeyCounter[inputs.length];
        for (int i = 0; i < inputs.length; i++) { result[i] = new KeyCounter(); result[i].addAll(inputs[i]); }
        return result;
    }

    private static final class Access {
        final Field node, host, catalog, overflow, autoReturn;
        final Object complete;
        final Method blockingMode, batchLimit, flush, flushLocal, localOverflow, sendList, lock, resolve, level, mode,
                refresh, cost, pos, contextTarget, blocked, before, supportsBatch, chunk, owned, abort, inserted,
                face, wake, save, empty, setBypass;

        Access(Class<?> type) throws ReflectiveOperationException {
            node = field(type, "gridNode"); host = field(type, "overloadedHost");
            blockingMode = host.getType().getMethod("getBlockingMode");
            catalog = field(type, "patternCatalog"); overflow = field(type, "wirelessOverflow");
            autoReturn = field(type, "autoReturn");
            flush = method(type, "flushWirelessSends", 0); flushLocal = method(type, "flushLocalDirectionalOverflow", 0);
            localOverflow = method(type, "hasLocalDirectionalOverflow", 0);
            sendList = method(type, "getSendList", 0); lock = method(type, "getCraftingLockedReason", 0);
            resolve = method(catalog.getType(), "resolve", IPatternDetails.class);
            level = method(host.getType(), "getLevel", 0); mode = method(host.getType(), "getProviderMode", 0);
            refresh = method(overflow.getType(), "refreshBackpressure", 0);
            empty = method(overflow.getType(), "isEmpty", 0);
            chunk = method(type, "pushBatchChunk", 7);
            Class<?> contextType = chunk.getParameterTypes()[0];
            contextTarget = method(contextType, "target", 0);
            Class<?> target = contextTarget.getReturnType();
            pos = method(target, "pos", 0); face = method(target, "boundFace", 0);
            supportsBatch = method(target, "supportsBatch", 2);
            batchLimit = method(target, "batchCopyLimit", ServerLevel.class);
            blocked = method(type, "isBatchTargetBlocked", contextType, IPatternDetails.class);
            before = method(autoReturn.getType(), "beforeDispatch", 2);
            complete = enumValue(chunk.getParameterTypes()[6], "COMPLETE_BATCH");
            Class<?> receipt = chunk.getReturnType();
            owned = method(receipt, "ownedCopies", 0); abort = method(receipt, "globalAbort", 0);
            inserted = method(receipt, "fullyInserted", 0);
            wake = method(type, "alertGridTick", 0); save = method(type, "saveChanges", 0);
            Class<?> power = Class.forName("com.moakiee.ae2lt.logic.energy.PowerCostUtil", false, type.getClassLoader());
            cost = method(power, "totalCost", KeyCounter[].class);
            Class<?> registry = Class.forName("com.moakiee.ae2lt.logic.provider.EjectModeRegistry", false,
                    type.getClassLoader());
            setBypass = method(registry, "setBypass", boolean.class);
        }
    }

    private sealed interface TransportAccess permits NormalAccess, WirelessAccess {}

    private static final class NormalAccess implements TransportAccess {
        final Field dispatch;
        final Method directions, order, target, sendDirection, send;

        NormalAccess(Class<?> type) throws ReflectiveOperationException {
            dispatch = field(type, "normalDispatch");
            directions = method(type, "activeNormalTargetDirections", 0);
            order = method(dispatch.getType(), "dispatchOrder", List.class);
            target = method(type, "resolveNormalBatchTarget", ServerLevel.class, Direction.class, IPatternDetails.class);
            sendDirection = method(type, "setSendDirection", Direction.class);
            send = method(type, "invokeSendStacksOut", 0);
        }
    }

    private static final class WirelessAccess implements TransportAccess {
        final Field dispatch, source, connectionsDirty;
        final Constructor<?> context;
        final Object success, softFail, hardFail, globalAbort;
        final Method connections, backpressure, contains, dimension, canAccept, alive, dispatchMode, speed, prepare,
                fairPass, fairSize, fairPoll, fairAllowance, fairRaise, fairSuccess, fairCooldown, pauseTarget,
                closePass, single;

        WirelessAccess(Class<?> type, Access shared) throws ReflectiveOperationException {
            dispatch = field(type, "wirelessDispatch"); source = field(type, "wirelessSource");
            connectionsDirty = field(type, "connectionsDirty");
            Class<?> scheduler = dispatch.getType();
            dispatchMode = method(shared.host.getType(), "getWirelessDispatchMode", 0);
            speed = method(shared.host.getType(), "getWirelessSpeedMode", 0);
            prepare = method(scheduler, "prepare", List.class, long.class, boolean.class);
            fairPass = method(scheduler, "beginFairPass", IPatternDetails.class, long.class);
            Class<?> passType = fairPass.getReturnType();
            fairSize = method(passType, "activeTargetsAtStart", 0); fairPoll = method(passType, "poll", 0);
            fairAllowance = method(passType, "allowance", 1); fairRaise = method(passType, "raiseAllowance", 2);
            fairSuccess = method(passType, "success", 2); fairCooldown = method(passType, "cooldown", 2);
            closePass = method(passType, "close", 0);
            single = method(scheduler, "dispatchSingleCopy", 8);
            Class<?> outcome = method(single.getParameterTypes()[5], "push", 1).getReturnType();
            success = enumValue(outcome, "SUCCESS"); softFail = enumValue(outcome, "SOFT_FAIL");
            hardFail = enumValue(outcome, "HARD_FAIL"); globalAbort = enumValue(outcome, "GLOBAL_ABORT");
            backpressure = method(shared.overflow.getType(), "isBackpressured", 0);
            contains = method(shared.overflow.getType(), "contains", 1);
            Class<?> connection = contains.getParameterTypes()[0];
            pauseTarget = method(scheduler, "pauseTarget", connection);
            dimension = method(connection, "dimension", 0);
            canAccept = method(connection, "canAccept", 3);
            alive = method(connection, "isAlive", ServerLevel.class);
            connections = method(type, "getOrRefreshValidConnections", ServerLevel.class, long.class);
            context = shared.contextTarget.getDeclaringClass().getDeclaredConstructor(ServerLevel.class,
                    shared.contextTarget.getReturnType());
            context.setAccessible(true);
        }
    }
    private static Object enumValue(Class<?> type, String name) throws NoSuchFieldException {
        return Arrays.stream(type.getEnumConstants()).filter(value -> value.toString().equals(name))
                .findFirst().orElseThrow(() -> new NoSuchFieldException(type.getName() + "." + name));
    }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { var f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Method method(Class<?> type, String name, int arguments) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (var m : c.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterCount() == arguments) {
                m.setAccessible(true); return m;
            }
        }
        for (var m : type.getMethods()) if (m.getName().equals(name) && m.getParameterCount() == arguments) {
            m.setAccessible(true); return m;
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                var method = c.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Continue through the hierarchy; the catalog is package-private.
            }
        }
        try {
            var method = type.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException absent) {
            throw new NoSuchMethodException(type.getName() + "." + name);
        }
    }
}
