package cn.dancingsnow.neoecoae.util;

/**
 * Implemented by inventories whose trailing rows are display-only.
 *
 * <p>A view can show more rows than it can act on. The pattern access terminal's view does exactly that: a
 * pattern disk's slot is hidden and the recipes inside the disk are appended after the real slots, because a
 * disk is not a pattern but its contents are. Those appended rows have no place to be written back to - taking
 * a recipe out of a disk has to draw a blank pattern and delete the entry, which only the disk's own removal
 * path does - so anything that moves rows around has to leave them alone.</p>
 *
 * <p>Transferring them anyway is not a no-op: the transfer samples through the display path and clears through
 * the slot view, so an appended row would be handed over while the source stayed put, i.e. the row would be
 * duplicated.</p>
 */
public interface WritablePrefix {

    /**
     * @return how many leading slots may be written to; the slots from here on are display-only
     */
    int writableSlotCount();
}
