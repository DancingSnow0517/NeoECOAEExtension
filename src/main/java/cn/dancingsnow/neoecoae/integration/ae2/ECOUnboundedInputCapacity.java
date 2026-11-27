package cn.dancingsnow.neoecoae.integration.ae2;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.capabilities.Capabilities;
import appeng.helpers.patternprovider.PatternProviderTarget;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Reads native capacity declarations without insertion probes or a required AE2LT dependency. */
public final class ECOUnboundedInputCapacity {
    private ECOUnboundedInputCapacity() {}

    public static boolean forProvider(Object logic, KeyCounter[] inputs) {
        if (!(logic instanceof PatternProviderLogicAccessor accessor)) return false;
        var host = accessor.neoecoae$getHost().getBlockEntity();
        if (!(host.getLevel() instanceof ServerLevel level)) return false;
        boolean found = false;
        // AE2 owns round-robin selection. Every selectable target must declare unlimited inputs.
        for (var direction : accessor.neoecoae$getActiveSides()) {
            var pos = host.getBlockPos().relative(direction);
            var face = direction.getOpposite();
            if (!level.isLoaded(pos)) continue;
            var be = level.getBlockEntity(pos);
            var machine = ICraftingMachine.of(level, pos, face, be);
            if (machine != null && machine.acceptsPlans()) return false;
            if (PatternProviderTarget.get(level, pos, be, face, IActionSource.empty()) == null) continue;
            if (!forTarget(level, pos, face, inputs)) return false;
            found = true;
        }
        return found;
    }

    public static boolean forTarget(ServerLevel level, BlockPos pos, Direction face, KeyCounter[] inputs) {
        if (!level.isLoaded(pos)) return false;
        var be = level.getBlockEntity(pos);
        if (be == null || ICraftingMachine.of(level, pos, face, be) != null) return false;
        return declared(be.getCapability(Capabilities.STORAGE, face).orElse(null), inputs);
    }

    static boolean declared(@Nullable MEStorage storage, KeyCounter[] inputs) {
        if (!(storage instanceof GenericInternalInventory inventory) || !inventory.canInsert()) return false;
        var assigned = new HashSet<Integer>();
        var keys = new HashSet<AEKey>();
        for (var counter : inputs) for (var entry : counter) {
            if (entry.getLongValue() <= 0L || !keys.add(entry.getKey())) continue;
            var key = entry.getKey();
            if (inventory.getMaxAmount(key) != Long.MAX_VALUE || !inventory.isAllowed(key)) return false;
            int selected = -1;
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (assigned.contains(slot)) continue;
                var stored = inventory.getKey(slot);
                if (key.equals(stored)) { selected = slot; break; }
                if (stored == null && selected < 0) selected = slot;
            }
            if (selected < 0) return false;
            assigned.add(selected);
        }
        return !keys.isEmpty();
    }
}
