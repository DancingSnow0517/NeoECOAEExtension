package cn.dancingsnow.neoecoae.grid;

import appeng.core.definitions.AEItems;
import net.minecraft.world.item.ItemStack;

/**
 * The rule that governs replacing a pattern whose item is about to be destroyed.
 *
 * <p>Migrating patterns clears source slots. Some of those patterns left something behind - a bus slot
 * keeps the stack - but a recipe absorbed by a container, and equally a recipe the storage already
 * reaches, leaves nothing. Clearing in those cases without handing a blank back turns "the network has
 * that recipe" into a vanished item, which is why the rule lives in one place: every caller that clears a
 * source has to be able to point at it.</p>
 */
public final class PatternRefund {

    private PatternRefund() {
    }

    /**
     * What replaces an encoded pattern once nothing is left in its place.
     *
     * <p>One blank per encoded pattern: a container records the recipe once, so a stack of N has to come
     * back as N blanks or the rest is lost. Clamped to at least one because a caller reaching here holds a
     * real pattern.</p>
     */
    public static ItemStack blankFor(ItemStack pattern) {
        return AEItems.BLANK_PATTERN.stack(blankCountFor(pattern));
    }

    /**
     * How many blanks one source pattern is worth.
     *
     * <p>Split out from {@link #blankFor} so the arithmetic is testable without a bound item registry - the
     * rule is the part worth pinning down, the item that carries it is not.</p>
     */
    public static int blankCountFor(ItemStack pattern) {
        return Math.max(1, pattern.getCount());
    }

    /**
     * Whether a source pattern may be cleared, expressed once so the call sites cannot drift apart.
     *
     * <p>{@code consumed} means a container took the recipe, so the item is gone and a blank is owed;
     * {@code covered} means that blank reached the network in full. A slot stores the pattern as a stack, so
     * the network already holds it and there is nothing to repay - clearing is always allowed.</p>
     */
    public static boolean mayClearSource(boolean consumed, boolean covered) {
        return !consumed || covered;
    }

    /**
     * Whether the source may be cleared, given what the refund managed to place.
     *
     * <p>Full coverage only. A partial insert leaves a remainder that nothing would hold, so keeping the
     * source and retrying later - the migration re-registers its candidates - loses nothing while the
     * alternative loses the remainder. Overshooting is accepted for the same reason: the loss is covered,
     * and refusing would strand the source for no gain.</p>
     */
    public static boolean covers(ItemStack replacement, long inserted) {
        return replacement != null && !replacement.isEmpty() && inserted >= replacement.getCount();
    }
}
