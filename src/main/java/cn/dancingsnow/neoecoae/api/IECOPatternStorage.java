package cn.dancingsnow.neoecoae.api;

import appeng.api.networking.IGridNodeService;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface IECOPatternStorage extends IGridNodeService {
    /**
     * Inserts a pattern through the legacy-compatible public entry point.
     *
     * <p>This method is intentionally boolean because optional integrations discover it through reflection.</p>
     */
    boolean insertPattern(ItemStack itemStack);

    /** Inserts a pattern and preserves the detailed ECO insertion outcome for native callers. */
    default ECOPatternInsertionResult insertPatternWithResult(ItemStack itemStack) {
        return insertPattern(itemStack)
                ? ECOPatternInsertionResult.INSERTED
                : ECOPatternInsertionResult.NO_TARGET;
    }

    /** Inserts a pattern whose details have already been decoded by the migration coordinator. */
    default ECOPatternInsertionResult insertPreparedPattern(ECOPreparedPattern prepared) {
        return insertPatternWithResult(prepared.stack());
    }

    /**
     * Inserts a pattern after the caller has already established that it is not present in the
     * logical storage domain. Implementations may skip an otherwise expensive duplicate scan.
     */
    default ECOPatternInsertionResult insertPatternKnownUnique(ItemStack itemStack) {
        return insertPatternWithResult(itemStack);
    }

    /** Inserts a prepared pattern after network-wide uniqueness has already been established. */
    default ECOPatternInsertionResult insertPreparedPatternKnownUnique(ECOPreparedPattern prepared) {
        return insertPatternKnownUnique(prepared.stack());
    }

    /** Whether a normal {@link #insertPatternWithResult(ItemStack)} call checks the complete logical domain. */
    default boolean checksLogicalDomainForDuplicates() {
        return false;
    }

    /**
     * Whether this storage can take {@code pattern} into a store other than its slot inventory right
     * now - a pattern disk, for example.
     *
     * <p>{@link cn.dancingsnow.neoecoae.grid.PatternCatalog} probes every writable storage for this
     * <em>before</em> its ordinary destination loop. Without that pass a pattern bound for a disk would be
     * spent on whichever bus happens to expose a free slot first, and the disk it belongs on would never be
     * reached.</p>
     *
     * <p>Must be free of side effects.</p>
     */
    default boolean canAcceptIntoAuxiliary(ItemStack pattern) {
        return false;
    }

    /**
     * Stores {@code pattern} into the auxiliary store that {@link #canAcceptIntoAuxiliary(ItemStack)}
     * just accepted it for.
     *
     * <p>Only called after that probe returned {@code true} for the same pattern. {@code INSERTED} means the
     * pattern is stored and the caller is done; {@code ALREADY_PRESENT} means it was already there. Any
     * other result - {@code INCOMPATIBLE} for a pattern this storage cannot execute, {@code NO_SPACE} or
     * {@code NO_TARGET} for one it declines after all - leaves the pattern to the ordinary destination
     * loop.</p>
     *
     * @param prepared the caller's already decoded pattern, when it has one; implementations may reuse it
     *                 instead of decoding {@code pattern} again
     */
    default ECOPatternInsertionResult insertIntoAuxiliary(ItemStack pattern, @Nullable ECOPreparedPattern prepared) {
        return ECOPatternInsertionResult.NO_TARGET;
    }

    /**
     * Convenience overload for callers that have not decoded the pattern.
     *
     * <p>Kept so a caller compiled against the earlier single-argument signature still resolves. Callers
     * that already hold an {@link ECOPreparedPattern} should use the two-argument form - implementations
     * reuse it instead of decoding the pattern again.</p>
     */
    default ECOPatternInsertionResult insertIntoAuxiliary(ItemStack pattern) {
        return insertIntoAuxiliary(pattern, null);
    }
}
