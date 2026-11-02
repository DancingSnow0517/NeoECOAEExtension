package cn.dancingsnow.neoecoae.mixins.compat.useless;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

class ECOUselessMixinSelectionTest {
    private static final String PACKAGE = "cn.dancingsnow.neoecoae.mixins.compat.useless.";
    private static final String PLUGIN = PACKAGE + "ECOUselessMixinPlugin";
    private static final String MIXIN = PACKAGE + "UselessSmartDoublingPlansMixin";
    private static final String TARGET =
        "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlans";
    private static final String RESOURCE = TARGET.replace('.', '/') + ".class";
    private static final String PLAN = "Lappeng/api/networking/crafting/ICraftingPlan;";

    @Test
    void legacyOverloadsRemainProtectedWithoutLoadingUselessClasses() throws Exception {
        assertTrue(plugin(target(false, null)).shouldApplyMixin(TARGET, MIXIN));
        assertTrue(plugin(target(true, null)).shouldApplyMixin(TARGET, MIXIN));
    }

    @Test
    void nativeApiAndBackportsRetireOnlyTheSubmissionHook() throws Exception {
        for (String owner : new String[]{
                "com/sorrowmist/useless/compat/neoecoae/NeoEcoPlanningCompat",
                "cn/dancingsnow/neoecoae/api/me/planning/ECOPlanningResultRegistry"}) {
            for (boolean levelOverload : new boolean[]{false, true}) {
                var plugin = plugin(target(levelOverload, owner));
                assertFalse(plugin.shouldApplyMixin(TARGET, MIXIN));
                assertTrue(plugin.shouldApplyMixin("unused", PACKAGE + "UselessDynamicPatternAccessor"));
                assertTrue(plugin.shouldApplyMixin("unused", PACKAGE + "UselessScaledPatternAccessor"));
            }
        }
    }

    @Test
    void absentOrChangedTargetsDoNotAttemptLegacyInjection() throws Exception {
        assertFalse(plugin(null).shouldApplyMixin(TARGET, MIXIN));
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET.replace('.', '/'), null, "java/lang/Object", null);
        writer.visitEnd();
        assertFalse(plugin(writer.toByteArray()).shouldApplyMixin(TARGET, MIXIN));
    }

    @Test
    void oneUnprotectedOverloadStillNeedsTheLegacyHooks() throws Exception {
        var nativeTarget = new ClassNode();
        new ClassReader(target(false, "com/sorrowmist/useless/compat/neoecoae/NeoEcoPlanningCompat"))
            .accept(nativeTarget, 0);
        var legacyTarget = new ClassNode();
        new ClassReader(target(true, null)).accept(legacyTarget, 0);
        nativeTarget.methods.addAll(legacyTarget.methods);
        var writer = new ClassWriter(0);
        nativeTarget.accept(writer);
        assertTrue(plugin(writer.toByteArray()).shouldApplyMixin(TARGET, MIXIN));
    }

    @Test
    void releasedBytecodeSelectsLegacyProtectionOrNativeApi() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input);
            assertTrue(plugin(input.readAllBytes()).shouldApplyMixin(TARGET, MIXIN));
        }
        inspectRelease("ECO_USELESS_CONTRACT_JAR", true);
        inspectRelease("ECO_USELESS_NATIVE_CONTRACT_JAR", false);
    }

    @Test
    void mixinConfigurationInstallsTheSelector() throws Exception {
        try (var input = getClass().getClassLoader()
                .getResourceAsStream("neoecoae.compat.useless_mod.mixins.json")) {
            assertNotNull(input);
            var config = JsonParser.parseReader(new InputStreamReader(input)).getAsJsonObject();
            assertEquals(PLUGIN, config.get("plugin").getAsString());
        }
    }

    private void inspectRelease(String environment, boolean expected) throws Exception {
        String release = System.getenv(environment);
        if (release == null || release.isBlank()) return;
        try (var jar = new ZipFile(Path.of(release).toFile());
                var input = jar.getInputStream(jar.getEntry(RESOURCE))) {
            assertEquals(expected, plugin(input.readAllBytes()).shouldApplyMixin(TARGET, MIXIN), release);
        }
    }

    private IMixinConfigPlugin plugin(byte[] target) throws Exception {
        ClassLoader parent = getClass().getClassLoader();
        var loader = new ClassLoader(parent) {
            @Override
            public InputStream getResourceAsStream(String name) {
                return name.equals(RESOURCE)
                    ? target == null ? null : new ByteArrayInputStream(target)
                    : super.getResourceAsStream(name);
            }

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.sorrowmist.useless.")) {
                    throw new AssertionError("Mixin selection must not load optional target classes: " + name);
                }
                if (!name.equals(PLUGIN)) return super.loadClass(name, resolve);
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    try (var input = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                        byte[] bytes = input.readAllBytes();
                        result = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(result);
                return result;
            }
        };
        return (IMixinConfigPlugin) loader.loadClass(PLUGIN).getConstructor().newInstance();
    }

    private static byte[] target(boolean levelOverload, String apiOwner) {
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET.replace('.', '/'), null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "rewriteForSubmission",
            "(" + PLAN + "Ljava/util/function/Function;"
                + (levelOverload ? "Lnet/minecraft/world/level/Level;" : "") + ")" + PLAN, null, null);
        method.visitCode();
        if (apiOwner != null) {
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, apiOwner, "shouldPreserveSubmissionPlan",
                "(" + PLAN + ")Z", false);
            method.visitInsn(Opcodes.POP);
        }
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
