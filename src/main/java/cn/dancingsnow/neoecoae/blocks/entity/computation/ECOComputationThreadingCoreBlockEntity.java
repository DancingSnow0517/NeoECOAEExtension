package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ECOComputationThreadingCoreBlockEntity extends AbstractComputationBlockEntity<ECOComputationThreadingCoreBlockEntity> {
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
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        var registries = level != null ? level.registryAccess() : ServerLifecycleHooks.getCurrentServer().registryAccess();
        for (int i = 0; i < cpus.length; i++) {
            ECOCraftingCPU cpu = cpus[i];
            if (cpu != null) {
                CompoundTag tag = new CompoundTag();
                cpu.writeToNBT(tag, registries);
                data.store("CPU" + i, CompoundTag.CODEC, tag);
            } else if (deferredInit[i] != null) {
                // 还未被 updateCluster 消费的存盘数据（区块在多方块成型前被重新加载又存盘的情况）：
                // 原样写回，否则只写 cpus[] 会把这份数据从存档里抹掉，导致重进世界后任务丢失。
                data.store("CPU" + i, CompoundTag.CODEC, deferredInit[i]);
            }
        }
    }

    @Override
    public void updateCluster(@Nullable NEComputationCluster cluster) {
        super.updateCluster(cluster);
        if (cluster != null) {
            // 成型：从存盘数据反序列化出 CPU 放进 cpus[] 并注册到集群
            for (int i = 0; i < deferredInit.length; i++) {
                CompoundTag tag = deferredInit[i];
                if (tag != null) {
                    ECOCraftingCPU cpu = new ECOCraftingCPU(cluster, null, this);
                    deferredInit[i] = null;
                    cpu.readFromNBT(tag, level.registryAccess());
                    if (cpu.getPlan() != null) {
                        cpus[i] = cpu;
                        cluster.pickup(cpu.getPlan(), cpu);
                    }
                }
            }
        } else {
            // 不成形（结构被破坏等）：取消所有任务，物品先尝试塞回网络，回不去的掉落到世界
            for (int i = 0; i < cpus.length; i++) {
                ECOCraftingCPU cpu = cpus[i];
                if (cpu != null) {
                    evacuate(cpu);
                    cpus[i] = null;
                }
            }
        }
    }

    /**
     * 取消该 CPU 的合成任务并清空其库存：{@link ECOCraftingCPULogic#cancel()} 会尽量把在制/库存物品
     * 塞回存储网络，网络收不下而残留的物品则掉落到世界中。
     */
    private void evacuate(ECOCraftingCPU cpu) {
        ECOCraftingCPULogic logic = cpu.getLogic();
        // 有任务则取消（cancel 内部会尝试把物品塞回网络）；随后再兜底尝试一次，覆盖无任务但有残留库存的情况
        logic.cancel();
        logic.storeItems();
        if (level == null) {
            return;
        }
        ListCraftingInventory inventory = logic.getInventory();
        List<ItemStack> drops = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : inventory.list) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }
            if (key instanceof AEItemKey itemKey) {
                while (amount > 0) {
                    long taken = Math.min(amount, itemKey.getMaxStackSize());
                    amount -= taken;
                    drops.add(itemKey.toStack((int) taken));
                }
            } else {
                key.addDrops(amount, drops, level, worldPosition);
            }
        }
        for (ItemStack stack : drops) {
            Block.popResource(level, worldPosition, stack);
        }
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        for (int i = 0; i < cpus.length; i++) {
            final int index = i;
            data.read("CPU" + i, CompoundTag.CODEC).ifPresent(tag -> deferredInit[index] = tag);
        }
        markForUpdate();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ECOCraftingCPU cpu : cpus) {
            if (cpu == null) continue;
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
