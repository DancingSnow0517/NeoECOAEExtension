package cn.dancingsnow.neoecoae.client.diagnostics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in development probe; uses the real client, world loader and framebuffer. */
public final class ClientStartupProbe {
    private static final Logger LOG = LoggerFactory.getLogger("neoecoae.clientSmoke");
    private static boolean started;
    private static int worldTicks;
    private static boolean captured;
    private static String lastScreen;
    private static net.minecraft.core.BlockPos workstationPos;

    private ClientStartupProbe() {}

    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        String currentScreen = client.screen == null ? "in_game" : client.screen.getClass().getName();
        if (!currentScreen.equals(lastScreen)) {
            LOG.info("CLIENT_SMOKE screen: {}", currentScreen);
            lastScreen = currentScreen;
        }
        if (java.nio.file.Files.exists(client.gameDirectory.toPath().resolve("client-smoke-stop"))) {
            LOG.info("CLIENT_SMOKE stopping client on local request");
            client.stop();
            return;
        }
        if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            client.options.onboardAccessibility = false;
            client.options.save();
            client.setScreen(new TitleScreen());
            return;
        }
        if (!started && client.screen instanceof TitleScreen && client.getOverlay() == null) {
            started = true;
            LOG.info("CLIENT_SMOKE title screen reached; creating isolated creative world");
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            var settings = new LevelSettings("NeoECOAE startup validation", GameType.CREATIVE, false,
                Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("neoecoae-startup-" + System.currentTimeMillis(),
                settings, new WorldOptions(20261021L, false, false), WorldPresets::createNormalWorldDimensions);
        }
        if (!captured && client.level != null && client.player != null && ++worldTicks >= 200) {
            captured = true;
            var server = client.getSingleplayerServer();
            LOG.info("CLIENT_SMOKE world entered: dimension={}, player={}, integratedServer={}, gameTime={}",
                client.level.dimension().location(), client.player.getGameProfile().getName(),
                server != null, client.level.getGameTime());
            LOG.info("CLIENT_SMOKE addons={}", java.util.List.of("advanced_ae", "expatternprovider", "extendedae_plus", "ae2lt")
                .stream().filter(net.minecraftforge.fml.ModList.get()::isLoaded).toList());
            if (server != null) server.execute(() -> {
                for (String recipe : java.util.List.of("reaction_chamber/energized_fluix_crystal", "reaction_chamber/energized_crystal",
                        "circuit_cutter/superconducting_processor_print", "compat/ae2lt/eco_lightning_cell_16m",
                        "compat/ae2lt/eco_lightning_cell_64m", "compat/ae2lt/eco_lightning_cell_256m")) {
                    var id = cn.dancingsnow.neoecoae.NeoECOAE.id(recipe);
                    LOG.info("CLIENT_SMOKE recipe {} loaded={}", id, server.getRecipeManager().byKey(id).isPresent());
                }
            });
            Screenshot.grab(client.gameDirectory, "neoecoae-startup-world.png", client.getMainRenderTarget(),
                message -> LOG.info("CLIENT_SMOKE screenshot: {}", message.getString()));
        }
        if (captured && Boolean.getBoolean("neoecoae.clientSmokeEnergy")) {
            ManagedEnergyClientProbe.tick(client);
        }
        if (captured && client.level != null && client.player != null
                && !System.getProperty("neoecoae.workstationModelSmoke", "").isBlank()) {
            WorkstationModelClientProbe.tick(client);
        }
        if (captured && client.level != null && client.player != null
                && Boolean.getBoolean("neoecoae.clientSmokeInventories")) {
            ManagedInventoryClientProbe.tick(client);
        }
        if (captured && client.level != null && client.player != null && Boolean.getBoolean("neoecoae.clientSmokeMenus")) {
            worldTicks++;
            if (worldTicks == 240) {
                var server = client.getSingleplayerServer();
                var id = client.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(id);
                    var level = player.serverLevel();
                    var pos = player.blockPosition().offset(2, 0, 0);
                    workstationPos = pos;
                    var block = cn.dancingsnow.neoecoae.all.NEBlocks.INTEGRATED_WORKING_STATION.get();
                    level.setBlockAndUpdate(pos, block.defaultBlockState());
                });
            }
            if (worldTicks == 260) {
                var server = client.getSingleplayerServer();
                var id = client.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(id);
                    var level = player.serverLevel();
                    var pos = workstationPos;
                    var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                        net.minecraft.core.Direction.UP, pos, false);
                    var result = level.getBlockState(pos).use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                    LOG.info("CLIENT_SMOKE workstation activation={}, menu={}", result, player.containerMenu.getClass().getName());
                });
            }
            if (worldTicks == 300) {
                LOG.info("CLIENT_SMOKE workstation screen={}", currentScreen);
                Screenshot.grab(client.gameDirectory, "neoecoae-startup-workstation.png", client.getMainRenderTarget(),
                    message -> LOG.info("CLIENT_SMOKE screenshot: {}", message.getString()));
            }
            if (worldTicks == 340) client.player.closeContainer();
        }
    }
}
