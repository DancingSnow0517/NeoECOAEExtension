package cn.dancingsnow.neoecoae.compat.ae2lt;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Read-only status adapter for AE2LT's ordinary and exact Tianshu jobs. */
public final class ECOAe2LtCpuStatus {
    private ECOAe2LtCpuStatus() {}

    public record Item(AEKey key, BigInteger stored, BigInteger active, BigInteger pending) {}
    public record Summary(@Nullable UUID jobId, BigInteger requested, BigInteger remaining, String state) {}

    private static boolean available() {
        var mods = ModList.get();
        return mods != null && mods.isLoaded("ae2lt");
    }

    public static List<Item> items(@Nullable ICraftingCPU cpu) {
        return cpu == null || !available() ? List.of() : Loaded.items(cpu);
    }

    @Nullable
    public static Summary summary(@Nullable ICraftingCPU cpu) {
        return cpu == null || !available() ? null : Loaded.summary(cpu);
    }

    public static void toggleSuspended(ICraftingCPU cpu) {
        if (available()) Loaded.toggleSuspended(cpu);
    }

    // Keep optional classes behind the mod-presence guard, including on dedicated servers.
    private static final class Loaded {
        private static List<Item> items(ICraftingCPU cpu) {
            if (cpu instanceof com.moakiee.ae2lt.crafting.big.BigCraftingCpu big) {
                // Reuse AE2LT's accounting of streaming blocks, byproducts, escrow and outstanding returns.
                // The long fields are bounded projections; the attached amounts are the authoritative values.
                return com.moakiee.ae2lt.crafting.big.BigCraftingStatus.create(big).getEntries().stream()
                    .map(entry -> {
                        var amounts = ((com.moakiee.ae2lt.crafting.big.BigStatusEntry) entry).ae2lt$amounts();
                        return new Item(entry.getWhat(), amounts.stored(), amounts.active(), amounts.pending());
                    }).toList();
            }
            if (cpu instanceof com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU wheel) {
                var logic = wheel.getCraftingLogic();
                var all = new KeyCounter();
                var keys = new HashSet<AEKey>();
                logic.getAllItems(all);
                all.forEach(entry -> keys.add(entry.getKey()));
                logic.getAllWaitingFor(keys);
                return keys.stream().map(key -> new Item(key, amount(logic.getStored(key)),
                    amount(logic.getWaitingFor(key)), amount(logic.getPendingOutputs(key)))).toList();
            }
            return List.of();
        }

        @Nullable
        private static Summary summary(ICraftingCPU cpu) {
            if (cpu instanceof com.moakiee.ae2lt.crafting.big.BigCraftingCpu big) {
                if (!big.isBusy()) return new Summary(null, BigInteger.ZERO, BigInteger.ZERO, "idle");
                var job = big.job();
                int blocks = job.program.blocks().size();
                // Preserve the CPU's certified-block progress ratio without projecting the order to long.
                BigInteger remaining = blocks == 0 ? job.amount : job.amount.multiply(
                    BigInteger.valueOf(Math.clamp(blocks - job.blockIndex, 0, blocks))).divide(BigInteger.valueOf(blocks));
                return new Summary(job.id, job.amount, remaining,
                    job.suspended ? "suspended" : job.returning ? "returning" : "running");
            }
            if (cpu instanceof com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU wheel) {
                var logic = wheel.getCraftingLogic();
                var output = logic.getFinalJobOutput();
                var link = logic.getLastLink();
                return new Summary(logic.hasJob() && link != null ? link.getCraftingID() : null,
                    amount(output == null ? 0 : output.amount()), amount(logic.getRemainingOutputAmount()),
                    logic.isJobSuspended() ? "suspended" : logic.isCantStoreItems() ? "returning"
                        : logic.hasJob() ? "running" : "idle");
            }
            return null;
        }

        private static void toggleSuspended(ICraftingCPU cpu) {
            if (cpu instanceof com.moakiee.ae2lt.crafting.big.BigCraftingCpu big) {
                big.toggleSuspended();
            } else if (cpu instanceof com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU wheel) {
                var logic = wheel.getCraftingLogic();
                logic.setJobSuspended(!logic.isJobSuspended());
            }
        }

        private static BigInteger amount(long value) { return BigInteger.valueOf(Math.max(0L, value)); }
    }
}
