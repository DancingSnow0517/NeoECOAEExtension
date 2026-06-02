package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
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

import java.util.List;

public class ECOComputationThreadingCoreBlockEntity
        extends AbstractComputationBlockEntity<ECOComputationThreadingCoreBlockEntity> {
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

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        HolderLookup.Provider registries = this.level != null
                ? this.level.registryAccess()
                : ServerLifecycleHooks.getCurrentServer().registryAccess();
        for (int i = 0; i < cpus.length; i++) {
            ECOCraftingCPU cpu = cpus[i];
            if (cpu != null) {
                CompoundTag tag = new CompoundTag();
                cpu.writeToNBT(tag, registries);
                data.put("CPU" + i, tag);
            }
        }
    }

    @Override
    public void updateCluster(@Nullable NEComputationCluster cluster) {
        super.updateCluster(cluster);
        if (cluster != null) {
            int restored = 0;
            HolderLookup.Provider registries = ServerLifecycleHooks.getCurrentServer() != null
                    ? ServerLifecycleHooks.getCurrentServer().registryAccess()
                    : null;
            if (registries == null) {
                return;
            }
            for (int i = 0; i < deferredInit.length; i++) {
                CompoundTag tag = deferredInit[i];
                if (tag != null) {
                    ECOCraftingCPU cpu = new ECOCraftingCPU(cluster, null, this);
                    deferredInit[i] = null;
                    cpu.readFromNBT(tag, registries);
                    if (cpu.getPlan() != null && cpu.getLogic().hasJob()) {
                        cpus[i] = cpu;
                        cluster.pickup(cpu.getPlan(), cpu);
                        restored++;
                    }
                }
            }
            if (restored > 0) {
                cluster.restoreActiveCpusFromThreadingCores();
                cluster.updateGridForChangedCpu(cluster);
            }
        }
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        for (int i = 0; i < cpus.length; i++) {
            if (data.contains("CPU" + i)) {
                deferredInit[i] = data.getCompound("CPU" + i);
            }
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
