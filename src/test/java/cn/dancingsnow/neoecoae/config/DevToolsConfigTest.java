package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevToolsConfigTest {
    private final ModConfig config = mock(ModConfig.class);

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        // Initialize the config before entering Mockito's unfinished when(...).thenReturn(...).
        assertNotNull(DevToolsConfig.SPEC);
    }

    @AfterEach void unload() {
        when(config.getSpec()).thenReturn(DevToolsConfig.SPEC);
        DevToolsConfig.unloading(new ModConfigEvent.Unloading(config));
        DevToolsConfig.SPEC.acceptConfig(null);
    }

    @Test void reloadChangesFlagAndResendsCommandsOnServerThread() throws Exception {
        var server = mock(MinecraftServer.class);
        var players = mock(PlayerList.class);
        var player = mock(ServerPlayer.class);
        var commands = mock(Commands.class);
        when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayers()).thenReturn(List.of(player));
        when(server.getCommands()).thenReturn(commands);

        try (var lifecycle = mockStatic(ServerLifecycleHooks.class)) {
            load(false);
            assertFalse(DevToolsConfig.enabled());
            lifecycle.when(ServerLifecycleHooks::getCurrentServer).thenReturn(server);

            reload(true);
            assertTrue(DevToolsConfig.enabled());
            var refresh = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(server).execute(refresh.capture());
            verifyNoInteractions(commands, players);
            refresh.getValue().run();
            verify(commands).sendCommands(player);

            clearInvocations(server, commands);
            reload(false);
            assertFalse(DevToolsConfig.enabled());
            verify(server).execute(refresh.capture());
            refresh.getValue().run();
            verify(commands).sendCommands(player);

            clearInvocations(server, commands);
            reload(false);
            verifyNoInteractions(server, commands);
        }
    }

    @Test void changesBeforeWorldIsLoadedDoNotNeedServer() throws Exception {
        try (var lifecycle = mockStatic(ServerLifecycleHooks.class)) {
            load(false);
            reload(true);
            assertTrue(DevToolsConfig.enabled());
        }
    }

    @Test void unrelatedConfigEventsDoNotEnableTools() throws Exception {
        try (var lifecycle = mockStatic(ServerLifecycleHooks.class)) {
            load(false);
            var unrelated = mock(ModConfig.class);
            DevToolsConfig.loading(new ModConfigEvent.Loading(unrelated));
            DevToolsConfig.reloading(new ModConfigEvent.Reloading(unrelated));
            assertFalse(DevToolsConfig.enabled());
        }
    }

    private void load(boolean enabled) throws Exception {
        setConfig(enabled);
        DevToolsConfig.loading(new ModConfigEvent.Loading(config));
    }

    private void reload(boolean enabled) throws Exception {
        setConfig(enabled);
        DevToolsConfig.reloading(new ModConfigEvent.Reloading(config));
    }

    private void setConfig(boolean enabled) throws Exception {
        when(config.getSpec()).thenReturn(DevToolsConfig.SPEC);
        var values = CommentedConfig.inMemory();
        values.set("devTools.enabled", enabled);
        DevToolsConfig.SPEC.correct(values);
        // FML's sealed loaded-config implementation is package-private; use the real
        // container so the test exercises ModConfigSpec's reload/cache behavior.
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
            .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
        constructor.setAccessible(true);
        var loaded = (IConfigSpec.ILoadedConfig) constructor.newInstance(values, null, config);
        DevToolsConfig.SPEC.acceptConfig(loaded);
    }
}
