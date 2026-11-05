package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Wait for the player and registry synchronization, then close this dedicated acceptance client. */
@EventBusSubscriber(modid = "neoecoae", value = Dist.CLIENT)
public final class KeyReuseClientAcceptance {
    private static int ticks;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("neoecoae.keyReuseAcceptance")) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || ++ticks != 40) return;
        if (!Files.readString(Path.of("key-reuse-acceptance-client.txt")).startsWith("PASS"))
            throw new AssertionError("integrated server acceptance failed");
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        if (ECOKeyFactory.item(stack) != ECOKeyFactory.item(stack))
            throw new AssertionError("client reuse disabled after world join");
        Files.writeString(Path.of("key-reuse-client-world.txt"), "PASS: player joined, registry sync completed, client factory reused\n");
        System.out.println("[KEY_REUSE_CLIENT_WORLD] PASS");
        client.stop();
    }
}
