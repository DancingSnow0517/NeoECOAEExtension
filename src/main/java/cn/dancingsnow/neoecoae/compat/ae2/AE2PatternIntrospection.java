package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.pattern.AECraftingPattern;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.mixins.ae2.AECraftingPatternAccessor;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AE2PatternIntrospection {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private static volatile boolean selfChecked;
    private static volatile boolean available;
    private static boolean warnedUnavailable;
    private static final AtomicLong RELOAD_GENERATION = new AtomicLong();

    private AE2PatternIntrospection() {
    }

    public static boolean isAvailable() {
        selfCheck();
        return available;
    }

    public static Object getStablePatternIdentity(IPatternDetails details) {
        try {
            AEItemKey definition = readDefinition(details);
            return definition != null ? definition : details;
        } catch (Throwable e) {
            disableOnce(e);
            return details;
        }
    }

    public static boolean isKnownSafePatternType(IPatternDetails details) {
        return details instanceof AECraftingPattern;
    }

    public static Optional<ECOFastPathKey> buildFastPathKey(
        IPatternDetails details,
        KeyCounter[] craftingContainer,
        Level level
    ) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            AEItemKey definition = readDefinition(details);
            if (definition == null) {
                return Optional.empty();
            }
            return ECOFastPathKey.of(
                definition,
                craftingContainer,
                level,
                RELOAD_GENERATION.get()
            );
        } catch (Throwable e) {
            disableOnce(e);
            return Optional.empty();
        }
    }

    public static void onRecipeReloadOrServerReload() {
        RELOAD_GENERATION.incrementAndGet();
        ECOCraftingFastPathCache.clearAllCaches();
        ECOFastPathDiagnostics.clear();
    }

    public static long getReloadGeneration() {
        return RELOAD_GENERATION.get();
    }

    private static AEItemKey readDefinition(IPatternDetails details) {
        if (details instanceof AECraftingPatternAccessor accessor) {
            AEItemKey definition = accessor.neoecoae$getDefinitionKey();
            if (definition != null) {
                return definition;
            }
        }
        return details.getDefinition();
    }

    private static synchronized void selfCheck() {
        if (selfChecked) {
            return;
        }
        selfChecked = true;
        try {
            Class.forName(AECraftingPattern.class.getName());
            Class.forName(CraftingCpuHelper.class.getName());
            available = true;
        } catch (Throwable e) {
            disableOnce(e);
        }
    }

    private static synchronized void disableOnce(Throwable e) {
        available = false;
        if (!warnedUnavailable) {
            warnedUnavailable = true;
            LOGGER.warn(
                "ECO AE2 fast path disabled: incompatible AE2 internals or accessor failure",
                e
            );
        }
    }
}
