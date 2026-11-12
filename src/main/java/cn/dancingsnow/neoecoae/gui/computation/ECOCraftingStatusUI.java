package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.security.IActionHost;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.api.storage.ISubMenuHost;
import appeng.api.storage.ITerminalHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.MenuHostLocator;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtCpuStatus;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.grid.NEMachineLookup;
import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces AE2's crafting status menu with the ECO status page while the terminal's network has a working
 * ECO computation host. Without one, AE2's own menu opens unchanged.
 */
public final class ECOCraftingStatusUI {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("neoecoae", "crafting_status");
    private static final int VALIDATION_INTERVAL = 10;
    // Only populated for the duration of a synchronous PlayerUIMenuType.openUI call on the server thread.
    private static final Map<UUID, Context> OPENING = new ConcurrentHashMap<>();

    private ECOCraftingStatusUI() {}

    public static void register() {
        PlayerUIMenuType.register(ID, ECOCraftingStatusUI::holder);
    }

    /** Opens the ECO page for a terminal's crafting status request; false lets AE2 open its own menu. */
    public static boolean tryOpen(ServerPlayer player, MenuHostLocator locator) {
        ITerminalHost host = locator.locate(player, ITerminalHost.class);
        if (host == null) return false;
        IGrid grid = grid(host);
        if (grid == null || !hasFunctionalHost(grid)) return false;
        OPENING.put(player.getUUID(), new Context(host, locator));
        try {
            return PlayerUIMenuType.openUI(player, ID);
        } finally {
            OPENING.remove(player.getUUID());
        }
    }

    /** A formed, online computation host whose cluster is active can serve crafting jobs. */
    public static boolean hasFunctionalHost(IGrid grid) {
        return !functionalHosts(grid).isEmpty();
    }

    private static List<ECOComputationSystemBlockEntity> functionalHosts(IGrid grid) {
        List<ECOComputationSystemBlockEntity> hosts = new ArrayList<>();
        for (var host : NEMachineLookup.getMachines(grid, ECOComputationSystemBlockEntity.class)) {
            var cluster = host.getCluster();
            if (host.isFormed() && host.getMainNode().isOnline() && cluster != null && cluster.isActive()) hosts.add(host);
        }
        return hosts;
    }

    /** Same toggle as AE2's suspend button; CPUs without a suspend state are left untouched. */
    private static void toggleSuspended(ICraftingCPU cpu) {
        if (cpu instanceof ECOCraftingCPU eco) {
            eco.getLogic().setJobSuspended(!eco.getLogic().isJobSuspended());
        } else if (cpu instanceof CraftingCPUCluster cluster) {
            cluster.craftingLogic.setJobSuspended(!cluster.craftingLogic.isJobSuspended());
        } else {
            ECOAe2LtCpuStatus.toggleSuspended(cpu);
        }
    }

    @Nullable
    private static IGrid grid(ITerminalHost host) {
        if (!(host instanceof IActionHost actionHost)) return null;
        var node = actionHost.getActionableNode();
        return node == null ? null : node.getGrid();
    }

    @Nullable
    private static PlayerUIMenuType.PlayerUIHolder holder(Player player) {
        if (player.level().isClientSide()) {
            // The client mirrors the widget tree; all data and actions stay on the server.
            return new Holder(null);
        }
        Context context = OPENING.get(player.getUUID());
        return context == null ? null : new Holder(context);
    }

    private record Holder(@Nullable Context context) implements PlayerUIMenuType.PlayerUIHolder {
        @Override
        public ModularUI createUI(Player player) {
            var source = new ComputationCpuStatusPanel.Source(player,
                () -> player.level().getGameTime(),
                () -> context != null && context.valid(player),
                () -> context == null ? List.of() : context.cpus(),
                ECOCraftingStatusUI::toggleSuspended,
                ICraftingCPU::cancelJob,
                () -> { if (context != null) context.back(player); });
            var panel = new ComputationCpuStatusPanel(source, new CpuSelectionState.Identities<>());
            return ECOModularUI.create(panel, player);
        }

        @Override
        public boolean isStillValid(Player player) {
            return context == null || context.valid(player);
        }
    }

    private static final class Context {
        private final MenuHostLocator locator;
        private ITerminalHost host;
        private long checkedTick = Long.MIN_VALUE;
        private boolean valid = true;

        private Context(ITerminalHost host, MenuHostLocator locator) {
            this.host = host;
            this.locator = locator;
        }

        /** Same lifetime rule as AE2's terminal sub-menus, plus the ECO host requirement for this page. */
        private boolean valid(Player player) {
            long tick = player.level().getGameTime();
            if (tick >= checkedTick && tick - checkedTick < VALIDATION_INTERVAL) return valid;
            checkedTick = tick;
            ITerminalHost located = player.isAlive() ? locator.locate(player, ITerminalHost.class) : null;
            if (located != null) host = located;
            IGrid grid = located == null ? null : grid(located);
            valid = grid != null && located.getLinkStatus().connected() && hasFunctionalHost(grid);
            return valid;
        }

        /** AE2's own CPU list for the network: native clusters, ECO threads and other addon CPUs alike. */
        private List<ICraftingCPU> cpus() {
            IGrid grid = grid(host);
            return grid == null ? List.of() : List.copyOf(grid.getCraftingService().getCpus());
        }

        /** Returns to the terminal like AE2's back tab; deferred so the current menu is not closed mid-event. */
        private void back(Player player) {
            if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.level().getServer() == null) return;
            ISubMenuHost target = host;
            ISubMenu subMenu = new ISubMenu() {
                @Override public MenuHostLocator getLocator() { return locator; }
                @Override public ISubMenuHost getHost() { return target; }
            };
            var server = serverPlayer.level().getServer();
            server.execute(new TickTask(server.getTickCount(), () -> {
                if (!serverPlayer.isRemoved()) target.returnToMainMenu(serverPlayer, subMenu);
            }));
        }
    }
}
