package cn.dancingsnow.neoecoae.mixins;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.network.ECOCpuOverlayPayload;
import cn.dancingsnow.neoecoae.network.ECOPlannerNetwork;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Publishes the serial-to-overlay mapping through NeoECOAE's own network channel instead of
 * appending it to AE2's GuiSync frame for the CPU list. AE2's GUI sync stream carries no
 * per-field length prefix, so foreign bytes inside a frame desynchronize the whole packet
 * whenever reader and writer disagree; keeping this data out-of-band makes the CPU list
 * immune to that failure mode regardless of which other addons transform AE2's classes.
 */
@Mixin(value = CraftingStatusMenu.class, remap = false)
public abstract class CraftingStatusMenuMixin extends CraftingCPUMenu {
    @Shadow
    @Final
    private WeakHashMap<ICraftingCPU, Integer> cpuSerialMap;

    @Shadow
    @Final
    private ImmutableSet<ICraftingCPU> lastCpuSet;

    @Unique private Map<Integer, String> neoecoae$lastSentOverlays = Map.of();

    public CraftingStatusMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "createCpuList", at = @At("RETURN"))
    private void neoecoae$syncCpuOverlays(CallbackInfoReturnable<CraftingStatusMenu.CraftingCpuList> cir) {
        // createCpuList sorts entries after constructing them. Resolve overlays from the stable
        // CPU serial after that sort so unrelated CPUs cannot shift a badge to another row.
        Map<Integer, String> overlays = null;
        for (var entry : cir.getReturnValue().cpus()) {
            for (var cpu : lastCpuSet) {
                if (cpuSerialMap.getOrDefault(cpu, -1) != entry.serial()) {
                    continue;
                }
                if (cpu instanceof ECOCraftingCPU ecoCpu) {
                    if (overlays == null) {
                        overlays = new HashMap<>();
                    }
                    overlays.put(
                            entry.serial(),
                            ecoCpu.getTier().getCPUOverlayTexture().toString());
                }
                break;
            }
        }

        Map<Integer, String> next = overlays != null ? Map.copyOf(overlays) : Map.of();
        if (next.equals(neoecoae$lastSentOverlays)) {
            return;
        }
        neoecoae$lastSentOverlays = next;

        // Each open menu instance has exactly one viewer, matching AE2's own sendPacketToClient.
        // The overlay packet is enqueued before AE2's periodic GuiDataSyncPacket below, so the
        // client cache is always populated by the time the matching CPU list arrives.
        if (getPlayer() instanceof ServerPlayer serverPlayer) {
            ECOPlannerNetwork.sendToPlayer(serverPlayer, new ECOCpuOverlayPayload(this.containerId, next));
        }
    }
}
