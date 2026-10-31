package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimizationRegistry;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.crafting.planner.growth.NetGrowthPatternValidationRegistry;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.AECraftingPatternAccessor;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AE2PatternIntrospection {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private static final Set<Class<?>> WARNED_DEFINITION_FAILURES = ConcurrentHashMap.newKeySet();
    private static volatile long reloadGeneration = 0L;

    private AE2PatternIntrospection() {
    }

    /** AE2 is a required dependency and the accessor mixin is mandatory, so introspection is always available. */
    public static boolean isAvailable() {
        return true;
    }

    /**
     * Monotonic counter bumped by every recipe/datapack/server reload. It is part of every
     * {@link ECOFastPathKey} and is also snapshotted into a verified fast-path credential so a reload that
     * lands between verification and execution invalidates the credential instead of being executed.
     */
    public static long reloadGeneration() {
        return reloadGeneration;
    }

    /** @deprecated Query the execution optimization registry; this does not classify general compatibility. */
    @Deprecated
    public static boolean isKnownSafePatternType(IPatternDetails details) {
        return ECOExecutionOptimizationRegistry.resolve(details).verifiedAssemblerFastPath();
    }

    /**
     * Identifies recipes whose ingredient match depends on an ItemStack component/NBT payload. This includes
     * vanilla shaped recipes using NeoForge's {@code neoforge:components} ingredient type: AE2 records the
     * selected concrete stack in the pattern, so its component patch remains visible through the input key.
     *
     * <p>This deliberately examines the encoded input stacks instead of recipe serializer classes. A component
     * ingredient is still an ordinary shaped/shapeless recipe, not a {@code SpecialRecipe}.</p>
     */
    public static boolean isSpecialNbtPattern(IPatternDetails details) {
        for (var input : details.getInputs()) {
            if (input == null) continue;
            for (var possible : input.getPossibleInputs()) {
                if (possible != null && possible.what() instanceof AEItemKey itemKey
                    && !itemKey.toStack(1).getComponentsPatch().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Optional<ECOFastPathKey> buildFastPathKey(
        IPatternDetails details,
        KeyCounter[] craftingContainer,
        Level level
    ) {
        Optional<Object> identity = getFastPathPatternIdentity(details);
        if (identity.isEmpty()) {
            return Optional.empty();
        }
        return ECOFastPathKey.of(identity.get(), craftingContainer, level, reloadGeneration);
    }

    public static synchronized void onRecipeReloadOrServerReload() {
        reloadGeneration++;
        ECOCraftingFastPathCache.clearAllCaches();
        NetGrowthPatternValidationRegistry.clear();
    }

    private static Optional<Object> getFastPathPatternIdentity(IPatternDetails details) {
        if (details instanceof AECraftingPatternAccessor accessor) {
            AEItemKey definition = accessor.neoecoae$getDefinitionKey();
            if (definition != null) {
                return Optional.of(definition);
            }
        }
        AEItemKey definition;
        try {
            // IPatternDetails may be implemented by another addon; one broken pattern only loses its own fast path.
            definition = details.getDefinition();
        } catch (RuntimeException e) {
            if (WARNED_DEFINITION_FAILURES.add(details.getClass())) {
                LOGGER.warn("ECO AE2 fast path skipped: {}.getDefinition() failed", details.getClass().getName(), e);
            }
            return Optional.empty();
        }
        return definition != null ? Optional.of(definition) : Optional.empty();
    }
}
