package cn.dancingsnow.neoecoae.api;

import appeng.api.networking.IGridNodeService;
import net.minecraft.world.item.ItemStack;

public interface IECOPatternStorage extends IGridNodeService {
    boolean insertPattern(ItemStack itemStack);

    /**
     * Whether this storage can currently accept the pattern into one of its pattern disks.
     *
     * <p>{@link cn.dancingsnow.neoecoae.grid.PatternStorage} consults this before ordinary slot
     * insertion so pattern disks take priority grid-wide: a storage without disk space must not
     * consume the pattern into a slot while another storage still has a disk that could hold it.</p>
     *
     * @return {@code true} when a pattern disk on this storage has the room and type for the pattern
     */
    default boolean canInsertIntoDisk(ItemStack itemStack) {
        return false;
    }
}
