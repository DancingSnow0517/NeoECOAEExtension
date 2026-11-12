package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.menu.guisync.DataSynchronization;
import appeng.menu.guisync.GuiSync;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.api.stacks.AEKey;
import com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingApi;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.junit.jupiter.api.Assertions.*;

/** Checks actual release descriptors and merge priorities without loading optional menu implementations. */
class CraftConfirmMenuPlanningContractTest {
    private static final String MENU = "appeng/menu/me/crafting/CraftConfirmMenu";
    private static final List<String> PREFIXES = List.of(
        "data_energistics$plan", "molecularmanipulator$plan", "appliedenhancements$plan");

    @Test
    void vanillaPlanningResetIsRequiredEvenWithoutOptionalMods() throws Exception {
        Inject nativeHook = hook("neoecoae$resetPlannerDiagnostics");
        assertArrayEquals(new String[]{"planJob"}, nativeHook.method());
        assertEquals(1, nativeHook.require());
        assertEquals("HEAD", nativeHook.at()[0].value());
        var target = readClass(CraftConfirmMenu.class);
        var entries = target.methods.stream().filter(method -> method.name.equals("planJob")).toList();
        assertFalse(entries.isEmpty());
        entries.forEach(method -> assertEquals(Type.BOOLEAN_TYPE, Type.getReturnType(method.desc)));

        Inject optionalHook = hook("resetExtendedPlannerDiagnostics");
        assertEquals(0, optionalHook.require());
        assertEquals("HEAD", optionalHook.at()[0].value());
        assertFalse(Arrays.asList(optionalHook.method()).contains("planJob"));
    }

