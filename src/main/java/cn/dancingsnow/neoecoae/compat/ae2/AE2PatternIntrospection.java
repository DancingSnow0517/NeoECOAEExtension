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
import cn.dancingsnow.neoecoae.impl.crafting.planner.growth.NetGrowthPatternValidationRegistry;
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

    /**
     * Monotonic counter bumped by every recipe/datapack/server reload. It is part of every
     * {@link ECOFastPathKey} and is also snapshotted into a verified fast-path credential so a reload that
     * lands between verification and execution invalidates the credential instead of being executed.
     */
    public static long reloadGeneration() {
        return reloadGeneration;
    }

    public static boolean isKnownSafePatternType(IPatternDetails details) {
        // AE2 stores smithing and stonecutting recipes as dedicated pattern implementations rather
        // than AECraftingPattern, but both still have deterministic molecular-assembler semantics.
        return details instanceof AECraftingPattern
            || details instanceof AESmithingTablePattern
            || details instanceof AEStonecuttingPattern;
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
        NetGrowthPatternValidationRegistry.clear();
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
            available = AECraftingPattern.class != null && CraftingCpuHelper.class != null;
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
