package cn.dancingsnow.neoecoae.compat.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import appeng.api.crafting.IPatternDetails;
import java.util.List;
import org.junit.jupiter.api.Test;

class ECOProviderPatternIntrospectionTest {
    @Test
    void returnsUnwrappedPatternDirectlyWhenNoWrapperMethodsExist() {
        IPatternDetails pattern = mock(IPatternDetails.class);

        assertSame(pattern, ECOProviderPatternIntrospection.unwrap(pattern));
    }

    @Test
    void followsCachedWrapperAccessorChain() {
        IPatternDetails base = mock(IPatternDetails.class);
        IPatternDetails wrapped = mock(IPatternDetails.class, withSettings().extraInterfaces(Wrapper.class));
        when(((Wrapper) wrapped).delegate()).thenReturn(base);

        assertSame(base, ECOProviderPatternIntrospection.unwrap(wrapped));
        assertEquals(List.of(wrapped, base), ECOProviderPatternIntrospection.wrapperChain(wrapped));
    }

    public interface Wrapper {
        IPatternDetails delegate();
    }
}