    @Test
    void releasedForeignPlanningEntriesAreCoveredAfterTheyAreMerged() throws Exception {
        Set<Path> releases = new HashSet<>();
        releases.add(Path.of(OmniBatchCraftingApi.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        // Supply minimum/current mod releases when checking a supported installation or changing version floors.
        String supplied = System.getenv("ECO_CRAFT_CONFIRM_CONTRACT_JARS");
        if (supplied != null && !supplied.isBlank()) {
            for (String jar : supplied.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (!jar.isBlank()) releases.add(Path.of(jar));
            }
        }
        Set<String> selectors = Set.of(hook("resetExtendedPlannerDiagnostics").method());
        int ourPriority = priority(mixinAnnotation(readClass(CraftConfirmMenuMixin.class)));
        for (Path release : releases) {
            int covered = 0;
            try (var jar = new ZipFile(release.toFile())) {
                assertNotNull(jar.getEntry("META-INF/neoforge.mods.toml"), release + ": expected a NeoForge mod JAR");
                for (var entry : java.util.Collections.list(jar.entries())) {
                    if (!entry.getName().endsWith(".class") || !entry.getName().contains("/mixin")) continue;
                    ClassNode candidate;
                    try (InputStream input = jar.getInputStream(entry)) {
                        candidate = readClass(input);
                    }
                    AnnotationNode mixin = mixinAnnotation(candidate);
                    if (mixin == null || !targetsMenu(mixin)) continue;
                    for (var method : candidate.methods) {
                        if (PREFIXES.stream().noneMatch(method.name::startsWith)) continue;
                        // Revision/status accessors share the prefix but do not start planning an AE key.
                        Type[] arguments = Type.getArgumentTypes(method.desc);
                        if (arguments.length == 0 || !arguments[0].equals(Type.getType(AEKey.class))) continue;
                        assertTrue(selectors.contains(method.name), release + ": unprotected entry " + method.name);
                        assertEquals(Type.BOOLEAN_TYPE, Type.getReturnType(method.desc), release + ": " + method.name);
                        assertTrue(ourPriority > priority(mixin),
                            release + ": diagnostics injector must be allowed to target this merged method");
                        covered++;
                    }
                }
            }
            // Supported older builds, including Omni 2.0.5, do not add a planning entry at all.
            // The required vanilla hook handles those builds; require=0 permits the absent optional selectors.
            System.out.println(release.getFileName() + ": " + covered + " optional planning method(s) covered");
        }
    }

    private static Inject hook(String name) throws Exception {
        return CraftConfirmMenuMixin.class.getDeclaredMethod(name, CallbackInfoReturnable.class)
            .getAnnotation(Inject.class);
    }

    private static ClassNode readClass(Class<?> type) throws Exception {
        try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(input);
            return readClass(input);
        }
    }

    private static ClassNode readClass(InputStream input) throws Exception {
        var node = new ClassNode();
        new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static AnnotationNode mixinAnnotation(ClassNode node) {
        if (node.invisibleAnnotations != null) {
            for (var annotation : node.invisibleAnnotations) {
                if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return annotation;
            }
        }
        if (node.visibleAnnotations != null) {
            for (var annotation : node.visibleAnnotations) {
                if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return annotation;
            }
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation != null && annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    private static int priority(AnnotationNode mixin) {
        Object priority = value(mixin, "priority");
        return priority instanceof Integer explicit ? explicit : 1000;
    }

    private static boolean targetsMenu(AnnotationNode mixin) {
        Object classes = value(mixin, "value");
        if (classes instanceof List<?> values && values.stream().anyMatch(type ->
                type instanceof Type target && target.getInternalName().equals(MENU))) return true;
        Object names = value(mixin, "targets");
        return names instanceof List<?> values && values.contains(MENU.replace('/', '.'));
    }

    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void preservesEcoAndForeignAddonFieldsInFullAndIncrementalUpdates() throws Exception {
        var server = new OtherAddonFields();
        var client = new OtherAddonFields();
        setEco(server, "neoecoae$calculationNanos", 1234567890123L);
        setEco(server, "neoecoae$planningStatusCode", 7);
        setEco(server, "neoecoae$optimizationState", 1);
        setEco(server, "neoecoae$exactPlanReady", true);
        setEco(server, "neoecoae$provenInfeasible", false);
        setEco(server, "neoecoae$reportSyncFailure", 2);
        setEco(server, "neoecoae$theoreticalBytes", "9223372036854775808123");
        setEco(server, "neoecoae$hasByteEstimate", true);
        setEco(server, "neoecoae$completeByteEstimate", true);
        setEco(server, "neoecoae$diagnosticsVersion", 7L);
        server.customProgress = 987654321L;
        server.customDiagnostic = "another addon diagnostic";
        server.customHighId = "another independently assigned range";
        var serverSync = new DataSynchronization(server);
        var clientSync = new DataSynchronization(client);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        try {
            serverSync.writeFull(buffer);
            clientSync.readUpdate(buffer, new it.unimi.dsi.fastutil.shorts.ShortOpenHashSet());
            assertEquals(1234567890123L, getEco(client, "neoecoae$calculationNanos"));
            assertEquals(7, getEco(client, "neoecoae$planningStatusCode"));
            assertEquals(1, getEco(client, "neoecoae$optimizationState"));
            assertEquals(true, getEco(client, "neoecoae$exactPlanReady"));
            assertEquals(2, client.neoecoae$getReportSyncFailure());
            assertEquals(new java.math.BigInteger("9223372036854775808123"), client.neoecoae$getTheoreticalBytes());
            assertTrue(client.neoecoae$hasByteEstimate());
            assertTrue(client.neoecoae$hasCompleteByteEstimate());
            assertEquals(7L, getEco(client, "neoecoae$diagnosticsVersion"));
            assertSame(CraftingGraphSnapshot.EMPTY, client.neoecoae$craftingGraph,
                "Byte metadata must not wait for material pages");
            assertEquals(server.customProgress, client.customProgress);
            assertEquals(server.customDiagnostic, client.customDiagnostic);
            assertEquals(server.customHighId, client.customHighId);

            buffer.clear();
            setEco(server, "neoecoae$planningStatusCode", 9);
            setEco(server, "neoecoae$optimizationState", 2);
            setEco(server, "neoecoae$theoreticalBytes", "0");
            setEco(server, "neoecoae$hasByteEstimate", false);
            setEco(server, "neoecoae$completeByteEstimate", false);
            server.customDiagnostic = "updated addon diagnostic";
            serverSync.writeUpdate(buffer);
            clientSync.readUpdate(buffer, new it.unimi.dsi.fastutil.shorts.ShortOpenHashSet());
            assertEquals(9, getEco(client, "neoecoae$planningStatusCode"));
            assertEquals(2, getEco(client, "neoecoae$optimizationState"));
            assertEquals(java.math.BigInteger.ZERO, client.neoecoae$getTheoreticalBytes());
            assertFalse(client.neoecoae$hasByteEstimate());
            assertFalse(client.neoecoae$hasCompleteByteEstimate());
            assertEquals(1234567890123L, getEco(client, "neoecoae$calculationNanos"));
            assertEquals(server.customDiagnostic, client.customDiagnostic);
            assertEquals(987654321L, client.customProgress);
            assertEquals(server.customHighId, client.customHighId);
        } finally {
            buffer.release();
        }
    }

    @Test
    void retainsAe2RejectionOfUnrelatedDuplicateFields() {
        var error = assertThrows(IllegalStateException.class,
            () -> new DataSynchronization(new ConflictingAddonFields()));
        assertTrue(error.getMessage().contains("same sync id twice: 100"));
    }

    @Test void materialRowsPublishThePlanRevisionBeforeGraphAndRejectStaleGraphCompletions() throws Exception {
        var client = new OtherAddonFields();
        var full = CraftingGraphSnapshot.EMPTY;
        var firstReport = full.materialReport();
        client.neoecoae$setMaterialReport(1, firstReport, ECOCycleItemList.EMPTY);
        assertSame(firstReport, client.neoecoae$craftingGraph);
        assertEquals(1L, client.neoecoae$getDisplayedPlanRevision());
        assertEquals(-1L, getEco(client, "neoecoae$receivedGraphVersion"));

        var nextReport = full.materialReport();
        client.neoecoae$setMaterialReport(2, nextReport, ECOCycleItemList.EMPTY);
        client.neoecoae$setDiagnostics(1, full, ECOCycleItemList.EMPTY);
        assertSame(nextReport, client.neoecoae$craftingGraph);
        assertEquals(2L, client.neoecoae$getDisplayedPlanRevision());
        assertEquals(-1L, getEco(client, "neoecoae$receivedGraphVersion"));

        client.neoecoae$setDiagnostics(2, full, ECOCycleItemList.EMPTY);
        assertEquals(2L, getEco(client, "neoecoae$receivedGraphVersion"));
        client.neoecoae$setMaterialReport(2, nextReport, ECOCycleItemList.EMPTY);
        assertSame(full, client.neoecoae$craftingGraph, "A late table packet cannot replace a full graph");
    }

    @Test void reportEncodingFailureIsContainedAndLeavesNoPendingStream() throws Exception {
        var server = new OtherAddonFields();
        var player = org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        player.containerMenu = org.mockito.Mockito.mock(net.minecraft.world.inventory.AbstractContainerMenu.class);
        org.mockito.Mockito.when(player.registryAccess()).thenReturn(net.minecraft.core.RegistryAccess.EMPTY);
        var invalid = new CraftingGraphSnapshot(-1, java.util.List.of(), java.util.List.of(), java.util.List.of(),
            java.util.List.of(), java.util.List.of(),
            new CraftingGraphSnapshot.Summary("x".repeat(32768), 0, 0, 0, 0, 0));
        var send = CraftConfirmMenuMixin.class.getDeclaredMethod("neoecoae$sendReport",
            net.minecraft.server.level.ServerPlayer.class, CraftingGraphSnapshot.class, boolean.class);
        send.setAccessible(true);
        for (boolean fullGraph : java.util.List.of(false, true)) {
            assertEquals(false, send.invoke(server, player, invalid, fullGraph));
            assertEquals(fullGraph ? 2 : 1, server.neoecoae$getReportSyncFailure());
            assertFalse(cn.dancingsnow.neoecoae.network.MenuDataTransport.busy(player,
                cn.dancingsnow.neoecoae.network.MenuDataTransport.Channel.GRAPH));
        }
    }

    private static void setEco(Object menu, String name, Object value) throws Exception {
        var field = CraftConfirmMenuMixin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(menu, value);
    }

    private static Object getEco(Object menu, String name) throws Exception {
        var field = CraftConfirmMenuMixin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(menu);
    }

    /** Simulates an independent addon occupying the whole legacy low-ID range and another range. */
    public static class OtherAddonFields extends CraftConfirmMenuMixin {
        @GuiSync(99) public boolean customHeader;
        @GuiSync(100) public long customProgress;
        @GuiSync(101) public String customDiagnostic = "";
        @GuiSync(102) public int customMode;
        @GuiSync(103) public int customState;
        @GuiSync(104) public boolean customOption;
        @GuiSync(105) public String customName = "";
        @GuiSync(106) public boolean customAvailable;
        @GuiSync(107) public boolean customReady;
        @GuiSync(108) public long customCount;
        @GuiSync(109) public String customMessage = "";
        @GuiSync(110) public long customVersion;
        @GuiSync(111) public int customTier;
        @GuiSync(112) public boolean customSelected;
        @GuiSync(23456) public String customHighId = "";
    }

    public static class ConflictingAddonFields extends OtherAddonFields {
        @GuiSync(100) public boolean duplicate;
    }
}
