package cn.dancingsnow.neoecoae.integration.ponder;

import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public class PonderPlatformUtils {
    public static final boolean PONDER_PRESENT;

    static {
        PONDER_PRESENT = ModList.get().isLoaded("ponder");
    }

    public static boolean isPonderLevel(Level level) {
        if (PONDER_PRESENT) {
            return isPonderLevelInternal(level);
        }
        return false;
    }

    private static boolean isPonderLevelInternal(Level level){
        return level instanceof PonderLevel;
    }
}
