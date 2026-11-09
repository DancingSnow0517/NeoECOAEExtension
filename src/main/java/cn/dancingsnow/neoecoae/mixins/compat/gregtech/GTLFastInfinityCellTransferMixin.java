package cn.dancingsnow.neoecoae.mixins.compat.gregtech;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.storage.persistence.StorageAmounts;
import cn.dancingsnow.neoecoae.storage.persistence.StorageTransferData;
import cn.dancingsnow.neoecoae.storage.transfer.GTLFastInfinityTransfer;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional GTL adapter; the Int128 generic is erased so GTL is not a compile-time dependency. */
@Pseudo
@Mixin(targets = "org.gtlcore.gtlcore.integration.ae2.storage.FastInfinityCellInventory", remap = false)
public abstract class GTLFastInfinityCellTransferMixin implements StorageCell, GTLFastInfinityTransfer.Source {
    @Shadow @Final private ItemStack stack;
    @Shadow @Final private ISaveProvider container;
    @Shadow private double storedItemCount;
    @Shadow private boolean isPersisted;
    @Shadow protected abstract Object2ObjectOpenHashMap<AEKey, Object> getCellItems();

    @Unique private UUID neoecoae$journalDisk;
    @Unique private StorageTransferData neoecoae$journal;

    @Override
    public StorageTransferData neoecoae$transferJournal() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || stack.getTag() == null || !stack.getTag().hasUUID("diskuuid"))
            return null;
        UUID disk = stack.getTag().getUUID("diskuuid");
        if (!disk.equals(neoecoae$journalDisk)) {
            neoecoae$journal = GTLFastInfinityTransfer.journal(server, stack);
            neoecoae$journalDisk = disk;
        }
        return neoecoae$journal;
    }

    @Override
    public StorageAmounts neoecoae$physicalContents() {
        StorageAmounts result = new StorageAmounts();
        getCellItems().forEach((key, value) -> result.set(key, HugeAmount.of(GTLFastInfinityTransfer.amount(value))));
        return result;
    }

    @Override
    public void neoecoae$retireContents(Set<AEKey> keys) {
        // Use GTL's native removal once per resource to update every version's display caches and notifications.
        // Only the already-durable destination owns the removed high bits at this point.
        neoecoae$retiring = true;
        try {
            for (AEKey key : keys) {
                Object counter = getCellItems().get(key);
                if (counter == null) throw new IllegalStateException("Missing sealed GTL resource");
                try {
                    var setter = counter.getClass().getMethod("set", long.class, long.class);
                    long high = (long) counter.getClass().getMethod("getHigh").invoke(counter);
                    long low = (long) counter.getClass().getMethod("getLow").invoke(counter);
                    setter.invoke(counter, 0L, 1L);
                    try {
                        if (extract(key, 1, Actionable.MODULATE, IActionSource.empty()) != 1)
                            throw new IllegalStateException("GTL native removal rejected a sealed resource");
                    } finally {
                        // If native removal failed before deleting the entry, restore its physical quantity.
                        if (getCellItems().get(key) == counter) setter.invoke(counter, high, low);
                    }
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Unsupported GTL Int128 mutation API", failure);
                }
            }
        } finally {
            neoecoae$retiring = false;
            // Recompute rather than subtract doubles: a huge removed stack must not round small survivors to zero.
            storedItemCount = neoecoae$physicalContents().totalExact().doubleValue();
            // Keep the UUID even for an empty source until its migration receipt has been saved.
            if (storedItemCount == 0) storedItemCount = 1;
            isPersisted = false;
        }
        persist();
        if (container != null) container.saveChanges();
    }

    @Unique private boolean neoecoae$retiring;

    @Override
    public void neoecoae$flushContents() {
        persist();
        GTLFastInfinityTransfer.flushSource(ServerLifecycleHooks.getCurrentServer());
    }

    @Inject(method = {"insert", "extract"}, at = @At("HEAD"), cancellable = true)
    private void neoecoae$sealMutations(AEKey key, long amount, Actionable mode, IActionSource source,
            CallbackInfoReturnable<Long> cir) {
        if (!neoecoae$retiring && neoecoae$sealed()) cir.setReturnValue(0L);
    }

    @Inject(method = "getAvailableStacks", at = @At("HEAD"), cancellable = true)
    private void neoecoae$hideSealedSource(KeyCounter out, CallbackInfo ci) {
        if (neoecoae$sealed()) ci.cancel();
    }

    @Inject(method = "persist", at = @At("HEAD"), cancellable = true)
    private void neoecoae$deferRetirementSnapshot(CallbackInfo ci) {
        if (neoecoae$retiring) ci.cancel();
    }
}
