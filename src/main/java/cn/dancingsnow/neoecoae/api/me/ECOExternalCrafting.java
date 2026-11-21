package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Optional CPU integrations register during common setup. Callbacks run on the owning server thread. */
public final class ECOExternalCrafting {
    private static final CopyOnWriteArrayList<Adapter> ADAPTERS = new CopyOnWriteArrayList<>();

    private ECOExternalCrafting() {}

    public interface Adapter {
        default boolean ownsDispatch(Object cpuLogic) { return false; }
        default boolean accepts(ICraftingCPU cpu, ICraftingPlan plan) { return false; }
        default boolean isSubmitting(ICraftingPlan plan) { return false; }
        default boolean supportsBigOrders(ICraftingCPU cpu) { return false; }
        default boolean bigOrderAvailable(IGrid grid, ICraftingCPU cpu) { return false; }
        default BigOrderSubmission submit(BigOrderContext context) { throw new UnsupportedOperationException(); }

        /** Null means this job is not owned here. Zero is an owned rejection and must not fall through. */
        default @Nullable BigInteger insert(Iterable<ICraftingCPU> cpus, UUID jobId, AEKey key,
                BigInteger amount, Actionable mode) { return null; }

        default void jobCanceled(Level level, UUID jobId) {}
        default void jobRestored(Level level, UUID jobId) {}
    }

    public record BigOrderContext(IGrid grid, ICraftingCPU cpu, ICraftingPlan visible, ECOPlanningResult confirmed,
            ECOPlannerOptions options, boolean forced, IActionSource source) {}

    public record BigOrderSubmission(String diagnostic, ICraftingSubmitResult result) {}

    public static void register(Adapter adapter) { ADAPTERS.addIfAbsent(adapter); }

    public static boolean ownsDispatch(Object cpuLogic) {
        for (var adapter : ADAPTERS) if (adapter.ownsDispatch(cpuLogic)) return true;
        return false;
    }

    public static boolean accepts(ICraftingCPU cpu, ICraftingPlan plan) {
        for (var adapter : ADAPTERS) if (adapter.accepts(cpu, plan)) return true;
        return false;
    }

    public static boolean isSubmitting(ICraftingPlan plan) {
        for (var adapter : ADAPTERS) if (adapter.isSubmitting(plan)) return true;
        return false;
    }

    public static @Nullable Adapter bigOrderAdapter(@Nullable ICraftingCPU cpu) {
        if (cpu == null) return null;
        for (var adapter : ADAPTERS) if (adapter.supportsBigOrders(cpu)) return adapter;
        return null;
    }

    public static @Nullable BigInteger insert(Iterable<ICraftingCPU> cpus, UUID jobId, AEKey key,
            BigInteger amount, Actionable mode) {
        for (var adapter : ADAPTERS) {
            var accepted = adapter.insert(cpus, jobId, key, amount, mode);
            if (accepted == null) continue;
            if (accepted.signum() < 0 || accepted.compareTo(amount) > 0)
                throw new IllegalStateException("Invalid external crafting output acceptance");
            return accepted;
        }
        return null;
    }

    public static void jobCanceled(Level level, UUID jobId) {
        for (var adapter : ADAPTERS) adapter.jobCanceled(level, jobId);
    }

    public static void jobRestored(Level level, UUID jobId) {
        for (var adapter : ADAPTERS) adapter.jobRestored(level, jobId);
    }
}
