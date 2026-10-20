package cn.dancingsnow.neoecoae.platform.network;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Loaded only by a clientbound packet handler. */
final class NEClientPacketContext {
    private NEClientPacketContext() {}
    static Player player() { return Minecraft.getInstance().player; }
}
