package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.syncdata.IManaged;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RPCMethod;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ManagedBlockEntityFieldsTest {
    abstract static class Parent implements IManaged {
        @Persisted int saved;
        @RPCMethod public void request() {}
    }

    abstract static class Child extends Parent {
        @DescSynced boolean active;
        int transientValue;
    }

    @Test
    void retainsInheritedFieldsAndRpcMethodsWithoutIncludingUnmanagedFields() {
        var holder = ManagedBlockEntityFields.get(Child.class);
        Set<String> fields = Arrays.stream(holder.getFields()).map(field -> field.getName())
            .collect(Collectors.toSet());
        assertEquals(Set.of("saved", "active"), fields);
        assertTrue(holder.getSyncedFieldIndex("saved").isPersist());
        assertTrue(holder.getSyncedFieldIndex("active").isDestSync());
        assertTrue(holder.getRpcMethodMap().containsKey("request"));
        assertThrows(IllegalArgumentException.class, () -> holder.getSyncedFieldIndex("transientValue"));
    }

    @Test
    void cachesConcreteMetadataWithoutMutatingTheParent() {
        var parent = ManagedBlockEntityFields.get(Parent.class);
        var child = ManagedBlockEntityFields.get(Child.class);
        assertSame(child, ManagedBlockEntityFields.get(Child.class));
        assertEquals(1, parent.getFields().length);
        assertEquals(2, child.getFields().length);
    }
}
