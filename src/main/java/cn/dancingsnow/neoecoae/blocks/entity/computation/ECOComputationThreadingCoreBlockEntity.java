package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOComputationThreadingCoreBlockEntity
        extends AbstractComputationBlockEntity<ECOComputationThreadingCoreBlockEntity> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    @Getter
    private final IECOTier tier;

    @Getter
    private ECOCraftingCPU[] cpus;

    private CompoundTag[] deferredInit;

    public ECOComputationThreadingCoreBlockEntity(
            BlockEntityType<?> type, BlockPos pos, BlockState blockState, IECOTier tier) {
        super(type, pos, blockState);
        this.tier = tier;
        cpus = new ECOCraftingCPU[tier.getCPUThreads()];
        deferredInit = new CompoundTag[tier.getCPUThreads()];
    }

    @Nullable public ECOCraftingCPU spawn(ICraftingPlan plan) {
        if (cluster != null && cluster.getActiveCpuCountCached() >= cluster.getMaxThreads()) {
            return null;
        }
        for (int i = 0; i < cpus.length; i++) {
            if (cpus[i] == null) {
                ECOCraftingCPU cpu = new ECOCraftingCPU(cluster, plan, this);
                cpus[i] = cpu;
                markForUpdate();
                return cpu;
            }
        }
        return null;
    }

    public boolean hasFreeCpuSlot() {
        for (ECOCraftingCPU cpu : cpus) {
            if (cpu == null) {
                return true;
            }
        }
        return false;
    }

    /** Expands the CPU slots exposed by this core for the controller's field-generator multiplier. */
    public void ensureCpuCapacity(int multiplier) {
        int safeMultiplier = Math.max(1, multiplier);
        long requested = (long) tier.getCPUThreads() * safeMultiplier;
        int target = requested >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) requested;
        ensureCpuCapacityTo(target);
    }

    public boolean isWorking() {
        for (ECOCraftingCPU cpu : cpus) {
            if (cpu == null) continue;
            return true;
        }
        return false;
    }

    /**
     * Restore CPUs from deferred NBT. Idempotent: only processes each slot once.
     * Returns number of CPUs successfully restored.
     */
    public int restoreDeferredCpus(NEComputationCluster cluster) {
        if (cluster == null) {
            return 0;
        }
        HolderLookup.Provider registries = this.level != null
                ? this.level.registryAccess()
                : (ServerLifecycleHooks.getCurrentServer() != null
                        ? ServerLifecycleHooks.getCurrentServer().registryAccess()
                        : null);
        if (registries == null) {
            LOGGER.warn("Cannot restore deferred ECO CPUs: registries unavailable. pos={}", worldPosition);
            return 0;
        }
        int restored = 0;
        for (int i = 0; i < deferredInit.length; i++) {
            CompoundTag tag = deferredInit[i];
            if (tag == null) {
                continue;
            }
            LOGGER.debug("Restoring ECO CPU from deferredInit[{}]. pos={}", i, worldPosition);
            ECOCraftingCPU cpu = new ECOCraftingCPU(cluster, null, this);
            try {
                cpu.readFromNBT(tag, registries);
            } catch (RuntimeException e) {
                LOGGER.error("Failed to read ECO CPU NBT from deferredInit[{}]. pos={}", i, worldPosition, e);
                continue; // Keep deferredInit[i] for retry
            }
            if (cpu.getPlan() != null && cpu.getLogic().hasJob()) {
                cpus[i] = cpu;
                cluster.pickup(cpu.getPlan(), cpu);
                deferredInit[i] = null; // Only clear on success
                restored++;
                LOGGER.debug(
                        "Restored ECO CPU slot {} with job. pos={} plan={}",
                        i,
                        worldPosition,
                        cpu.getPlan().finalOutput());
            } else if (cpu.getPlan() != null) {
                LOGGER.warn(
                        "ECO CPU slot {} has plan but no job; keeping deferredInit for retry. pos={}",
                        i,
                        worldPosition);
            } else {
                LOGGER.debug("ECO CPU slot {} has no plan; keeping deferredInit for retry. pos={}", i, worldPosition);
            }
        }
        // Count remaining deferred slots for diagnostic visibility
        int remainingDeferred = 0;
        for (CompoundTag tag : deferredInit) {
            if (tag != null) remainingDeferred++;
        }
        if (restored > 0 || remainingDeferred > 0) {
            LOGGER.debug(
                    "restoreDeferredCpus complete: restored={} remainingDeferred={} pos={}",
                    restored,
                    remainingDeferred,
                    worldPosition);
        }
        if (restored > 0) {
            // Ensure the restored CPU state is persisted to disk immediately
            markForUpdate();
            saveChanges();
        }
        return restored;
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        HolderLookup.Provider registries = this.level != null
                ? this.level.registryAccess()
                : (ServerLifecycleHooks.getCurrentServer() != null
                        ? ServerLifecycleHooks.getCurrentServer().registryAccess()
                        : null);
        if (registries == null) {
            // Even if registries unavailable, preserve deferred NBT tags so they aren't
            // wiped.
            int preserved = preserveDeferredTags(data);
            LOGGER.warn(
                    "Cannot save ECO CPUs: registries unavailable. preservedDeferred={} pos={}",
                    preserved,
                    worldPosition);
            return;
        }
        data.putInt("cpuCapacity", cpus.length);
        int saved = 0;
        int preserved = 0;
        for (int i = 0; i < cpus.length; i++) {
            ECOCraftingCPU cpu = cpus[i];
            if (cpu != null) {
                CompoundTag tag = new CompoundTag();
                cpu.writeToNBT(tag, registries);
                data.put("CPU" + i, tag);
                saved++;
            } else if (deferredInit[i] != null) {
                // CRITICAL: Preserve deferred NBT that hasn't been restored yet.
                // Without this, a save before restoreDeferredCpus succeeds would
                // permanently wipe the CPU data from disk.
                data.put("CPU" + i, deferredInit[i].copy());
                preserved++;
            }
        }
        if (saved > 0 || preserved > 0) {
            LOGGER.debug("Saved ECO CPU NBT: active={} deferredPreserved={} pos={}", saved, preserved, worldPosition);
        }
    }

    /**
     * Fallback: write any deferredInit tags directly to NBT when registries are
     * unavailable, to prevent silent data loss.
     */
    private int preserveDeferredTags(CompoundTag data) {
        int preserved = 0;
        for (int i = 0; i < deferredInit.length; i++) {
            if (deferredInit[i] != null && !data.contains("CPU" + i)) {
                data.put("CPU" + i, deferredInit[i].copy());
                preserved++;
            }
        }
        return preserved;
    }

    @Override
    public void updateCluster(@Nullable NEComputationCluster cluster) {
        super.updateCluster(cluster);
        if (cluster != null) {
            int restored = restoreDeferredCpus(cluster);
            if (restored > 0) {
                cluster.restoreActiveCpusFromThreadingCores();
                cluster.updateGridForChangedCpu(cluster);
            }
        }
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        int savedCapacity = Math.max(tier.getCPUThreads(), data.getInt("cpuCapacity"));
        for (String key : data.getAllKeys()) {
            if (!key.startsWith("CPU")) {
                continue;
            }
            try {
                savedCapacity = Math.max(savedCapacity, Integer.parseInt(key.substring(3)) + 1);
            } catch (NumberFormatException ignored) {
                // Ignore unrelated keys with a CPU prefix.
            }
        }
        ensureCpuCapacityTo(savedCapacity);
        for (int i = 0; i < cpus.length; i++) {
            if (data.contains("CPU" + i)) {
                deferredInit[i] = data.getCompound("CPU" + i);
            }
        }
        markForUpdate();
    }

    private void ensureCpuCapacityTo(int target) {
        if (target <= cpus.length) {
            return;
        }
        cpus = Arrays.copyOf(cpus, target);
        deferredInit = Arrays.copyOf(deferredInit, target);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (int i = 0; i < cpus.length; i++) {
            KeyCounter owned = new KeyCounter();
            if (cpus[i] != null) {
                cpus[i].getLogic().getOwnedItems(owned);
            } else if (deferredInit[i] != null) {
                collectDeferredOwnedItems(deferredInit[i], owned);
            }
            addOwnedDrops(owned, drops, level, pos);
        }
    }

    private static void collectDeferredOwnedItems(CompoundTag cpuTag, KeyCounter out) {
        ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
        inventory.readFromNBT(cpuTag.getList("inventory", Tag.TAG_COMPOUND));
        out.addAll(inventory.list);

        CompoundTag jobTag = cpuTag.getCompound("job");
        long buffered = Math.max(0L, jobTag.getLong("bufferedFinalOutput"));
        if (buffered <= 0L) {
            return;
        }
        try {
            GenericStack finalOutput = GenericStack.readTag(jobTag.getCompound("finalOutput"));
            if (finalOutput != null) {
                out.add(finalOutput.what(), buffered);
            }
        } catch (RuntimeException e) {
            LOGGER.error("Unable to decode buffered output from deferred ECO crafting CPU", e);
        }
    }

    private static void addOwnedDrops(KeyCounter owned, List<ItemStack> drops, Level level, BlockPos pos) {
        for (Object2LongMap.Entry<AEKey> entry : owned) {
            if (entry.getKey() instanceof AEItemKey itemKey) {
                long amount = entry.getLongValue();
                while (amount > 0L) {
                    long taken = Math.min(amount, itemKey.getMaxStackSize());
                    amount -= taken;
                    drops.add(itemKey.toStack((int) taken));
                }
            } else {
                entry.getKey().addDrops(entry.getLongValue(), drops, level, pos);
            }
        }
    }

    public void dropCpuInventory(ECOCraftingCPU cpu) {
        if (this.level == null || this.level.isClientSide || cpu == null || !cpu.hasRemainingItems()) {
            return;
        }

        List<ItemStack> drops = new ArrayList<>();
        addCpuInventoryDrops(cpu, this.level, this.worldPosition, drops);
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                Block.popResource(this.level, this.worldPosition, drop);
            }
        }
        if (!drops.isEmpty()) {
            cpu.getLogic().getInventory().clear();
            markForUpdate();
            saveChanges();
        }
    }

    public int clearDeferredCpuData() {
        int cleared = 0;
        for (int i = 0; i < deferredInit.length; i++) {
            if (deferredInit[i] != null) {
                deferredInit[i] = null;
                cleared++;
            }
        }
        if (cleared > 0) {
            markForUpdate();
            saveChanges();
        }
        return cleared;
    }

    private static void addCpuInventoryDrops(ECOCraftingCPU cpu, Level level, BlockPos pos, List<ItemStack> drops) {
        KeyCounter owned = new KeyCounter();
        cpu.getLogic().getOwnedItems(owned);
        addOwnedDrops(owned, drops, level, pos);
    }

    public void deactivate(ECOCraftingCPU cpu) {
        for (int i = 0; i < cpus.length; i++) {
            if (cpus[i] == cpu) {
                cpus[i] = null;
            }
        }
        markForUpdate();
    }
}
