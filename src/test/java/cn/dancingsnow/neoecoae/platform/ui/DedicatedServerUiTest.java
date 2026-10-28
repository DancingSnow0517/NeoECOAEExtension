package cn.dancingsnow.neoecoae.platform.ui;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.io.IOException;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.RuntimeDistCleaner;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.*;

/** Uses Forge's real side cleaner, rather than loading untransformed client bytecode in server tests. */
class DedicatedServerUiTest {
    private static final String ROOT = "cn.dancingsnow.neoecoae.";
    private static final String TEXTURES = ROOT + "platform.ui.runtime.gui.texture.";

    @Test void sharedTexturesInitializeAndComposeWithoutClientClasses() throws Exception {
        var cleaner = cleaner(Dist.DEDICATED_SERVER);
        var loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.minecraft.client.") || name.startsWith("com.mojang.blaze3d.")) {
                    throw new ClassNotFoundException("Dedicated server has no " + name);
                }
                if (!name.startsWith(TEXTURES)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    var type = findLoadedClass(name);
                    if (type == null) {
                        try {
                            var node = transformed(name, cleaner);
                            var writer = new ClassWriter(0);
                            node.accept(writer);
                            var bytes = writer.toByteArray();
                            type = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException failure) {
                            throw new ClassNotFoundException(name, failure);
                        }
                    }
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        };
        var texture = Class.forName(TEXTURES + "IGuiTexture", true, loader);
        var empty = texture.getField("EMPTY").get(null);
        assertSame(empty, texture.getMethod("copy").invoke(empty));
        var array = java.lang.reflect.Array.newInstance(texture, 3);
        java.lang.reflect.Array.set(array, 0, empty);
        java.lang.reflect.Array.set(array, 2, empty);
        var group = texture.getMethod("group", array.getClass()).invoke(null, array);
        assertSame(group, texture.getMethod("getRawTexture").invoke(group));
        for (String name : List.of("ColorRectTexture", "ColorBorderTexture", "SpriteTexture",
                "ItemStackTexture", "UIResourceTexture", "VanillaSpriteTexture")) {
            assertDoesNotThrow(() -> Class.forName(TEXTURES + name, true, loader));
        }
    }

    @Test void factoryReadersAndTheirCompilerBridgesAreClientOnly() throws Exception {
        for (String name : List.of(ROOT + "platform.ui.runtime.gui.factory.BlockUIMenuType$1",
                ROOT + "compat.ae2.ECORemoteMachineUI$1")) {
            var server = transformed(name, cleaner(Dist.DEDICATED_SERVER));
            var client = transformed(name, cleaner(Dist.CLIENT));
            assertTrue(server.methods.stream().noneMatch(method -> method.name.equals("readHolderFromSyncData")), name);
            assertEquals(2, client.methods.stream().filter(method -> method.name.equals("readHolderFromSyncData")).count(), name);
        }
    }

    @Test void commonModularUiRetainsOnlyTheExplicitPlayerConstructorOnServers() throws Exception {
        var server = transformed(ROOT + "platform.ui.runtime.gui.ui.ModularUI", cleaner(Dist.DEDICATED_SERVER));
        var constructors = server.methods.stream().filter(method -> method.name.equals("<init>")).toList();
        assertEquals(1, constructors.size());
        assertTrue(constructors.get(0).desc.contains("Lnet/minecraft/world/entity/player/Player;"));
        var client = transformed(ROOT + "platform.ui.runtime.gui.ui.ModularUI", cleaner(Dist.CLIENT));
        assertEquals(2, client.methods.stream().filter(method -> method.name.equals("<init>")).count());
    }

    private static RuntimeDistCleaner cleaner(Dist dist) {
        var cleaner = new RuntimeDistCleaner();
        cleaner.<java.util.function.Consumer<Dist>>getExtension().accept(dist);
        return cleaner;
    }

    private static ClassNode transformed(String name, RuntimeDistCleaner cleaner) throws IOException {
        try (var stream = DedicatedServerUiTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (stream == null) throw new IOException("Missing production class: " + name);
            var node = new ClassNode();
            new ClassReader(stream.readAllBytes()).accept(node, 0);
            cleaner.processClassWithFlags(ILaunchPluginService.Phase.AFTER, node, Type.getObjectType(node.name), "ui-regression");
            return node;
        }
    }
}
