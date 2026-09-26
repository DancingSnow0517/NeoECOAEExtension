package cn.dancingsnow.neoecoae.compat.ae2lt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** ECO-only transport adapter. Never calls getBatchCapacity, pushBatch or either AE2LT ramp. */
public final class ECOAe2LtDirectDispatch {
    private static final String LOGIC = "com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic";
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
    @Nullable private static Object logic(Object provider) {
        return logic(provider, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    @Nullable private static Object logic(Object provider, Set<Object> visited) {
        if (provider == null) return null;
        if (!visited.add(provider)) return null;
        for (Class<?> type = provider.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(LOGIC)) return provider;
        }
        try {
            Object value = provider.getClass().getMethod("getLogic").invoke(provider);
            return logic(value, visited);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    public static final class Session {
        private final Object logic;
        private final Access a;
        private Session(Object logic, Access a) { this.logic = logic; this.a = a; }

        public ECOBatchAdmission submit(IPatternDetails pattern, KeyCounter[] prototype, long copies) {
            if (copies <= 0) return ECOBatchAdmission.rejected();
            try {
                // Flush old custody before accepting anything new, just like the native entry point.
                a.flush.invoke(logic);
                a.flushLocal.invoke(logic);
                var node = (IManagedGridNode) a.node.get(logic);
                if (!node.isActive() || (boolean) a.localOverflow.invoke(logic)
                        || !((List<?>) a.sendList.invoke(logic)).isEmpty()
                        || !a.lock.invoke(logic).toString().equals("NONE")) return ECOBatchAdmission.rejected();
                Object canonical = a.resolve.invoke(a.catalog.get(logic), pattern);
                if (canonical == null) return ECOBatchAdmission.rejected();
                Object host = a.host.get(logic);
                if (!(a.level.invoke(host) instanceof ServerLevel level)) return ECOBatchAdmission.rejected();
                boolean wireless = a.mode.invoke(host).toString().equals("WIRELESS");
                Object overflow = a.overflow.get(logic);
                a.refresh.invoke(overflow);
                if (wireless && (boolean) a.backpressure.invoke(overflow)) return ECOBatchAdmission.rejected();
                // Directional inventory transactions have per-face overflow ownership. Reuse the
                // existing one-copy transaction rather than multiplying its routed buffers.
                if (directional(pattern)) {
                    long accepted = 0;
                    while (accepted < copies && ((ICraftingProvider) logic).pushPattern(pattern, copy(prototype))) {
                        accepted++;
                        if (!drained()) break;
                    }
                    if (accepted > 0) a.save.invoke(logic);
                    return accepted == 0 ? ECOBatchAdmission.rejected()
                            : ECOBatchAdmission.accepted(accepted, accepted == copies && drained());
                }
                List<?> targets = wireless ? (List<?>) a.connections.invoke(logic, level, level.getGameTime())
                        : (List<?>) a.directions.invoke(logic);
                double cost = ((Number) a.cost.invoke(null, (Object) prototype)).doubleValue();
                // Rotate only ECO calls. Native target scheduling/adaptive history remains untouched.
                if (targets.isEmpty()) return ECOBatchAdmission.rejected();
                int start = Math.floorMod(level.getGameTime(), targets.size());
                for (int i = 0; i < targets.size(); i++) {
                    Object target;
                    Object context;
                    ServerLevel targetLevel;
                    if (wireless) {
                        target = targets.get((start + i) % targets.size());
                        if ((boolean) a.contains.invoke(overflow, target)) continue;
                        targetLevel = level.getServer().getLevel((net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>)
                                a.dimension.invoke(target));
                        if (targetLevel == null || !targetLevel.isLoaded((net.minecraft.core.BlockPos) a.pos.invoke(target))) continue;
                        if (!(boolean) a.canAccept.invoke(target, targetLevel, pattern, a.source.get(logic))) continue;
                        context = a.context.newInstance(targetLevel, target);
                    } else {
                        context = a.normalTarget.invoke(logic, level, targets.get((start + i) % targets.size()), pattern);
                        if (context == null) continue;
                        target = a.contextTarget.invoke(context);
                        targetLevel = level;
                    }
                    if ((boolean) a.blocked.invoke(logic, context, canonical)) continue;
                    a.before.invoke(a.autoReturn.get(logic), targetLevel, target);
                    int count = (int) Math.min(Integer.MAX_VALUE, copies);
                    // A machine lacking a counted transport can only own one physical transaction.
                    if (!(boolean) a.supportsBatch.invoke(target, targetLevel, pattern)) count = 1;
                    a.setBypass.invoke(null, true);
                    try {
                        Object receipt = a.chunk.invoke(logic, context, pattern, canonical, copy(prototype), count, cost, a.complete);
                        long accepted = ((Number) a.owned.invoke(receipt)).longValue();
                        if (accepted < 0 || accepted > count) return ECOBatchAdmission.indeterminate();
                        if (accepted == 0) {
                            if ((boolean) a.abort.invoke(receipt)) return ECOBatchAdmission.rejected();
                            continue;
                        }
                        if (!wireless) {
                            a.sendDirection.invoke(logic, ((Direction) a.face.invoke(target)).getOpposite());
                            a.send.invoke(logic);
                        }
                        a.wake.invoke(logic);
                        a.save.invoke(logic);
                        return ECOBatchAdmission.accepted(accepted,
                                accepted == copies && (boolean) a.inserted.invoke(receipt) && drained());
                    } finally {
                        a.setBypass.invoke(null, false);
                    }
                }
                return ECOBatchAdmission.rejected();
            } catch (ReflectiveOperationException failure) {
                // A callback/save can fail after insertion; never refund or replay unknown custody.
                throw new IllegalStateException("AE2LT direct batch custody is unknown", failure);
            }
        }

        private boolean drained() throws ReflectiveOperationException {
            return ((List<?>) a.sendList.invoke(logic)).isEmpty()
                    && (boolean) a.empty.invoke(a.overflow.get(logic))
                    && !(boolean) a.localOverflow.invoke(logic);
        }
    }

    private static boolean directional(IPatternDetails pattern) {
        pattern = cn.dancingsnow.neoecoae.compat.ae2.ECOProviderPatternIntrospection.unwrap(pattern);
        if (pattern == null) return false;
        try { return (boolean) pattern.getClass().getMethod("directionalInputsSet").invoke(pattern); }
        catch (NoSuchMethodException absent) { return false; }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static KeyCounter[] copy(KeyCounter[] inputs) {
        var result = new KeyCounter[inputs.length];
        for (int i = 0; i < inputs.length; i++) { result[i] = new KeyCounter(); result[i].addAll(inputs[i]); }
        return result;
    }

    /** Resolve the complete transport contract before claiming the provider; no game types are optional links. */
    private static final class Access {
        final Field node, host, catalog, overflow, source, autoReturn;
        final Constructor<?> context;
        final Object complete;
        final Method flush, flushLocal, localOverflow, sendList, lock, resolve, level, mode, refresh, backpressure,
                connections, directions, cost, contains, dimension, pos, canAccept, normalTarget, contextTarget,
                blocked, before, supportsBatch, chunk, owned, abort, inserted, sendDirection, face, send, wake, save, empty, setBypass;
        Access(Class<?> type) throws ReflectiveOperationException {
            node = field(type, "gridNode"); host = field(type, "overloadedHost");
            catalog = field(type, "patternCatalog"); overflow = field(type, "wirelessOverflow");
            source = field(type, "wirelessSource"); autoReturn = field(type, "autoReturn");
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
            Class<?> registry = Class.forName("com.moakiee.ae2lt.logic.EjectModeRegistry", false, type.getClassLoader());
            setBypass = method(registry, "setBypass", 1);
        }
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
