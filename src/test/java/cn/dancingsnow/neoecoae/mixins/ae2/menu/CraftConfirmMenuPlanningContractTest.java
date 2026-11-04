package cn.dancingsnow.neoecoae.mixins.ae2.menu;

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
        Inject nativeHook = hook("resetPlannerDiagnostics");
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
}
