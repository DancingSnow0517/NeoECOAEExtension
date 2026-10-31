package cn.dancingsnow.neoecoae.compat.gregtech;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchInputScaling;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Optional GT transports and physical buffer limits. ECO owns batch sizing, independently of GTL auto-expand. */
public final class ECOGregTechProcessingDispatch {
    private static final String GT_BUFFER =
            "com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine";
    private static final String GTL_BUFFER =
            "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase";
    private static final String GTL_MOLECULAR_ASSEMBLER =
            "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine";
    private static final String GTL_CAPACITY =
            "org.gtlcore.gtlcore.integration.ae2.crafting.IPatternProviderAutoExpand";
    private static final ResourceLocation CIRCUIT = new ResourceLocation("gtceu", "programmed_circuit");
    private static final ECOBatchInputScaling CIRCUIT_SCALING = new ECOBatchInputScaling(
            key -> key instanceof AEItemKey && CIRCUIT.equals(key.getId()));
    private static final ThreadLocal<BufferPush> CURRENT_PUSH = new ThreadLocal<>();
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) {
            boolean gtlBuffer = hasType(type, GTL_BUFFER);
            boolean stockBuffer = BatchInputProvider.class.isAssignableFrom(type) && hasType(type, GT_BUFFER);
            boolean molecularAssembler = hasType(type, GTL_MOLECULAR_ASSEMBLER);
            Method bufferCapacity = gtlBuffer || molecularAssembler
                    ? method(type, "gtlcore$graphCapacity", IPatternDetails.class, Map.class, long.class) : null;
            // The assembler consumes aggregate counters with its registered original pattern. Its
            // preflight also rejects missing tools and bounds the long-backed output queue.
            return new Access(gtlBuffer || stockBuffer, molecularAssembler && bufferCapacity != null,
                    gtlBuffer || hasType(type, GTL_CAPACITY), bufferCapacity,
                    method(type, "getLogic"));
        }
    };

    private ECOGregTechProcessingDispatch() {}

    /** Added only when the native GT buffer's physical-input call has been adapted successfully. */
    public interface BatchInputProvider {}

    public static boolean isBuffer(Object provider) {
        return provider != null && ACCESS.get(provider.getClass()).buffer();
    }

    public static boolean isBatchProvider(Object provider) {
        if (provider == null) return false;
        var access = ACCESS.get(provider.getClass());
        return access.buffer() || access.molecularAssembler();
    }

    public static ECOBatchInputScaling inputScaling(Object provider) {
        Object logic = logic(provider);
        return logic != null && ACCESS.get(logic.getClass()).circuitConfiguration()
                ? CIRCUIT_SCALING : ECOBatchInputScaling.LINEAR;
    }

    public static boolean supportsInputs(KeyCounter[] inputs) {
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey) && !(entry.getKey() instanceof AEFluidKey)) return false;
            }
        }
        return true;
    }

    /** Queried for every chunk, before any debit; GTL also accounts for already buffered quantities. */
    public static long maxOperations(Object provider, IPatternDetails pattern, KeyCounter[] inputs, long requested) {
        if (requested <= 0) return 0;
        Object logic = logic(provider);
        if (logic == null) return requested;
        var access = ACCESS.get(logic.getClass());
        try {
            if (access.bufferCapacity() != null) {
                Map<AEKey, Long> perCraft = new HashMap<>();
                for (var counter : inputs) {
                    for (var entry : counter) perCraft.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
                }
                return bounded(access.bufferCapacity().invoke(logic, pattern, perCraft, requested), requested);
            }
            return requested;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            // Capacity observation has no custody. Retain the ordinary one-copy admission contract.
            return Math.min(1L, requested);
        }
    }

    /** Public admission sees the original pattern; only the native slot's emission sees the execution view. */
    public static boolean pushBuffer(ICraftingProvider provider, IPatternDetails pattern,
            IPatternDetails executionPattern, KeyCounter[] totals) {
        BufferPush previous = CURRENT_PUSH.get();
        CURRENT_PUSH.set(new BufferPush(pattern, executionPattern, totals));
        try {
            return provider.pushPattern(pattern, totals);
        } finally {
            if (previous == null) CURRENT_PUSH.remove();
            else CURRENT_PUSH.set(previous);
        }
    }

    public static IPatternDetails bufferExecutionPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        BufferPush push = CURRENT_PUSH.get();
        return push != null && push.pattern() == pattern && push.inputs() == inputs ? push.execution() : pattern;
    }

    private static long bounded(Object value, long requested) {
        return Math.max(0L, Math.min(requested, ((Number) value).longValue()));
    }

    private static Object logic(Object provider) {
        Object current = provider;
        for (int depth = 0; current != null && depth < 8; depth++) {
            var access = ACCESS.get(current.getClass());
            if (access.buffer() || access.molecularAssembler()
                    || access.circuitConfiguration() || access.logic() == null) return current;
            try {
                Object next = access.logic().invoke(current);
                if (next == current) return current;
                current = next;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                return null;
            }
        }
        return null;
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        try {
            Method method = type.getMethod(name, parameters);
            return method.trySetAccessible() ? method : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    private static boolean hasType(Class<?> type, String name) {
        if (type == null) return false;
        if (type.getName().equals(name)) return true;
        for (var contract : type.getInterfaces()) if (hasType(contract, name)) return true;
        return hasType(type.getSuperclass(), name);
    }

    private record Access(boolean buffer, boolean molecularAssembler, boolean circuitConfiguration, Method bufferCapacity,
                          Method logic) {}
    private record BufferPush(IPatternDetails pattern, IPatternDetails execution, KeyCounter[] inputs) {}
}
