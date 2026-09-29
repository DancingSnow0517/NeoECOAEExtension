package cn.dancingsnow.neoecoae.mixins.client.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import appeng.client.gui.widgets.CPUSelectionList;
import appeng.core.localization.Tooltips;
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
    void storageAmountWrapTargetsReleasedAe2Call() throws IOException, NoSuchMethodException {
        var mixinMethod = CpuSelectionListMixin.class.getDeclaredMethod(
                "wrapStorageAmount", long.class, com.llamalad7.mixinextras.injector.wrapoperation.Operation.class);
        WrapOperation wrap = mixinMethod.getAnnotation(WrapOperation.class);
        assertNotNull(wrap);
        assertEquals("drawBackgroundLayer", wrap.method()[0]);

        var target = new ClassNode();
        try (var stream = CPUSelectionList.class.getResourceAsStream("CPUSelectionList.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(target, 0);
        }

        long matchingCalls = target.methods.stream()
                .filter(method -> method.name.equals("drawBackgroundLayer"))
                .flatMap(method -> StreamSupport.stream(method.instructions.spliterator(), false))
                .filter(instruction -> instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.owner.equals(Type.getInternalName(Tooltips.class))
                        && ("L" + call.owner + ";" + call.name + call.desc).equals(wrap.at()[0].target()))
                .count();
        assertEquals(1, matchingCalls);
    }
}
