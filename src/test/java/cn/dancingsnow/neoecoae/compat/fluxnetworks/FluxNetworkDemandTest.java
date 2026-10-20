package cn.dancingsnow.neoecoae.compat.fluxnetworks;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.mixins.compat.fluxnetworks.ServerFluxNetworkMixin;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

class FluxNetworkDemandTest {
    private static long sum(long... requests) throws Exception {
        Method patch = ServerFluxNetworkMixin.class.getDeclaredMethod(
            "neoecoae$limitDemandContribution", long.class, long.class);
        patch.setAccessible(true);
        long total = 0;
        for (long request : requests) {
            // Preserve the original target's LADD after the patched getRequest expression.
            long contribution = (long) patch.invoke(null, request, total);
            assertTrue(contribution >= 0 && contribution <= Math.max(0, request));
            total += contribution;
            assertTrue(total >= 0);
        }
        return total;
    }

    @Test
    void ordinaryDemandAndInvalidNegativeRequests() throws Exception {
        assertEquals(0, sum());
        assertEquals(6000, sum(1000, 0, 2000, 3000));
        assertEquals(1000, sum(Long.MIN_VALUE, 400, -1, 600));
    }

    @Test
    void unlimitedStorageAndOtherConsumersSaturateInEitherOrder() throws Exception {
        assertEquals(Long.MAX_VALUE, sum(Long.MAX_VALUE, 1));
        assertEquals(Long.MAX_VALUE, sum(1000, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, sum(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 1000));
        assertEquals(Long.MAX_VALUE, sum(Long.MAX_VALUE - 1000, 1000));
        assertEquals(Long.MAX_VALUE - 1, sum(Long.MAX_VALUE - 1000, 999));
    }

    @Test
    void mixedNetworksAgreeWithUnboundedArithmetic() throws Exception {
        Random random = new Random(6089446);
        for (int trial = 0; trial < 1000; trial++) {
            long[] requests = new long[random.nextInt(20)];
            BigInteger expected = BigInteger.ZERO;
            for (int i = 0; i < requests.length; i++) {
                requests[i] = random.nextLong();
                expected = expected.add(BigInteger.valueOf(Math.max(0, requests[i])));
            }
            assertEquals(expected.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact(), sum(requests));
        }
    }

    @Test
    void releasedEightZeroZeroHasExactlyOneDemandAdditionInTheInjectionSlice() throws Exception {
        ClassNode target = new ClassNode();
        try (var stream = getClass().getResourceAsStream(
                "/sonar/fluxnetworks/common/connection/ServerFluxNetwork.class")) {
            assertNotNull(stream, "Flux Networks 8.0.0 test fixture is missing");
            new ClassReader(stream).accept(target, 0);
        }
        var tick = target.methods.stream().filter(m -> m.name.equals("onEndServerTick")
            && m.desc.equals("()V")).findFirst().orElseThrow();
        boolean inSlice = false;
        int matches = 0;
        int transferRequests = 0;
        for (var instruction : tick.instructions) {
            if (!(instruction instanceof MethodInsnNode call)
                    || !call.owner.equals("sonar/fluxnetworks/common/connection/TransferHandler")) continue;
            if (call.name.equals("onCycleEnd") && call.desc.equals("()V")) inSlice = true;
            if (!call.name.equals("getRequest") || !call.desc.equals("()J")) continue;
            if (!inSlice) {
                transferRequests++;
                continue;
            }
            matches++;
            assertEquals(Opcodes.LADD, nextOpcode(call).getOpcode());
            var store = assertInstanceOf(VarInsnNode.class, nextOpcode(nextOpcode(call)));
            assertEquals(Opcodes.LSTORE, store.getOpcode());
            int position = tick.instructions.indexOf(call);
            assertEquals(1, tick.localVariables.stream().filter(local -> local.name.equals("limiter")
                && local.desc.equals("J") && local.index == store.var
                && tick.instructions.indexOf(local.start) <= position
                && tick.instructions.indexOf(local.end) > position).count());
        }
        assertEquals(1, matches);
        assertEquals(1, transferRequests, "Actual transfer request must remain outside the patch slice");
    }

    private static AbstractInsnNode nextOpcode(AbstractInsnNode instruction) {
        do {
            instruction = instruction.getNext();
        } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }
}
