package cn.dancingsnow.neoecoae.compat.ae2lt;

import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.level.Level;

/**
 * Reflective cancellation bridge for AE2-Lightning-Tech's time-wheel CPU.
 *
 * <p>The time-wheel logic owns a private job type and does not expose a stable
 * cancellation callback. Keeping those details here lets the optional mixin
 * remain inert when AE2LT is absent or changes its private implementation.</p>
 *
 * <p><b>Fixed for AE2LT Reborn 2.1.0+</b>: BigCraftingJob directly contains a public
 * UUID id field instead of a separate link object. This bridge now accesses job.id directly.</p>
 */
public final class ECOAe2LtCancellationBridge {
    private static final String JOB_FIELD = "job";
    private static final String CPU_FIELD = "cpu";
    private static final String JOB_ID_FIELD = "id";

    private static final ClassValue<Optional<Access>> ACCESS = new ClassValue<>() {
        @Override
        protected Optional<Access> computeValue(Class<?> type) {
            try {
                return Optional.of(new Access(type));
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return Optional.empty();
            }
        }
    };

    private ECOAe2LtCancellationBridge() {
    }

    /** Publishes the terminal decision before the time-wheel logic clears its private job. */
    public static void cancel(Object logic) {
        if (logic == null) {
            return;
        }

        var access = ACCESS.get(logic.getClass()).orElse(null);
        if (access == null) {
            return;
        }

        try {
            Object job = access.job.get(logic);
            if (job == null) {
                return;
            }
            // AE2LT Reborn 2.1.0+: BigCraftingJob has "public final UUID id" field
            UUID jobId = (UUID) access.jobId.get(job);
            if (jobId == null) {
                return;
            }
            Object cpu = access.cpu.get(logic);
            Level level = cpu == null ? null : (Level) access.level.invoke(cpu);
            // The live CPU normally always supplies a server level; otherwise let cancellation proceed quietly.
            ECOCraftingJobLifecycle.cancelAndRecover(level, jobId);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // Private AE2LT details are optional and version-sensitive. A failed bridge must not cancel the CPU.
        }
    }

    private static final class Access {
        private final Field job;
        private final Field cpu;
        private final Field jobId;
        private final Method level;

        private Access(Class<?> logicType) throws ReflectiveOperationException {
            this.job = field(logicType, JOB_FIELD);
            this.cpu = field(logicType, CPU_FIELD);
            // BigCraftingJob.id is a public final field, but we still need reflection to access it
            this.jobId = field(job.getType(), JOB_ID_FIELD);
            this.level = method(cpu.getType(), "getLevel");
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Continue through the implementation hierarchy.
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Continue through the implementation hierarchy.
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
}
