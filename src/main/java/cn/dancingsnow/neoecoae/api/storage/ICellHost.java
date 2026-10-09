package cn.dancingsnow.neoecoae.api.storage;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Stable integration point for a single ECO storage or computation cell slot.
 * Third-party terminals may query this interface with {@code instanceof}; addons may also implement it.
 *
 * <p>Mutations must run on the owning server thread. Callers are responsible for checking player/network
 * permissions before changing a slot; this interface does not authenticate a player.</p>
 */
public interface ICellHost {
    /**
     * Requests replacement of the current cell, or removal when {@code cellStack} is {@code null}.
     *
     * <p>Pass a non-empty, valid stack of exactly one cell to install it. {@link ItemStack#EMPTY} is not
     * a removal request and is rejected by ECO hosts. Invalid cells and changes blocked by extraction
     * restrictions are silently rejected. The {@code void} signature is retained for compatibility;
     * callers must re-read {@link #getCellStack()} before transferring ownership of either stack.</p>
     *
     * <p>Check {@link #isItemValid(ItemStack)} for insertion and {@link #canExtractCell()} before removing
     * or replacing a cell. Prefer separate removal and insertion so the previous cell is not lost.</p>
     *
     * @param cellStack a single valid cell, or {@code null} to clear the slot
     */
    void setCellStack(@Nullable ItemStack cellStack);

    /**
     * Returns the installed cell, or {@code null} when the slot is empty.
     * The returned stack may be the host's live stack; do not mutate it directly.
     */
    @Nullable
    ItemStack getCellStack();

    /**
     * Whether this host accepts the non-empty cell type in its current configuration.
     * This does not indicate that the slot is empty or that its current cell can be replaced.
     */
    boolean isItemValid(ItemStack stack);

    /**
     * Whether extraction/replacement is currently permitted. An empty slot may also return {@code true};
     * check {@link #getCellStack()} separately for presence.
     *
     * <p>ECO storage drives deny extraction during infinite-storage migration and while an infinite
     * member is locked outside a formed infinite-storage host. A formed infinite host permits member
     * extraction. This query never releases ownership or checks player permissions.</p>
     */
    default boolean canExtractCell() {
        return true;
    }

    /**
     * Optional player-facing explanation of an extraction restriction, normally a translatable component.
     * A {@code null} result means there is no explanation; always use {@link #canExtractCell()} to decide
     * whether extraction is allowed. An empty slot is not an extraction restriction.
     */
    @Nullable
    default Component getCellExtractionBlockReasonText() {
        return null;
    }
}
