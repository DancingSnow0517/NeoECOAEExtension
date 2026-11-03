package cn.dancingsnow.neoecoae.crafting.display.processing;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Lifts vanilla's block reach check for exactly one machine menu opened from the crafting status page. The exemption
 * covers the opening interaction and then only the menu it produced; any other menu ends it.
 */
public final class ECORemoteMachineReach {
    private static final Map<Player, Marker> MARKERS = new WeakHashMap<>();

    private ECORemoteMachineReach() {}

    private static final class Marker {
        final Level level;
        final BlockPos pos;
        @Nullable AbstractContainerMenu menu;

        Marker(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos.immutable();
        }
    }

    static synchronized void mark(Player player, Level level, BlockPos pos) {
        MARKERS.put(player, new Marker(level, pos));
    }

    static synchronized void bind(Player player, AbstractContainerMenu menu) {
        var marker = MARKERS.get(player);
        if (marker != null) marker.menu = menu;
    }

    static synchronized void clear(Player player) {
        MARKERS.remove(player);
    }

    /** True while {@code menu} is the menu opened remotely for {@code player}; the server keeps it open past reach. */
    public static synchronized boolean allowsMenu(Player player, AbstractContainerMenu menu) {
        var marker = MARKERS.get(player);
        if (marker == null || marker.menu == null) return false;
        if (player.containerMenu != marker.menu) {
            MARKERS.remove(player);
            return false;
        }
        return marker.menu == menu && marker.level == player.level();
    }

    /** True while {@code player} uses the menu opened remotely for the block at {@code pos}. */
    public static synchronized boolean allows(Player player, BlockPos pos) {
        var marker = MARKERS.get(player);
        if (marker == null) return false;
        if (marker.menu != null && player.containerMenu != marker.menu) {
            MARKERS.remove(player);
            return false;
        }
        return marker.level == player.level() && marker.pos.equals(pos);
    }
}
