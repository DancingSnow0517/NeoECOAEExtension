package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.core.sync.packets.PatternAccessTerminalPacket;
import cn.dancingsnow.neoecoae.network.PatternAccessTerminalBatches;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.function.Function;

@Mixin(targets = "appeng.menu.implementations.PatternAccessTermMenu$ContainerTracker", remap = false)
public abstract class PatternAccessTermTrackerMixin {
    @Unique private static final int NEOECOAE_SLOTS_PER_PACKET = 128;

    @WrapOperation(method = "createFullPacket", at = @At(value = "INVOKE",
        target = "Lappeng/core/sync/packets/PatternAccessTerminalPacket;fullUpdate(JIJLappeng/api/implementations/blockentities/PatternContainerGroup;Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;)Lappeng/core/sync/packets/PatternAccessTerminalPacket;"))
    private PatternAccessTerminalPacket neoecoae$splitFull(long inventoryId, int inventorySize, long sortBy,
            PatternContainerGroup group, Int2ObjectMap<ItemStack> slots, Operation<PatternAccessTerminalPacket> original) {
        return neoecoae$split(inventoryId, slots, batch -> original.call(inventoryId, inventorySize, sortBy, group, batch));
    }

    @WrapOperation(method = "createUpdatePacket", at = @At(value = "INVOKE",
        target = "Lappeng/core/sync/packets/PatternAccessTerminalPacket;incrementalUpdate(JLit/unimi/dsi/fastutil/ints/Int2ObjectMap;)Lappeng/core/sync/packets/PatternAccessTerminalPacket;"))
    private PatternAccessTerminalPacket neoecoae$splitIncremental(long inventoryId, Int2ObjectMap<ItemStack> slots,
            Operation<PatternAccessTerminalPacket> original) {
        return neoecoae$split(inventoryId, slots, batch -> original.call(inventoryId, batch));
    }

    @Unique
    private static PatternAccessTerminalPacket neoecoae$split(long inventoryId, Int2ObjectMap<ItemStack> slots,
            Function<Int2ObjectMap<ItemStack>, PatternAccessTerminalPacket> firstPacket) {
        if (slots.size() <= NEOECOAE_SLOTS_PER_PACKET) return firstPacket.apply(slots);

        var entries = slots.int2ObjectEntrySet().iterator();
        int remaining = slots.size();
        PatternAccessTerminalPacket first = null;
        var continuation = new ArrayList<PatternAccessTerminalPacket>();
        while (remaining > 0) {
            int size = Math.min(remaining, NEOECOAE_SLOTS_PER_PACKET);
            var batch = new Int2ObjectArrayMap<ItemStack>(size);
            for (int index = 0; index < size; index++) {
                var entry = entries.next();
                batch.put(entry.getIntKey(), entry.getValue());
            }
            if (first == null) first = firstPacket.apply(batch);
            else continuation.add(PatternAccessTerminalPacket.incrementalUpdate(inventoryId, batch));
            remaining -= size;
        }
        ((PatternAccessTerminalBatches) first).neoecoae$setContinuation(continuation);
        return first;
    }
}
