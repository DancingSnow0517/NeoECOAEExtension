package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationTerminalGroupTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName("cn.dancingsnow.neoecoae.NeoECOAE");
        }
    }

    @Test
    void installedModulesStaySeparateEvenWhenUploadSelectorsGroupOnlyByName() {
        Set<String> names = new HashSet<>();
        Set<AEItemKey> icons = new HashSet<>();
        int modules = 0;
        for (var module : LargeWorkstationModule.values()) {
            if (!module.hasUpgradeCard()) continue;
            var host = host(new AtomicReference<>(module));
            // A player-supplied name must survive without merging workstations that have different modules.
            doReturn(Component.literal("My workstation")).when(host).getName();
            var group = host.getTerminalGroup();
            assertTrue(names.add(group.name().getString()), "Upload groups must differ for " + module);
            assertTrue(icons.add(group.icon()), "Terminal icons must differ for " + module);
            assertTrue(group.name().getString().startsWith(module.shortTitle().getString()));
            assertTrue(group.name().getString().endsWith("My workstation"));
            assertEquals(host.getTerminalIcon(), group.icon());
            assertTrue(group.icon().getReadOnlyStack().is(Items.BARREL));
            assertEquals(Component.translatable("gui.neoecoae.workstation.module_current", module.title()),
                group.tooltip().getFirst());
            modules++;
        }
        assertEquals(modules, names.size());
        assertEquals(modules, icons.size());
    }

    @Test
    void changingCardsUpdatesTheExistingProviderAndRemovingThemRestoresRegularGrouping() throws Exception {
        var module = new AtomicReference<>(LargeWorkstationModule.REGULAR);
        var host = host(module);
        var provider = mock(LargeWorkstationPatternProvider.class, CALLS_REAL_METHODS);
        var hostField = LargeWorkstationPatternProvider.class.getDeclaredField("host");
        hostField.setAccessible(true);
        hostField.set(provider, host);

        PatternContainerGroup regular = provider.getTerminalGroup();
        for (var installed : new LargeWorkstationModule[] {
            LargeWorkstationModule.ADVANCED_AE, LargeWorkstationModule.DATA_ENERGISTICS,
            LargeWorkstationModule.APPLIED_GENERATORS
        }) {
            module.set(installed);
            assertNotEquals(regular, provider.getTerminalGroup());
            assertEquals(host.getTerminalGroup(), provider.getTerminalGroup());
            assertEquals(host(module).getTerminalGroup(), provider.getTerminalGroup(),
                "Workstations with the same module must still share a group");
        }
        module.set(LargeWorkstationModule.REGULAR);
        assertEquals(regular, provider.getTerminalGroup());
        assertNull(host.getTerminalIcon().getReadOnlyStack().get(DataComponents.CUSTOM_MODEL_DATA));
    }

    @Test
    void detachedInterfacesAndClustersWithoutControllersUseTheRegularFallback() {
        var host = host(new AtomicReference<>(LargeWorkstationModule.REGULAR));
        var regular = host.getTerminalGroup();
        doReturn(null).when(host).getCluster();
        assertEquals(regular, host.getTerminalGroup());
        doReturn(mock(NEIntegratedWorkingStationCluster.class)).when(host).getCluster();
        assertEquals(regular, host.getTerminalGroup());
    }

    @Test
    void terminalIconModelsResolveToTheExistingModuleControllerArtwork() throws Exception {
        try (var stream = getClass().getResourceAsStream(
            "/assets/neoecoae/models/item/large_integrated_working_station_interface.json")) {
            assertNotNull(stream);
            var model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("neoecoae:block/large_integrated_working_station_interface", model.get("parent").getAsString());
            var overrides = model.getAsJsonArray("overrides");
            for (var module : LargeWorkstationModule.values()) {
                if (module == LargeWorkstationModule.REGULAR) continue;
                var icon = host(new AtomicReference<>(module)).getTerminalIcon().getReadOnlyStack();
                int modelData = icon.get(DataComponents.CUSTOM_MODEL_DATA).value();
                var override = overrides.asList().stream().map(element -> element.getAsJsonObject())
                    .filter(entry -> entry.getAsJsonObject("predicate").get("custom_model_data").getAsInt() == modelData)
                    .findFirst().orElseThrow();
                String path = override.get("model").getAsString();
                assertEquals("neoecoae:" + module.modelPath(false), path);
                assertNotNull(getClass().getResource("/assets/neoecoae/models/" + path.substring(9) + ".json"));
            }
        }
    }

    private static ECOLargeIntegratedWorkingStationInterfaceBlockEntity host(
        AtomicReference<LargeWorkstationModule> module
    ) {
        var host = mock(ECOLargeIntegratedWorkingStationInterfaceBlockEntity.class, CALLS_REAL_METHODS);
        var controller = mock(ECOLargeIntegratedWorkingStationBlockEntity.class);
        var cluster = mock(NEIntegratedWorkingStationCluster.class);
        when(controller.getInstalledModule()).thenAnswer(call -> module.get());
        when(cluster.getController()).thenReturn(controller);
        doReturn(cluster).when(host).getCluster();
        doReturn(Blocks.BARREL.defaultBlockState()).when(host).getBlockState();
        doReturn(Component.literal("Large Integrated Working Station Communication Interface")).when(host).getName();
        return host;
    }
}
