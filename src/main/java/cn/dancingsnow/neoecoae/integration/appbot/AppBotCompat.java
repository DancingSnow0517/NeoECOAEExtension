package cn.dancingsnow.neoecoae.integration.appbot;

import appbot.ae2.ManaKeyType;
import appeng.api.stacks.AEKeyType;

/**
 * Safe accessor for Applied Botanics API types.
 *
 * <p>Only call from the {@code appbot} optional integration boundary.
 */
public final class AppBotCompat {
    private AppBotCompat() {}

    public static AEKeyType getManaKeyType() {
        return ManaKeyType.TYPE;
    }
}
