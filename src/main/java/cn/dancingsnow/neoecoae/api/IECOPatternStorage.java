package cn.dancingsnow.neoecoae.api;

import appeng.api.networking.IGridNodeService;
import net.minecraft.world.item.ItemStack;

public interface IECOPatternStorage extends IGridNodeService {
    ECOPatternInsertionResult insertPattern(ItemStack itemStack);

    /** Inserts a pattern whose details have already been decoded by the migration coordinator. */
    default ECOPatternInsertionResult insertPreparedPattern(ECOPreparedPattern prepared) {
        return insertPattern(prepared.stack());
    }

    /**
     * Inserts a pattern after the caller has already established that it is not present in the
     * logical storage domain. Implementations may skip an otherwise expensive duplicate scan.
     */
    default ECOPatternInsertionResult insertPatternKnownUnique(ItemStack itemStack) {
        return insertPattern(itemStack);
    }

    /** Inserts a prepared pattern after network-wide uniqueness has already been established. */
    default ECOPatternInsertionResult insertPreparedPatternKnownUnique(ECOPreparedPattern prepared) {
        return insertPatternKnownUnique(prepared.stack());
    }

    /** Whether a normal {@link #insertPattern(ItemStack)} call checks the complete logical domain. */
    default boolean checksLogicalDomainForDuplicates() {
        return false;
    }
}
