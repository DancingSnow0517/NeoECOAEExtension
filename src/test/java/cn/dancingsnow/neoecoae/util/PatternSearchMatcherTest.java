package cn.dancingsnow.neoecoae.util;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class PatternSearchMatcherTest {
    @TempDir Path directory;

    @Test void releasedJecMatchesFullPinyinInitialsMixedTextAndFluids() throws Exception {
        // NeoForge normally expands nested jars. An isolated JVM needs JEC's bundled PinIn explicitly.
        Path pinIn = directory.resolve("PinIn.jar");
        ClassLoader parent = getClass().getClassLoader();
        try (var input = parent.getResourceAsStream("META-INF/jarjar/PinIn-1.6.0.jar")) {
            assertNotNull(input, "released JEC must supply the pinyin engine");
            Files.copy(input, pinIn);
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{pinIn.toUri().toURL()}, parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("me.towdium.")) return super.loadClass(name, resolve);
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    try (var input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(name);
                        byte[] bytes = input.readAllBytes();
                        result = defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(result);
                return result;
            }
        }) {
            var matcher = PatternSearchMatcher.load(loader);
            assertTrue(matcher.test("铁锭", "tieding"));
            assertTrue(matcher.test("铁锭", "td"));
            assertTrue(matcher.test("铁锭 gear", "td gear"));
            assertTrue(matcher.test("液态水", "yts"));
            assertTrue(matcher.test("钻石", "zuanshi"));
            assertFalse(matcher.test("铜锭", "tieding"));
            assertFalse(matcher.test("铁锭", "gear"));
        }
    }

    @Test void absentJecKeepsLiteralSearchWithoutLoadingGameOrViewerClasses() {
        var matcher = PatternSearchMatcher.load(new ClassLoader(null) { });
        assertTrue(matcher.test("iron ingot 铁锭", "iron"));
        assertTrue(matcher.test("iron ingot 铁锭", "铁锭"));
        assertFalse(matcher.test("iron ingot 铁锭", "tieding"));
    }
}
