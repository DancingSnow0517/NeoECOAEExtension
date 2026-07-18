package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class ECOComputationThreadingCoreBlockEntity extends AbstractComputationBlockEntity<ECOComputationThreadingCoreBlockEntity> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOComputationThreadingCoreBlockEntity.class);
    @Getter
    private final IECOTier tier;
    @Getter
    private final ECOCraftingCPU[] cpus;
    private final CompoundTag[] deferredInit;

    public ECOComputationThreadingCoreBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
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
            if (cpu == null) continue;
            return true;
        }
        return false;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        for (int i = 0; i < cpus.length; i++) {
            ECOCraftingCPU cpu = cpus[i];
            if (cpu != null) {
                CompoundTag tag = new CompoundTag();
                cpu.writeToNBT(tag, registries);
                data.put("CPU" + i, tag);
            } else if (deferredInit[i] != null) {
                data.put("CPU" + i, deferredInit[i].copy());
            }
        }
    }

    @Override
    public void updateCluster(@Nullable NEComputationCluster cluster) {
        NEComputationCluster previous = this.cluster;
        if (cluster == null && previous != null) {
            HolderLookup.Provider registries = level != null ? level.registryAccess() : null;
            if (registries != null) {
                for (int i = 0; i < cpus.length; i++) {
                    ECOCraftingCPU cpu = cpus[i];
                    if (cpu == null) {
                        continue;
                    }
                    CompoundTag tag = new CompoundTag();
                    cpu.writeToNBT(tag, registries);
                    deferredInit[i] = tag;
                    previous.deactivate(cpu.getPlan());
                    cpus[i] = null;
                }
                setChanged();
            }
        }
        super.updateCluster(cluster);
        if (cluster != null) {
            for (int i = 0; i < deferredInit.length; i++) {
                CompoundTag tag = deferredInit[i];
                if (tag != null) {
                    HolderLookup.Provider registries = level != null ? level.registryAccess() : null;
                    if (registries == null) {
                        continue;
                    }
                    ECOCraftingCPU cpu = new ECOCraftingCPU(cluster, null, this);
                    try {
                        cpu.readFromNBT(tag, registries);
                        if (cpu.getPlan() != null) {
                            cpus[i] = cpu;
                            deferredInit[i] = null;
                            cluster.pickup(cpu.getPlan(), cpu);
                        } else {
                            LOGGER.error("Deferred ECO crafting CPU at {} has no valid plan; keeping it quarantined", worldPosition);
                        }
                    } catch (RuntimeException e) {
                        LOGGER.error("Unable to restore deferred ECO crafting CPU at {}; keeping its data", worldPosition, e);
                    }
                }
            }
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        Arrays.fill(cpus, null);
        Arrays.fill(deferredInit, null);
        for (int i = 0; i < cpus.length; i++) {
            if (data.contains("CPU" + i)) {
                deferredInit[i] = data.getCompound("CPU" + i);
            }
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        HolderLookup.Provider registries = level.registryAccess();
        for (int i = 0; i < cpus.length; i++) {
            KeyCounter owned = new KeyCounter();
            ECOCraftingCPU cpu = cpus[i];
            if (cpu != null) {
                cpu.getLogic().getOwnedItems(owned);
            } else if (deferredInit[i] != null) {
                collectDeferredOwnedItems(deferredInit[i], registries, owned);
            }
            addOwnedDrops(owned, drops, level, pos);
        }
    }

    private static void collectDeferredOwnedItems(
        CompoundTag cpuTag,
        HolderLookup.Provider registries,
        KeyCounter out
    ) {
        ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
        inventory.readFromNBT(cpuTag.getList("inventory", Tag.TAG_COMPOUND), registries);
        out.addAll(inventory.list);

        CompoundTag jobTag = cpuTag.getCompound("job");
        long buffered = Math.max(0L, jobTag.getLong("bufferedFinalOutput"));
        if (buffered <= 0L) {
            return;
        }
        try {
            GenericStack finalOutput = GenericStack.readTag(registries, jobTag.getCompound("finalOutput"));
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
                    int taken = (int) Math.min(amount, itemKey.getMaxStackSize());
                    amount -= taken;
                    drops.add(itemKey.toStack(taken));
                }
            } else {
                entry.getKey().addDrops(entry.getLongValue(), drops, level, pos);
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
