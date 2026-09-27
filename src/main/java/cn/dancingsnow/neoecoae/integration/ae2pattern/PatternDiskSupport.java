package cn.dancingsnow.neoecoae.integration.ae2pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;

import cn.dancingsnow.neoecoae.api.AuxiliaryPatternHolder;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * What a host asks when it wants pattern-disk aware behaviour, without naming the mod that provides it.
 *
 * <p>AE2 Pattern Disk is an optional dependency. The classes that touch its api live in this package and are
 * only ever reached through {@link #install}, which the integration calls once it has been loaded - and the
 * integration manager only loads it when the mod is present. So AEPD types stay out of every signature here,
 * and a host can hold this facade unconditionally: with nothing installed, {@link #available()} is false and
 * the host keeps behaving as a plain one.</p>
 *
 * <p>The backend is supplied rather than implemented here because the implementations need AEPD's api in
 * their method bodies. Splitting them keeps this class loadable on a machine that has never seen the mod.</p>
 */
public final class PatternDiskSupport {

    /**
     * A view that expands disks into their patterns, together with the handle that rebuilds it.
     *
     * <p>The two travel together because they are only useful together: the view caches its row layout, so a
     * host that changes its disks and loses the handle would show stale rows forever.</p>
     */
    public interface TerminalView {

        /** @return the inventory to expose in place of the raw slots */
        InternalInventory view();

        /** Rebuilds the row layout after the disks changed. */
        void invalidate();
    }

    /**
     * The AEPD-backed implementation, provided by the integration.
     *
     * <p>Every method is called on the server thread; the level and grid suppliers exist because a host can
     * be asked for its storage before it is attached to anything.</p>
     */
    public interface Backend {

        /**
         * @param slots the host's pattern inventory, whose slots may hold pattern disks
         * @return a holder over {@code slots}, or {@code null} when disks are not readable yet
         */
        @Nullable
        AuxiliaryPatternHolder holderFor(InternalInventory slots, Supplier<IGrid> grid, Supplier<Level> level);

        /**
         * @return a terminal view that expands the disks in {@code diskSlots} into their patterns, or
         *         {@code null} to leave the host's own view in place
         * @see PatternDiskSupport#terminalView
         */
        @Nullable
        TerminalView terminalView(
                InternalInventory diskSlots,
                Supplier<IGrid> grid,
                IActionHost machine,
                Runnable onChanged,
                Supplier<Level> level);

        /**
         * @return whether a disk in {@code slots} could take {@code pattern} right now. Must not write.
         * @see PatternDiskSupport#canAcceptAuxiliary
         */
        boolean canAcceptAuxiliary(InternalInventory slots, ItemStack pattern, @Nullable Level level);

        /**
         * Writes {@code pattern} onto a disk in {@code slots}.
         *
         * <p>No blank-pattern accounting passes through here. The blank a write frees is settled by the caller
         * that removed the source pattern - see {@code PatternRefund} - which is the only party that knows an
         * item was actually consumed. A sink here would charge the network a second time.</p>
         *
         * @return whether a disk took it; {@code false} leaves every disk untouched
         * @see PatternDiskSupport#insertAuxiliary
         */
        boolean insertAuxiliary(InternalInventory slots, ItemStack pattern, @Nullable Level level);

        /**
         * Decodes every pattern on the disks in {@code slots}.
         *
         * @return the decoded patterns, or an empty list when nothing is readable
         * @see PatternDiskSupport#decodeAuxiliary
         */
        List<IPatternDetails> decodeAuxiliary(InternalInventory slots, @Nullable Level level);

        /**
         * @return whether {@code stack} is an auxiliary pattern container, i.e. a slot holding it is not a slot
         *         holding one pattern
         * @see PatternDiskSupport#isAuxiliaryContainer
         */
        boolean isAuxiliaryContainer(ItemStack stack);
    }

    private static volatile Backend backend;

    private PatternDiskSupport() {
    }

    /** Installs {@code value}, called by the integration while it loads. */
    public static void install(Backend value) {
        backend = value;
    }

    /** @return whether AEPD is present, i.e. whether anything may be asked of this facade */
    public static boolean available() {
        return backend != null;
    }

    /**
     * @return whether {@code stack} is an auxiliary pattern container. Answered from the stack alone, so a
     *         client can ask it too - which is what the pattern preview's quick move needs, since which host a
     *         stack would end up in is only known on the server.
     */
    public static boolean isAuxiliaryContainer(ItemStack stack) {
        Backend current = backend;
        return current != null && current.isAuxiliaryContainer(stack);
    }

    /**
     * @return a holder over {@code slots}, or {@code null} when AEPD is absent - which is the "no pattern
     *         disks here" answer a host should treat as "behave as before", not as a failure
     */
    @Nullable
    public static AuxiliaryPatternHolder holderFor(
            InternalInventory slots, Supplier<IGrid> grid, Supplier<Level> level) {
        Backend current = backend;
        return current == null ? null : current.holderFor(slots, grid, level);
    }

    /**
     * Wraps {@code diskSlots} into a view whose rows are the patterns on the disks they hold, charging the
     * network a blank pattern for each pattern taken back out.
     *
     * <p>Server-side only: the accounting resolves a grid, which the client does not have. Call
     * {@link TerminalView#invalidate()} on the result when the host's disks change - which is what
     * {@link AuxiliaryPatternHolder#getAuxiliaryRevision()} tells it.</p>
     *
     * @return the view, or {@code null} when AEPD is absent or the call does not apply
     */
    @Nullable
    public static TerminalView terminalView(
            InternalInventory diskSlots,
            Supplier<IGrid> grid,
            IActionHost machine,
            Runnable onChanged,
            Supplier<Level> level) {
        Backend current = backend;
        return current == null ? null : current.terminalView(diskSlots, grid, machine, onChanged, level);
    }

    /**
     * @return whether a disk in {@code slots} could take {@code pattern}. A read-only question: asking it
     *         twice, or asking it and not writing, changes nothing
     */
    public static boolean canAcceptAuxiliary(InternalInventory slots, ItemStack pattern, @Nullable Level level) {
        Backend current = backend;
        return current != null && current.canAcceptAuxiliary(slots, pattern, level);
    }

    /**
     * Writes {@code pattern} onto a disk in {@code slots}.
     *
     * <p>Accounts for no blank pattern: the caller that removed the source pattern settles that, since only it
     * knows whether an item was consumed. Writing here does not touch the network's blank count.</p>
     *
     * @return whether a disk took it. {@code false} means every disk was left untouched and
     *         {@code pattern} is still the caller's to place somewhere else
     */
    public static boolean insertAuxiliary(
            InternalInventory slots,
            ItemStack pattern,
            @Nullable Level level) {
        Backend current = backend;
        return current != null && current.insertAuxiliary(slots, pattern, level);
    }

    /**
     * Decodes every pattern held on the disks in {@code slots}, so a host can advertise them the way it
     * advertises its own slot patterns.
     *
     * <p>The result is not cached here: decoding is memoized against the disks' contents by the layer that
     * owns the decode, and a host calling this on every crafting request would otherwise pay for a fresh list
     * each time.</p>
     *
     * @return the decoded patterns, in disk-then-slot order; empty when AEPD is absent, {@code level} is
     *         {@code null}, or nothing on the disks decodes
     */
    public static List<IPatternDetails> decodeAuxiliary(InternalInventory slots, @Nullable Level level) {
        Backend current = backend;
        return current == null ? List.of() : current.decodeAuxiliary(slots, level);
    }
}
