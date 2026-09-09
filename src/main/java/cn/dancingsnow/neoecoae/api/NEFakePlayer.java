package cn.dancingsnow.neoecoae.api;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.UUID;

public final class NEFakePlayer {
    private static final UUID DEFAULT_UUID = UUID.fromString("90892e60-608b-4b30-9b4a-73ebbb2594e8");
    private static final GameProfile PROFILE = new GameProfile(DEFAULT_UUID, "[ECO]");

    private NEFakePlayer() {
    }

    public static Player getFakePlayer(ServerLevel level) {
        return FakePlayerFactory.get(level, PROFILE);
    }
}
