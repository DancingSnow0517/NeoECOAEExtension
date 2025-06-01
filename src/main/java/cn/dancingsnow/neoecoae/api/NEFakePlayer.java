package cn.dancingsnow.neoecoae.api;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.UUID;

public class NEFakePlayer {
    private static final UUID DEFAULT_UUID = UUID.fromString("90892e60-608b-4b30-9b4a-73ebbb2594e8");

    private static Player fakePlayer = null;

    public static Player getFakePlayer(ServerLevel level) {
        if (fakePlayer != null) {
            return fakePlayer;
        }
        fakePlayer = FakePlayerFactory.get(level, new GameProfile(DEFAULT_UUID, "[ECO]"));
        return fakePlayer;
    }
}
