package cn.dancingsnow.neoecoae.util;

import appeng.api.crafting.IPatternDetails;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Search keywords for one pattern: its own name plus everything it is built from and produces.
 *
 * <p>Shared rather than kept beside the bus: a terminal finds a recipe only through its keywords, so both
 * hosts that publish patterns - the bus's slots and the workstation's disks - have to build them the same
 * way, or a recipe would be findable on one and invisible to every search on the other.</p>
 */
public final class PatternSearchKeywords {

    private PatternSearchKeywords() {
    }

    public static String build(ItemStack stack, @Nullable IPatternDetails details) {
        if (stack.isEmpty()) {
            return "";
        }
        StringBuilder keywords = new StringBuilder(stack.getHoverName().getString());
        if (details != null) {
            for (var output : details.getOutputs()) {
                if (output != null) {
                    keywords.append('\n').append(output.what().getDisplayName().getString());
                }
            }
            for (var input : details.getInputs()) {
                if (input == null) {
                    continue;
                }
                for (var possible : input.getPossibleInputs()) {
                    if (possible != null) {
                        keywords.append('\n').append(possible.what().getDisplayName().getString());
                    }
                }
            }
        }
        return keywords.toString().toLowerCase(Locale.ROOT);
    }
}
