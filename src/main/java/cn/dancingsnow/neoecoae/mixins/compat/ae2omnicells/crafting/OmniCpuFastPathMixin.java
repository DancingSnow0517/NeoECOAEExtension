package cn.dancingsnow.neoecoae.mixins.compat.ae2omnicells.crafting;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOExternalCpuFastPath;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class OmniCpuFastPathMixin {
    @Shadow @Final private ListCraftingInventory inventory;
    @Shadow @Final CraftingCPUCluster cluster;
    @Shadow private ExecutingCraftingJob job;
    @Unique private ECOExternalCpuFastPath neoecoae$fastPath;

    @Unique private ECOExternalCpuFastPath neoecoae$fastPath() {
        if (neoecoae$fastPath == null) neoecoae$fastPath = new ECOExternalCpuFastPath(cluster::markDirty);
        return neoecoae$fastPath;
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true)
    private void neoecoae$dispatch(int maxPatterns, CraftingService crafting, IEnergyService energy,
            Level level, CallbackInfoReturnable<Integer> cir) {
        // These engines own batching and use the provider SPI adapters instead.
        if (ModList.get().isLoaded("thunderbolt") || ModList.get().isLoaded("molecularmanipulator")) return;
        int consumed = neoecoae$fastPath().execute(this, job, inventory, maxPatterns, crafting, energy, level);
        if (consumed > 0) cir.setReturnValue(consumed);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void neoecoae$read(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        neoecoae$fastPath().read(tag.getCompound("neoecoaeFastPath"));
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void neoecoae$write(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (neoecoae$fastPath == null) return;
        var ledger = new CompoundTag();
        neoecoae$fastPath.write(ledger);
        tag.put("neoecoaeFastPath", ledger);
    }
}
