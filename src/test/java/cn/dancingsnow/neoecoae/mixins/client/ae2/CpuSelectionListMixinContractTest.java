package cn.dancingsnow.neoecoae.mixins.client.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.InfoBar;
import appeng.menu.me.crafting.CraftingStatusMenu;
import java.io.IOException;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

class CpuSelectionListMixinContractTest {
    @Test
    void storageWrapTargetsReleasedAe2Call() throws IOException, NoSuchMethodException {
        var mixinMethod = CpuSelectionListMixin.class.getDeclaredMethod(
                "wrapStorageAdd", InfoBar.class, String.class, int.class, float.class, int.class, int.class,
                com.llamalad7.mixinextras.injector.wrapoperation.Operation.class,
                CraftingStatusMenu.CraftingCpuListEntry.class);
        WrapOperation wrap = mixinMethod.getAnnotation(WrapOperation.class);
        assertNotNull(wrap);
        assertEquals("drawBackgroundLayer", wrap.method()[0]);
        assertEquals(1, wrap.at()[0].ordinal());

        var target = new ClassNode();
        try (var stream = CPUSelectionList.class.getResourceAsStream("CPUSelectionList.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(target, 0);
        }

        long matchingCalls = target.methods.stream()
                .filter(method -> method.name.equals("drawBackgroundLayer"))
                .flatMap(method -> StreamSupport.stream(method.instructions.spliterator(), false))
                .filter(instruction -> instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && call.owner.equals(Type.getInternalName(InfoBar.class))
                        && ("L" + call.owner + ";" + call.name + call.desc).equals(wrap.at()[0].target()))
                .count();
        assertEquals(3, matchingCalls);
        var draw = target.methods.stream().filter(method -> method.name.equals("drawBackgroundLayer"))
                .findFirst().orElseThrow();
        var storageCall = StreamSupport.stream(draw.instructions.spliterator(), false)
                .filter(instruction -> instruction instanceof MethodInsnNode call
                        && ("L" + call.owner + ";" + call.name + call.desc).equals(wrap.at()[0].target()))
                .skip(wrap.at()[0].ordinal()).findFirst().orElseThrow();
        int storageCallIndex = draw.instructions.indexOf(storageCall);
        var cpu = draw.localVariables.stream().filter(local -> local.name.equals("cpu"))
                .findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(draw.instructions.indexOf(cpu.start) <= storageCallIndex
                && storageCallIndex < draw.instructions.indexOf(cpu.end));
    }
}
