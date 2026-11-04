package cn.dancingsnow.neoecoae.compat.processing;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProcessingReflectionCacheTest {
    public static class Parent {
        int value;
        public int value() { return value; }
        private int hidden() { return 17; }
    }

    public static final class Child extends Parent {}

    @Test
    void inheritedAccessorIsReusedWhileInstancesAndValuesStayLive() throws Exception {
        var first = new Child();
        var second = new Child();
        first.value = 3;
        second.value = 7;
        var method = ProcessingReflection.publicNoArgMethod(Child.class, "value");
        assertSame(method, ProcessingReflection.publicNoArgMethod(Child.class, "value"));
        assertEquals(3, method.invoke(first));
        assertEquals(7, method.invoke(second));
        first.value = 11;
        assertEquals(11, method.invoke(first));
    }

    @Test
    void publicAbsenceDoesNotReuseThePrivateProcessingAccessor() {
        var child = new Child();
        assertNull(ProcessingReflection.publicNoArgMethod(Child.class, "hidden"));
        assertEquals(17, ProcessingReflection.call(child, "hidden"));
        assertNull(ProcessingReflection.publicNoArgMethod(Child.class, "hidden"));
        assertNull(ProcessingReflection.publicNoArgMethod(Child.class, "absent"));
        assertNull(ProcessingReflection.publicNoArgMethod(Child.class, "absent"));
    }
}
