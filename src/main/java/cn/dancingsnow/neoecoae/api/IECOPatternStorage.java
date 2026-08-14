package cn.dancingsnow.neoecoae.api;

import appeng.api.networking.IGridNodeService;
import net.minecraft.world.item.ItemStack;

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
}
