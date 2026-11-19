package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.core.network.ClientboundPacket;
import appeng.core.network.clientbound.PatternAccessTerminalPacket;
import appeng.menu.implementations.PatternAccessTermMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

@Mixin(PatternAccessTermMenu.class)
public abstract class PatternAccessTermMenuMixin {
    @Unique
    private static final int NEOECOAE_MAX_PATTERN_SLOTS_PER_PACKET = 128;

    @WrapOperation(method = {"sendFullUpdate", "sendIncrementalUpdate"}, at = @At(value = "INVOKE",
        target = "Lappeng/menu/implementations/PatternAccessTermMenu;sendPacketToClient(Lappeng/core/network/ClientboundPacket;)V"))
    private void neoecoae$sendPatternBatches(PatternAccessTermMenu menu, ClientboundPacket payload,
                                            Operation<Void> original) {
        if (!(payload instanceof PatternAccessTerminalPacket packet)
            || packet.slots().size() <= NEOECOAE_MAX_PATTERN_SLOTS_PER_PACKET
            || !(menu.getPlayer() instanceof ServerPlayer player)) {
            original.call(menu, payload);
            return;
        }

        var batches = neoecoae$splitPatternUpdate(packet);
        PacketDistributor.sendToPlayer(player, batches[0], Arrays.copyOfRange(batches, 1, batches.length));
    }

    @Unique
    private static PatternAccessTerminalPacket[] neoecoae$splitPatternUpdate(PatternAccessTerminalPacket packet) {
        int remaining = packet.slots().size();
        var batches = new PatternAccessTerminalPacket[Math.ceilDiv(remaining, NEOECOAE_MAX_PATTERN_SLOTS_PER_PACKET)];
        var entries = packet.slots().int2ObjectEntrySet().iterator();
        for (int index = 0; index < batches.length; index++) {
            int batchSize = Math.min(remaining, NEOECOAE_MAX_PATTERN_SLOTS_PER_PACKET);
            var slots = new Int2ObjectArrayMap<ItemStack>(batchSize);
            for (int slotIndex = 0; slotIndex < batchSize; slotIndex++) {
                var entry = entries.next();
                slots.put(entry.getIntKey(), entry.getValue());
            }
            batches[index] = index == 0 && packet.fullUpdate()
                ? PatternAccessTerminalPacket.fullUpdate(packet.inventoryId(), packet.inventorySize(), packet.sortBy(),
                    packet.group(), slots)
                : PatternAccessTerminalPacket.incrementalUpdate(packet.inventoryId(), slots);
            remaining -= batchSize;
        }
        return batches;
    }
}
