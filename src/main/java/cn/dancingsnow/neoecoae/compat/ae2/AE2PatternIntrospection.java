package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.mixins.ae2.AECraftingPatternAccessor;
import java.util.Optional;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AE2PatternIntrospection {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private static boolean selfChecked = false;
    private static boolean available = false;
    private static boolean warnedUnavailable = false;
    private static long reloadGeneration = 0L;

    private AE2PatternIntrospection() {}

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
        } catch (RuntimeException | LinkageError e) {
            disableOnce(e);
            return details;
        }
    }

    public static boolean isKnownSafePatternType(IPatternDetails details) {
        if (isKnownSpecialPattern(details)) {
            return true;
        }
        if (!(details instanceof AECraftingPattern)) {
            return isExternalProcessingPattern(details);
        }
        if (!(details instanceof AECraftingPatternAccessor accessor)) {
            return false;
        }
        try {
            var recipe = accessor.neoecoae$getRecipe();
            return recipe != null && !recipe.isSpecial();
        } catch (RuntimeException | LinkageError e) {
            disableOnce(e);
            return false;
        }
    }

    public static PatternEligibility classifyPatternEligibility(IPatternDetails details) {
        if (isKnownSpecialPattern(details)) {
            return details.getDefinition() != null
                    ? PatternEligibility.ELIGIBLE
                    : PatternEligibility.RECIPE_UNAVAILABLE;
        }
        if (!(details instanceof AECraftingPattern pattern)) {
            return isExternalProcessingPattern(details) && details.getDefinition() != null
                    ? PatternEligibility.ELIGIBLE
                    : PatternEligibility.UNSUPPORTED_PATTERN_TYPE;
        }
        if (!(pattern instanceof AECraftingPatternAccessor accessor)) {
            return PatternEligibility.RECIPE_UNAVAILABLE;
        }
        try {
            CraftingRecipe recipe = accessor.neoecoae$getRecipe();
            if (recipe == null) {
                return PatternEligibility.RECIPE_UNAVAILABLE;
            }
            if (pattern.canSubstitute() && recipe.isSpecial()) {
                return PatternEligibility.SUBSTITUTION_SPECIAL_RECIPE;
            }
            return PatternEligibility.ELIGIBLE;
        } catch (RuntimeException | LinkageError failure) {
            disableOnce(failure);
            return PatternEligibility.RECIPE_UNAVAILABLE;
        }
    }

    public static boolean hasStableFastPathInputs(IPatternDetails details) {
        if (isKnownSpecialPattern(details)) {
            if (details instanceof AEStonecuttingPattern stonecutting) {
                return details.getDefinition() != null && !stonecutting.canSubstitute();
            }
            if (details instanceof AESmithingTablePattern smithing) {
                return details.getDefinition() != null && !smithing.canSubstitute();
            }
        }
        if (!(details instanceof AECraftingPattern pattern)
                || !(details instanceof AECraftingPatternAccessor accessor)) {
            return false;
        }
        try {
            var recipe = accessor.neoecoae$getRecipe();
            return recipe != null && !recipe.isSpecial() && !pattern.canSubstitute() && !pattern.canSubstituteFluids();
        } catch (RuntimeException | LinkageError e) {
            disableOnce(e);
            return false;
        }
    }

    private static boolean isKnownSpecialPattern(IPatternDetails details) {
        return details != null
                && (details.getClass() == AEStonecuttingPattern.class
                        || details.getClass() == AESmithingTablePattern.class);
    }

    public static Optional<ECOFastPathKey> buildFastPathKey(
            IPatternDetails details, KeyCounter[] craftingContainer, Level level) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            return ECOFastPathKey.of(getStablePatternIdentity(details), craftingContainer, level, reloadGeneration);
        } catch (RuntimeException | LinkageError e) {
            disableOnce(e);
            return Optional.empty();
        }
    }

    public static long getReloadGeneration() {
        return reloadGeneration;
    }

    private static boolean isExternalProcessingPattern(IPatternDetails details) {
        if (details == null) {
            return false;
        }
        String name = details.getClass().getName();
        return name.contains("ProcessingPattern") || name.endsWith("OverloadedProviderOnlyPatternDetails");
    }

    public static void onRecipeReloadOrServerReload() {
        reloadGeneration++;
        ECOCraftingFastPathCache.clearAllCaches();
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
        } catch (ClassNotFoundException | LinkageError e) {
            disableOnce(e);
        }
    }

    private static void disableOnce(Throwable e) {
        available = false;
        if (!warnedUnavailable) {
            warnedUnavailable = true;
            LOGGER.warn("ECO AE2 fast path disabled: incompatible AE2 internals or accessor failure", e);
        }
    }

    public enum PatternEligibility {
        ELIGIBLE,
        UNSUPPORTED_PATTERN_TYPE,
        RECIPE_UNAVAILABLE,
        SUBSTITUTION_SPECIAL_RECIPE
    }
}
