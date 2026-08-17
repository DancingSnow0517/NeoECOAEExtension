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
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOSelectedInputPatternDetails;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningService;
import cn.dancingsnow.neoecoae.mixins.ae2.AECraftingPatternAccessor;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.crafting.CraftingRecipe;
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
        details = ECOSelectedInputPatternDetails.unwrap(details);
        return details instanceof AECraftingPattern
            || details instanceof AESmithingTablePattern
            || details instanceof AEStonecuttingPattern
            || isExternalProcessingPattern(details);
    }

    public static PatternEligibility classifyPatternEligibility(IPatternDetails details) {
        details = ECOSelectedInputPatternDetails.unwrap(details);
        if (details instanceof AESmithingTablePattern smithingPattern) {
            // Both strict and substitution smithing patterns become concrete before the key is
            // built. The selected template/base/addition and the assembled output are verified by
            // the worker, so the cache entry remains tied to one exact input selection.
            return PatternEligibility.ELIGIBLE;
        }
        if (details instanceof AEStonecuttingPattern) {
            // A stonecutting pattern stores one concrete recipe and output. Input substitution
            // only changes which valid ingredient stack is supplied, not the recipe result.
            return PatternEligibility.ELIGIBLE;
        }
        if (!(details instanceof AECraftingPattern pattern)) {
            // External CPUs can batch provider-owned processing patterns (AdvancedAE/AE2LT)
            // without relying on AE2's vanilla CraftingRecipe internals. They still need a stable
            // definition and pass the later concrete input/output safety checks.
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
            if (recipe.isSpecial()) {
                return PatternEligibility.SPECIAL_RECIPE;
            }
            return PatternEligibility.ELIGIBLE;
        } catch (Throwable e) {
            disableOnce(e);
            return PatternEligibility.RECIPE_UNAVAILABLE;
        }
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
        ECOPlanningService.clearCaches();
    }

    /**
     * Productive Bees' configurable comb block recipe is special only because its output keeps
     * the BeeType component from its single input. The AE2 pattern already exposes that input as
     * a concrete stack, so ECO can safely treat this one-input conversion as a normal operation.
     */
    public static boolean isProductiveBeesConfigurableCombBlockRecipe(IPatternDetails details) {
        details = ECOSelectedInputPatternDetails.unwrap(details);
        if (!(details instanceof AECraftingPatternAccessor accessor)) {
            return false;
        }
        try {
            CraftingRecipe recipe = accessor.neoecoae$getRecipe();
            return recipe != null
                && "cy.jdkdigital.productivebees.common.recipe.ConfigurableCombBlockRecipe"
                    .equals(recipe.getClass().getName())
                && details.getInputs() != null
                && details.getInputs().length == 1
                && details.getOutputs() != null
                && !details.getOutputs().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static long getReloadGeneration() {
        return RELOAD_GENERATION.get();
    }

    private static AEItemKey readDefinition(IPatternDetails details) {
        details = ECOSelectedInputPatternDetails.unwrap(details);
        if (details instanceof AECraftingPatternAccessor accessor) {
            AEItemKey definition = accessor.neoecoae$getDefinitionKey();
            if (definition != null) {
                return definition;
            }
        }
        return details.getDefinition();
    }

    private static boolean isExternalProcessingPattern(IPatternDetails details) {
        if (details == null) {
            return false;
        }
        String name = details.getClass().getName();
        return name.contains("ProcessingPattern")
            || name.endsWith("OverloadedProviderOnlyPatternDetails");
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

    public enum PatternEligibility {
        ELIGIBLE,
        UNSUPPORTED_PATTERN_TYPE,
        RECIPE_UNAVAILABLE,
        SPECIAL_RECIPE
    }
}
