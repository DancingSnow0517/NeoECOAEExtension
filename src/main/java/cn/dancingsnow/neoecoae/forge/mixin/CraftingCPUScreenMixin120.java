package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftingCPUScreen.class, remap = false)
public abstract class CraftingCPUScreenMixin120 {
    @Unique private static final long NEOECOAE_ACTIVE_HOLD_TICKS =
            Math.max(0L, Long.getLong("neoecoae.craftingStatusActiveHoldTicks", 10L));

    @Shadow
    private CraftingStatus status;

    @Unique private final Map<Long, Long> neoecoae$heldZeroSinceTicks = new HashMap<>();
    @Unique private final Map<Long, CraftingStatusEntry> neoecoae$heldZeroEntries = new HashMap<>();

    @ModifyVariable(method = "postUpdate", at = @At("HEAD"), argsOnly = true)
    private CraftingStatus neoecoae$smoothActiveAmount(CraftingStatus update) {
        if (neoecoae$isTerminalUpdate(update)) {
            this.neoecoae$clearHeldActiveAmounts();
            return update;
        }

        if (NEOECOAE_ACTIVE_HOLD_TICKS <= 0 || this.status == null || update.isFullStatus()) {
            this.neoecoae$clearHeldActiveAmounts();
            return update;
        }

        Map<Long, CraftingStatusEntry> previousEntries = new HashMap<>();
        for (CraftingStatusEntry entry : this.status.getEntries()) {
            previousEntries.put(entry.getSerial(), entry);
        }

        long tick = neoecoae$currentClientTick();
        boolean changed = false;
        ImmutableList.Builder<CraftingStatusEntry> entries = ImmutableList.builder();
        Set<Long> updatedSerials = new HashSet<>();

        for (CraftingStatusEntry entry : update.getEntries()) {
            long serial = entry.getSerial();
            updatedSerials.add(serial);
            CraftingStatusEntry previous = previousEntries.get(serial);
            if (previous != null && previous.getActiveAmount() > 0 && entry.getActiveAmount() == 0) {
                Long zeroSinceTick = this.neoecoae$heldZeroSinceTicks.get(serial);
                if (zeroSinceTick == null) {
                    zeroSinceTick = tick;
                    this.neoecoae$heldZeroSinceTicks.put(serial, zeroSinceTick);
                }
                this.neoecoae$heldZeroEntries.put(serial, entry);

                if (tick - zeroSinceTick <= NEOECOAE_ACTIVE_HOLD_TICKS) {
                    entries.add(new CraftingStatusEntry(
                            serial,
                            entry.getWhat(),
                            entry.getStoredAmount(),
                            previous.getActiveAmount(),
                            entry.getPendingAmount()));
                    changed = true;
                    continue;
                }
            } else if (entry.getActiveAmount() > 0) {
                this.neoecoae$heldZeroSinceTicks.remove(serial);
                this.neoecoae$heldZeroEntries.remove(serial);
            }

            entries.add(entry);
        }

        Iterator<Long> heldSerials = this.neoecoae$heldZeroSinceTicks.keySet().iterator();
        while (heldSerials.hasNext()) {
            Long serial = heldSerials.next();
            if (!previousEntries.containsKey(serial)) {
                heldSerials.remove();
                this.neoecoae$heldZeroEntries.remove(serial);
            }
        }

        if (!changed) {
            return update;
        }

        return new CraftingStatus(
                update.isFullStatus(),
                update.getElapsedTime(),
                update.getRemainingItemCount(),
                update.getStartItemCount(),
                entries.build());
    }

    @Inject(method = "postUpdate", at = @At("RETURN"))
    private void neoecoae$expireHeldActiveAmounts(CraftingStatus update, CallbackInfo ci) {
        if (this.status == null || this.neoecoae$heldZeroSinceTicks.isEmpty()) {
            return;
        }

        long tick = neoecoae$currentClientTick();
        ArrayList<CraftingStatusEntry> entries = new ArrayList<>(this.status.getEntries());
        boolean changed = false;
        Set<Long> expiredSerials = new HashSet<>();

        for (Map.Entry<Long, Long> heldEntry : this.neoecoae$heldZeroSinceTicks.entrySet()) {
            if (tick - heldEntry.getValue() > NEOECOAE_ACTIVE_HOLD_TICKS) {
                expiredSerials.add(heldEntry.getKey());
            }
        }

        if (expiredSerials.isEmpty()) {
            return;
        }

        for (Long serial : expiredSerials) {
            this.neoecoae$heldZeroSinceTicks.remove(serial);
            CraftingStatusEntry zeroEntry = this.neoecoae$heldZeroEntries.remove(serial);
            if (zeroEntry == null) {
                continue;
            }

            for (int i = 0; i < entries.size(); i++) {
                CraftingStatusEntry current = entries.get(i);
                if (current.getSerial() != serial) {
                    continue;
                }

                if (zeroEntry.isDeleted()) {
                    entries.remove(i);
                } else {
                    entries.set(i, new CraftingStatusEntry(
                            serial,
                            current.getWhat(),
                            zeroEntry.getStoredAmount(),
                            zeroEntry.getActiveAmount(),
                            zeroEntry.getPendingAmount()));
                }
                changed = true;
                break;
            }
        }

        if (!changed) {
            return;
        }

        Collections.sort(entries);
        this.status = new CraftingStatus(
                true,
                this.status.getElapsedTime(),
                this.status.getRemainingItemCount(),
                this.status.getStartItemCount(),
                entries);
    }

    @Unique private static long neoecoae$currentClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null ? minecraft.level.getGameTime() : 0L;
    }

    @Unique private static boolean neoecoae$isTerminalUpdate(CraftingStatus update) {
        return update.getStartItemCount() == 0 && update.getRemainingItemCount() == 0;
    }

    @Unique private void neoecoae$clearHeldActiveAmounts() {
        this.neoecoae$heldZeroSinceTicks.clear();
        this.neoecoae$heldZeroEntries.clear();
    }
}
