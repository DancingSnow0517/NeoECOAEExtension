package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.pattern.AECraftingPattern;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.mixins.ae2.AECraftingPatternAccessor;
import java.util.Optional;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AE2PatternIntrospection {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private static boolean selfChecked = false;
    private static boolean available = false;
    private static boolean warnedUnavailable = false;
    private static long reloadGeneration = 0L;

    private AE2PatternIntrospection() {
    }

    public static boolean isAvailable() {
        selfCheck();
        return available;
    }

    public static Object getStablePatternIdentity(IPatternDetails details) {
        try {
            if (details instanceof AECraftingPatternAccessor accessor) {
                AEItemKey definition = accessor.neoecoae$getDefinitionKey();
                if (definition != null) {
                    return definition;
                }
            }
            AEItemKey definition = details.getDefinition();
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
            Optional<Object> identity = getFastPathPatternIdentity(details);
            if (identity.isEmpty()) {
                return Optional.empty();
            }
            return ECOFastPathKey.of(identity.get(), craftingContainer, level, reloadGeneration);
        } catch (Throwable e) {
            disableOnce(e);
            return Optional.empty();
        }
    }

    public static void onRecipeReloadOrServerReload() {
        reloadGeneration++;
        ECOCraftingFastPathCache.clearAllCaches();
    }

    public static long getReloadGeneration() {
        return reloadGeneration;
    }

    private static Optional<Object> getFastPathPatternIdentity(IPatternDetails details) {
        try {
            if (details instanceof AECraftingPatternAccessor accessor) {
                AEItemKey definition = accessor.neoecoae$getDefinitionKey();
                if (definition != null) {
                    return Optional.of(definition);
                }
            }
            AEItemKey definition = details.getDefinition();
            return definition != null ? Optional.of(definition) : Optional.empty();
        } catch (Throwable e) {
            disableOnce(e);
            return Optional.empty();
        }
    }

    private static void selfCheck() {
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

    private static void disableOnce(Throwable e) {
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
