package cn.dancingsnow.neoecoae.compat.ae2lt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission.StopReason;
import cn.dancingsnow.neoecoae.compat.processing.ProcessingReflection;
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
    private static final ClassValue<Optional<Access>> ACCESS = new ClassValue<>() {
        protected Optional<Access> computeValue(Class<?> type) {
            try { return Optional.of(new Access(type)); }
            catch (ReflectiveOperationException | LinkageError unavailable) { return Optional.empty(); }
        }
    };
    private ECOAe2LtDirectDispatch() {}

    public static boolean isProvider(Object provider) { return logic(provider) != null; }
    @Nullable public static Session open(Object provider) {
        Object logic = logic(provider);
        if (logic == null) return null;
        var access = ACCESS.get(logic.getClass()).orElse(null);
        return access == null ? null : new Session(logic, access);
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
        private Session(Object logic, Access a) { this.logic = logic; this.a = a; }

        public boolean canBatchWhileBlocking() {
            if (a.blockingMode == null) return false;
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

        public ECOBatchAdmission submit(IPatternDetails pattern, KeyCounter[] prototype, long copies) {
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
                boolean wireless = a.mode.invoke(host).toString().equals("WIRELESS");
                Object overflow = a.overflow.get(logic);
                a.refresh.invoke(overflow);
                if (wireless && (boolean) a.backpressure.invoke(overflow)) {
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
                List<?> targets = wireless ? (List<?>) a.connections.invoke(logic, level, level.getGameTime())
                        : (List<?>) a.directions.invoke(logic);
                double cost = ((Number) a.cost.invoke(null, (Object) prototype)).doubleValue();
                if (targets.isEmpty()) return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
                if (!wireless) {
                    var resolved = new ArrayList<Target>();
                    for (Object direction : (List<?>) a.normalOrder.invoke(a.normalDispatch.get(logic), targets)) {
                        Object context = a.normalTarget.invoke(logic, level, direction, pattern);
                        if (context != null) resolved.add(new Target(level, a.contextTarget.invoke(context), context));
                    }
                    return submitDistributed(pattern, canonical, prototype, copies, cost, level, resolved, null);
                }
                Object scheduler = a.wirelessDispatch.get(logic);
                boolean fast = a.speed.invoke(host).toString().equals("FAST");
                Object mode = a.dispatchMode.invoke(host);
                Object[] preparation = a.prepare.getParameterCount() == 3
                        ? new Object[]{targets, level.getGameTime(), fast}
                        : new Object[]{targets, level.getGameTime(), fast, mode};
                a.prepare.invoke(scheduler, preparation);
                if (mode.toString().equals("SINGLE_TARGET")) {
                    return submitSingleTarget(pattern, canonical, prototype, copies, cost, level,
                            scheduler, mode, fast, targets.size());
                }
                // Reuse provider-owned, pattern-scoped fairness across CPU visits. ECO still owns
                // batch growth; only physical target selection and accepted-copy counts are shared.
                Object pass = a.fairPass.invoke(scheduler, canonical, level.getGameTime());
                try {
                    return submitDistributed(pattern, canonical, prototype, copies, cost, level, List.of(), pass);
                } finally {
                    a.closePass.invoke(pass);
                }
            } catch (ReflectiveOperationException failure) {
                // A callback/save can fail after insertion; never refund or replay unknown custody.
                throw new IllegalStateException("AE2LT direct batch custody is unknown", failure);
            }
        }

        private ECOBatchAdmission submitDistributed(IPatternDetails pattern, Object canonical, KeyCounter[] prototype,
                long copies, double cost, ServerLevel level, List<Target> targets, @Nullable Object pass)
                throws ReflectiveOperationException {
            int targetCount = pass == null ? targets.size() : ((Number) a.fairSize.invoke(pass)).intValue();
            if (targetCount == 0) return ECOBatchAdmission.rejected(
                    pass == null ? StopReason.TARGET_UNAVAILABLE : StopReason.FAIRNESS);
            long accepted = 0;
            long inserted = 0;
            StopReason reason = StopReason.NONE;
            for (int i = 0; i < targetCount && accepted < copies; i++) {
                Object connection = pass == null ? null : a.fairPoll.invoke(pass);
                if (pass != null && connection == null) { reason = StopReason.FAIRNESS; break; }
                Target target = pass == null ? targets.get(i) : wirelessTarget(level, connection, pattern);
                if (target == null) {
                    if ((boolean) a.contains.invoke(a.overflow.get(logic), connection)) {
                        a.pauseTarget.invoke(a.wirelessDispatch.get(logic), connection);
                    } else {
                        a.fairCooldown.invoke(pass, connection, level.getGameTime() + 1L);
                    }
                    reason = StopReason.TARGET_UNAVAILABLE;
                    continue;
                }
                long remaining = copies - accepted;
                long share = 1L + (remaining - 1L) / (targetCount - i);
                if (pass != null) {
                    // Let under-served machines catch up, while splitting a fresh batch among
                    // all ready targets instead of handing the complete offer to its first target.
                    share = Math.min(remaining, Math.max(share,
                            ((Number) a.fairAllowance.invoke(pass, connection)).longValue()));
                    a.fairRaise.invoke(pass, connection, share);
                }
                Chunk chunk = pushChunk(target, pattern, canonical, prototype, share, cost, pass != null);
                if (!chunk.valid()) return ECOBatchAdmission.indeterminate();
                accepted += chunk.accepted;
                if (chunk.inserted) inserted += chunk.accepted;
                if (pass != null) {
                    if (chunk.accepted > 0) a.fairSuccess.invoke(pass, connection, chunk.accepted);
                    else if (chunk.reason != StopReason.RESOURCES) {
                        a.fairCooldown.invoke(pass, connection, level.getGameTime() + 1L);
                    }
                    // Native flushing resumes this target after its owned bucket drains.
                    if ((boolean) a.contains.invoke(a.overflow.get(logic), connection)) {
                        a.pauseTarget.invoke(a.wirelessDispatch.get(logic), connection);
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

        private ECOBatchAdmission submitSingleTarget(IPatternDetails pattern, Object canonical, KeyCounter[] prototype,
                long copies, double cost, ServerLevel level, Object scheduler, Object mode, boolean fast, int targets)
                throws ReflectiveOperationException {
            var receipt = new Chunk[1];
            Class<?> callback = a.single.getParameterTypes()[5];
            Object attempt = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback},
                    (proxy, method, arguments) -> {
                        Target target = wirelessTarget(level, arguments[0], pattern);
                        if (target == null) {
                            receipt[0] = new Chunk(0, 0, false, StopReason.TARGET_UNAVAILABLE);
                            return a.hardFail;
                        }
                        Chunk chunk = pushChunk(target, pattern, canonical, prototype, copies, cost, true);
                        receipt[0] = chunk;
                        if (!chunk.valid()) return a.globalAbort;
                        return chunk.accepted > 0 ? a.success
                                : chunk.reason == StopReason.RESOURCES ? a.globalAbort : a.softFail;
                    });
            java.util.function.Predicate<Object> alive = connection -> {
                try {
                    var targetLevel = level.getServer().getLevel(
                            (net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>) a.dimension.invoke(connection));
                    return targetLevel != null && (boolean) a.alive.invoke(connection, targetLevel);
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            };
            java.util.function.Consumer<Object> removed = connection -> {
                try { a.connectionsDirty.setBoolean(logic, true); }
                catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
            };
            a.single.invoke(scheduler, mode, canonical, level.getGameTime(), fast, targets, attempt, alive, removed);
            Chunk chunk = receipt[0];
            if (chunk == null) return ECOBatchAdmission.rejected(StopReason.TARGET_UNAVAILABLE);
            if (!chunk.valid()) return ECOBatchAdmission.indeterminate();
            StopReason reason = chunk.reason;
            if (chunk.accepted > 0 && !drained()) reason = StopReason.BUFFERED;
            return receipt(chunk.accepted, chunk.inserted ? chunk.accepted : 0L, reason);
        }

        @Nullable private Target wirelessTarget(ServerLevel level, Object connection, IPatternDetails pattern)
                throws ReflectiveOperationException {
            if ((boolean) a.contains.invoke(a.overflow.get(logic), connection)) return null;
            var targetLevel = level.getServer().getLevel(
                    (net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>) a.dimension.invoke(connection));
            if (targetLevel == null || !targetLevel.isLoaded((net.minecraft.core.BlockPos) a.pos.invoke(connection))
                    || !(boolean) a.canAccept.invoke(connection, targetLevel, pattern, a.source.get(logic))) return null;
            return new Target(targetLevel, connection, a.context.newInstance(targetLevel, connection));
        }

        private Chunk pushChunk(Target target, IPatternDetails pattern, Object canonical, KeyCounter[] prototype,
                long copies, double cost, boolean wireless) throws ReflectiveOperationException {
            int count = (int) Math.min(Integer.MAX_VALUE, copies);
            if (!allowsBatch() || (boolean) a.blocked.invoke(logic, target.context, canonical)) {
                return new Chunk(count, 0, false, StopReason.TARGET_UNAVAILABLE);
            }
            a.before.invoke(a.autoReturn.get(logic), target.level, target.address);
            // A machine lacking a counted transport can only own one physical transaction.
            if (!(boolean) a.supportsBatch.invoke(target.address, target.level, pattern)) count = 1;
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
                    if (!wireless) {
                        a.sendDirection.invoke(logic, ((Direction) a.face.invoke(target.address)).getOpposite());
                        a.send.invoke(logic);
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
        pattern = cn.dancingsnow.neoecoae.compat.ae2.ECOProviderPatternIntrospection.unwrap(pattern);
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

    /** Resolve the complete transport contract before claiming the provider; no game types are optional links. */
    private static final class Access {
        final Field node, host, catalog, overflow, source, autoReturn, normalDispatch, wirelessDispatch, connectionsDirty;
        final Constructor<?> context;
        final Object complete, success, softFail, hardFail, globalAbort;
        final @Nullable Method blockingMode;
        final Method flush, flushLocal, localOverflow, sendList, lock, resolve, level, mode, refresh, backpressure,
                connections, directions, cost, contains, dimension, pos, canAccept, normalTarget, contextTarget,
                blocked, before, supportsBatch, chunk, owned, abort, inserted, sendDirection, face, send, wake, save, empty, setBypass,
                normalOrder, dispatchMode, speed, prepare, fairPass, fairSize, fairPoll, fairAllowance, fairRaise, fairSuccess,
                fairCooldown, pauseTarget, closePass, single, alive;
        Access(Class<?> type) throws ReflectiveOperationException {
            node = field(type, "gridNode"); host = field(type, "overloadedHost");
            Method optionalBlockingMode;
            try { optionalBlockingMode = host.getType().getMethod("getBlockingMode"); }
            catch (NoSuchMethodException | SecurityException unavailable) { optionalBlockingMode = null; }
            blockingMode = optionalBlockingMode;
            catalog = field(type, "patternCatalog"); overflow = field(type, "wirelessOverflow");
            source = field(type, "wirelessSource"); autoReturn = field(type, "autoReturn");
            normalDispatch = field(type, "normalDispatch"); wirelessDispatch = field(type, "wirelessDispatch");
            connectionsDirty = field(type, "connectionsDirty");
            normalOrder = method(normalDispatch.getType(), "dispatchOrder", 1);
            dispatchMode = method(host.getType(), "getWirelessDispatchMode", 0);
            speed = method(host.getType(), "getWirelessSpeedMode", 0);
            // Released 2.1.2-beta3 includes the dispatch mode; newer source derives it later.
            Method preparation;
            try { preparation = method(wirelessDispatch.getType(), "prepare", 3); }
            catch (NoSuchMethodException olderContract) {
                preparation = method(wirelessDispatch.getType(), "prepare", 4);
            }
            prepare = preparation;
            fairPass = method(wirelessDispatch.getType(), "beginFairPass", 2);
            Class<?> passType = fairPass.getReturnType();
            fairSize = method(passType, "activeTargetsAtStart", 0); fairPoll = method(passType, "poll", 0);
            fairAllowance = method(passType, "allowance", 1); fairRaise = method(passType, "raiseAllowance", 2);
            fairSuccess = method(passType, "success", 2); fairCooldown = method(passType, "cooldown", 2);
            pauseTarget = method(wirelessDispatch.getType(), "pauseTarget", 1);
            closePass = method(passType, "close", 0);
            single = method(wirelessDispatch.getType(), "dispatchSingleCopy", 8);
            Class<?> outcome = method(single.getParameterTypes()[5], "push", 1).getReturnType();
            success = enumValue(outcome, "SUCCESS"); softFail = enumValue(outcome, "SOFT_FAIL");
            hardFail = enumValue(outcome, "HARD_FAIL"); globalAbort = enumValue(outcome, "GLOBAL_ABORT");
            flush = method(type, "flushWirelessSends", 0); flushLocal = method(type, "flushLocalDirectionalOverflow", 0);
            localOverflow = method(type, "hasLocalDirectionalOverflow", 0);
            sendList = method(type, "getSendList", 0); lock = method(type, "getCraftingLockedReason", 0);
            // The Reborn catalog also exposes resolve(AEKey). Selecting by arity alone
            // can bind that overload and fail only when the first live dispatch arrives.
            resolve = method(catalog.getType(), "resolve", IPatternDetails.class);
            level = method(host.getType(), "getLevel", 0); mode = method(host.getType(), "getProviderMode", 0);
            refresh = method(overflow.getType(), "refreshBackpressure", 0);
            backpressure = method(overflow.getType(), "isBackpressured", 0);
            empty = method(overflow.getType(), "isEmpty", 0); contains = method(overflow.getType(), "contains", 1);
            connections = method(type, "getOrRefreshValidConnections", 2);
            directions = method(type, "activeNormalTargetDirections", 0);
            normalTarget = method(type, "resolveNormalBatchTarget", 3);
            Class<?> contextType = normalTarget.getReturnType();
            context = contextType.getDeclaredConstructors()[0]; context.setAccessible(true);
            contextTarget = method(contextType, "target", 0);
            Class<?> target = contextTarget.getReturnType();
            Class<?> connection = contains.getParameterTypes()[0];
            dimension = method(connection, "dimension", 0); pos = method(target, "pos", 0);
            face = method(target, "boundFace", 0); canAccept = method(target, "canAccept", 3);
            supportsBatch = method(target, "supportsBatch", 2);
            alive = method(target, "isAlive", 1);
            blocked = method(type, "isBatchTargetBlocked", 2);
            before = method(autoReturn.getType(), "beforeDispatch", 2);
            chunk = method(type, "pushBatchChunk", 7);
            Class<?> acceptance = chunk.getParameterTypes()[6];
            complete = Arrays.stream(acceptance.getEnumConstants()).filter(v -> v.toString().equals("COMPLETE_BATCH"))
                    .findFirst().orElseThrow(() -> new NoSuchFieldException("COMPLETE_BATCH"));
            Class<?> receipt = chunk.getReturnType();
            owned = method(receipt, "ownedCopies", 0); abort = method(receipt, "globalAbort", 0);
            inserted = method(receipt, "fullyInserted", 0);
            sendDirection = method(type, "setSendDirection", 1); send = method(type, "invokeSendStacksOut", 0);
            wake = method(type, "alertGridTick", 0); save = method(type, "saveChanges", 0);
            Class<?> power = Class.forName("com.moakiee.ae2lt.logic.energy.PowerCostUtil", false, type.getClassLoader());
            cost = method(power, "totalCost", 1);
            Class<?> registry = Class.forName("com.moakiee.ae2lt.logic.provider.EjectModeRegistry", false, type.getClassLoader());
            setBypass = method(registry, "setBypass", 1);
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
