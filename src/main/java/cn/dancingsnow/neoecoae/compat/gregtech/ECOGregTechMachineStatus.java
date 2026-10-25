package cn.dancingsnow.neoecoae.compat.gregtech;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/** Optional GT/GTL observation. No GT classes are linked, inventories probed, or chunks loaded. */
public final class ECOGregTechMachineStatus {
    private static final ClassValue<Map<String, Optional<Member>>> MEMBERS = new ClassValue<>() {
        @Override protected Map<String, Optional<Member>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private ECOGregTechMachineStatus() {}

    public static List<Target> targets(ICraftingProvider provider) {
        Object current = provider;
        for (int i = 0; current != null && i < 8; i++) {
            if (current instanceof ECOProcessingTargetProvider traced) {
                Target target = traced.neoecoae$getProcessingTarget();
                return target == null ? List.of() : List.of(target);
            }
            if (buffer(current.getClass())) {
                Object world = value(current, "getLevel");
                Object position = value(current, "getPos", "getBlockPos");
                return world instanceof Level level && position instanceof BlockPos pos
                    ? List.of(new Target(level.dimension().location(), pos)) : List.of();
            }
            Object next = value(current, "getLogic");
            if (next == current) break;
            current = next;
        }
        return List.of();
    }

    /** Incremental traversal. Completion has no machine-count limit; the caller budgets each step. */
    public static final class Scan {
        private final ArrayDeque<Target> pending = new ArrayDeque<>();
        private final HashSet<Target> visited = new HashSet<>();
        private final java.util.LinkedHashMap<String, Machine> machines = new java.util.LinkedHashMap<>();
        private final AEKey output;

        public Scan(Iterable<Target> roots, AEKey output) {
            for (var root : roots) pending.add(root);
            this.output = output;
        }

        public boolean complete() { return pending.isEmpty(); }
        public List<Machine> machines() { return new ArrayList<>(machines.values()); }
        public void addRoot(Target target) { if (!visited.contains(target)) pending.addLast(target); }

        /** One step visits at most one coordinate, including duplicate and unavailable coordinates. */
        public void step(Level context) {
            if (pending.isEmpty() || context == null || context.isClientSide || context.getServer() == null) return;
            Target target = pending.removeFirst();
            if (!visited.add(target)) return;
            Level level = context.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, target.dimension()));
            if (level == null || !level.hasChunkAt(target.position())) {
                machines.put(location(target), unavailable(target, "unloaded"));
                return;
            }
            var be = level.getBlockEntity(target.position());
            if (be == null || be.isRemoved()) return;
            Object machine = value(be, "getMetaMachine");
            if (machine == null) return;
            Object logic = value(machine, "getRecipeLogic");
            if (logic != null) {
                machines.put(location(target), sampleMachine(logic, be.getBlockState().getBlock().getName(), location(target), output));
                return;
            }
            // Resolving getters may prune proxies or resolve unloaded controllers. Read only stored positions.
            addPositions(pending, target, value(machine, "controllerPositions"));
            if (buffer(machine.getClass())) addPositions(pending, target, value(machine, "proxies"));
            if (proxy(machine.getClass())) addPositions(pending, target, value(machine, "bufferPos"));
        }
    }

    /** Tests and adapters can inspect the same version-tolerant recipe-logic contract. */
    static Machine sampleMachine(Object logic, Component name, String location, AEKey output) {
        Object recipe = value(logic, "getLastRecipe", "lastRecipe");
        boolean matches = recipe != null && produces(recipe, output);
        int duration = matches ? positiveInt(value(logic, "getMaxProgress", "getDuration", "duration")) : 0;
        int progress = duration > 0 ? positiveInt(value(logic, "getProgress", "progress")) : 0;
        State state = Boolean.TRUE.equals(value(logic, "isSuspend")) ? State.PAUSED
            : matches && Boolean.TRUE.equals(value(logic, "isWaiting")) ? State.WAITING
            : matches && Boolean.TRUE.equals(value(logic, "isWorking")) ? State.RUNNING : State.QUEUED;
        if (state == State.QUEUED) { duration = 0; progress = 0; }
        Component reason = Component.empty();
        if (state == State.WAITING || state == State.QUEUED) {
            reason = component(value(logic, "getWaitingReason", "getBestFailureReason"));
            Object result = value(logic, state == State.WAITING ? "getWorkingStatus" : "getRecipeStatus");
            Component gtlReason = component(value(result, "reason"));
            if (!gtlReason.getString().isEmpty()) reason = gtlReason;
        }
        long parallel = matches ? positiveLong(value(recipe, "getRealParallels", "getTotalRuns", "parallels")) : 0;
        Object id = matches ? value(recipe, "getId", "id") : null;
        return new Machine(name, location, id == null ? "" : id.toString(), progress, duration,
            parallel, state, reason);
    }

    private static boolean produces(Object recipe, AEKey output) {
        Object contents = value(recipe, "outputs");
        if (!(contents instanceof Map<?, ?> map)) return false;
        for (Object list : map.values()) if (matches(list, output, 0)) return true;
        return false;
    }

    private static boolean matches(Object content, AEKey output, int depth) {
        if (content == null || depth > 5) return false;
        if (content instanceof ItemStack stack) return output instanceof AEItemKey item && item.matches(stack);
        if (content instanceof Ingredient ingredient) {
            for (var stack : ingredient.getItems()) if (matches(stack, output, depth + 1)) return true;
            return false;
        }
        if (content instanceof Iterable<?> list) {
            for (var entry : list) if (matches(entry, output, depth + 1)) return true;
            return false;
        }
        if (content instanceof Object[] array) {
            for (var entry : array) if (matches(entry, output, depth + 1)) return true;
            return false;
        }
        if (output instanceof AEFluidKey fluid && value(content, "getFluid") instanceof Fluid actual) {
            Object tag = value(content, "getTag");
            return fluid.equals(AEFluidKey.of(actual, tag instanceof CompoundTag nbt ? nbt : null));
        }
        Object nested = value(content, "getContent", "content", "getStacks");
        return nested != content && matches(nested, output, depth + 1);
    }

    private static void addPositions(ArrayDeque<Target> pending, Target parent, Object positions) {
        if (positions instanceof BlockPos pos) pending.addLast(new Target(parent.dimension(), pos));
        else if (positions instanceof Iterable<?> list) {
            for (Object entry : list) {
                if (entry instanceof BlockPos pos) pending.addLast(new Target(parent.dimension(), pos));
            }
        }
    }

    private static boolean buffer(Class<?> type) {
        return named(type, "com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine")
            || named(type, "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase");
    }

    private static boolean proxy(Class<?> type) {
        return named(type, "com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferProxyPartMachine")
            || named(type, "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferProxyPartMachine");
    }

    private static boolean named(Class<?> type, String name) {
        for (var current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().equals(name)) return true;
        }
        return false;
    }

    private static String location(Target target) {
        return target.dimension() + " [" + target.position().toShortString() + "]";
    }

    private static Machine unavailable(Target target, String reason) {
        return new Machine(Component.translatable("gui.neoecoae.processing.machine"), location(target), "", 0, 0, 0,
            State.UNAVAILABLE, Component.translatable("gui.neoecoae.processing." + reason));
    }

    private static Component component(Object value) {
        if (value instanceof Optional<?> optional) value = optional.orElse(null);
        return value instanceof Component component ? component : Component.empty();
    }

    private static int positiveInt(Object value) { return (int) Math.min(Integer.MAX_VALUE, positiveLong(value)); }
    private static long positiveLong(Object value) { return value instanceof Number number ? Math.max(0, number.longValue()) : 0; }

    private static Object value(Object object, String... names) {
        if (object == null) return null;
        try {
            var members = MEMBERS.get(object.getClass());
            for (String name : names) {
                var member = members.computeIfAbsent(name, key -> member(object.getClass(), key));
                if (member.isPresent()) {
                    Object result = member.get().get(object);
                    if (result != null) return result;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError incompatible) {
            // Optional integrations must never affect dispatch or output accounting.
        }
        return null;
    }

    private static Optional<Member> member(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            if (method.trySetAccessible()) return Optional.of(method::invoke);
        } catch (NoSuchMethodException ignored) {}
        for (var current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                if (field.trySetAccessible()) return Optional.of(field::get);
            } catch (NoSuchFieldException ignored) {}
        }
        return Optional.empty();
    }

    @FunctionalInterface private interface Member {
        Object get(Object object) throws ReflectiveOperationException;
    }
}
