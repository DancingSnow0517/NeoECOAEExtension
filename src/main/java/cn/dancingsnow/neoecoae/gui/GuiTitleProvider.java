package cn.dancingsnow.neoecoae.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets a block decide the title of the interface GUI opened for it.
 *
 * <p>The interface GUIs used to hardcode their own titles, so an addon that registers its own
 * interface block on top of {@code ECOMachineInterfaceBlockEntity} gets this mod's name in the
 * header of a GUI belonging to a block that does not exist in its own registry. The block is the
 * only thing both sides can reach without either subclassing the block entity or patching the UI,
 * so the title is asked of it and falls back to the previous literal for every block that does not
 * implement this.
 */
public interface GuiTitleProvider {
    Component getGuiTitle(BlockState state);

    /** {@code ecoDefault} is whatever the GUI passed before this existed. */
    static Component title(BlockState state, Component ecoDefault) {
        Block block = state.getBlock();
        return block instanceof GuiTitleProvider provider ? provider.getGuiTitle(state) : ecoDefault;
    }
}
