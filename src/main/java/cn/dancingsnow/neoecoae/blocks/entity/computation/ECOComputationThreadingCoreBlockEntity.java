package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ECOComputationThreadingCoreBlockEntity
        extends AbstractComputationBlockEntity<ECOComputationThreadingCoreBlockEntity> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    @Getter
    private final IECOTier tier;
    @Getter
    private final ECOCraftingCPU[] cpus;
    private final CompoundTag[] deferredInit;

    public ECOComputationThreadingCoreBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState blockState,
            IECOTier tier) {
        super(type, pos, blockState);
        this.tier = tier;
        cpus = new ECOCraftingCPU[tier.getCPUThreads()];
        deferredInit = new CompoundTag[tier.getCPUThreads()];
    }

    @Nullable
    public ECOCraftingCPU spawn(ICraftingPlan plan) {
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

    public boolean isWorking() {
        for (ECOCraftingCPU cpu : cpus) {
            if (cpu == null)
                continue;
            return true;
        }
        return false;
    }

    /**
     * Restore CPUs from deferred NBT. Idempotent — only processes each slot once.
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
            LOGGER.warn("Cannot restore deferred ECO CPUs — registries unavailable. pos={}", worldPosition);
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
            cpu.readFromNBT(tag, registries);
            deferredInit[i] = null;
            if (cpu.getPlan() != null && cpu.getLogic().hasJob()) {
                cpus[i] = cpu;
                cluster.pickup(cpu.getPlan(), cpu);
                restored++;
                LOGGER.info("Restored ECO CPU slot {} with job. pos={} plan={}",
                        i, worldPosition, cpu.getPlan().finalOutput());
            } else if (cpu.getPlan() != null) {
                LOGGER.warn("ECO CPU slot {} has plan but no job — skipping restore. pos={}", i, worldPosition);
            } else {
                LOGGER.debug("ECO CPU slot {} has no plan — skipping restore. pos={}", i, worldPosition);
            }
        }
        return restored;
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        HolderLookup.Provider registries = this.level != null
                ? this.level.registryAccess()
                : ServerLifecycleHooks.getCurrentServer().registryAccess();
        int saved = 0;
        for (int i = 0; i < cpus.length; i++) {
            ECOCraftingCPU cpu = cpus[i];
            if (cpu != null) {
                CompoundTag tag = new CompoundTag();
                cpu.writeToNBT(tag, registries);
                data.put("CPU" + i, tag);
                saved++;
            }
        }
        if (saved > 0) {
            LOGGER.debug("Saved {} ECO CPU(s) to NBT. pos={}", saved, worldPosition);
        }
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
        int loaded = 0;
        for (int i = 0; i < cpus.length; i++) {
            if (data.contains("CPU" + i)) {
                deferredInit[i] = data.getCompound("CPU" + i);
                loaded++;
            }
        }
        if (loaded > 0) {
            LOGGER.debug("Loaded {} ECO CPU NBT tags into deferredInit. pos={}", loaded, worldPosition);
        }
        markForUpdate();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ECOCraftingCPU cpu : cpus) {
            if (cpu == null)
                continue;
            ListCraftingInventory inventory = cpu.getLogic().getInventory();
            for (Object2LongMap.Entry<AEKey> entry : inventory.list) {
                if (entry.getKey() instanceof AEItemKey itemKey) {
                    long amount = entry.getLongValue();
                    while (amount > 0) {
                        long taken = Math.min(amount, itemKey.getMaxStackSize());
                        amount -= taken;
                        drops.add(itemKey.toStack((int) taken));
                    }
                    continue;
                }
                entry.getKey().addDrops(entry.getLongValue(), drops, level, worldPosition);
            }
        }
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
