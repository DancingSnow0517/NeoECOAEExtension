package cn.dancingsnow.neoecoae.crafting.adapter.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternWrappers;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ECOProviderPatternIntrospectionTest {
    @Test
    void returnsUnwrappedPatternDirectlyWhenNoWrapperMethodsExist() {
        IPatternDetails pattern = mock(IPatternDetails.class);

        assertSame(pattern, ECOProviderPatternIntrospection.unwrap(pattern));
    }

    @Test
    void followsExplicitWrapperContract() {
        IPatternDetails base = mock(IPatternDetails.class);
        IPatternDetails wrapped = mock(IPatternDetails.class,
            withSettings().extraInterfaces(ECOPatternWrappers.Wrapper.class));
        when(((ECOPatternWrappers.Wrapper) wrapped).ecoProviderPattern()).thenReturn(base);

        assertSame(base, ECOProviderPatternIntrospection.unwrap(wrapped));
        assertEquals(List.of(wrapped, base), ECOProviderPatternIntrospection.wrapperChain(wrapped));
    }

    @Test
    void methodNameAloneDoesNotAuthorizeUnwrapping() {
        IPatternDetails wrapped = mock(IPatternDetails.class, withSettings().extraInterfaces(Wrapper.class));
        when(((Wrapper) wrapped).delegate()).thenThrow(new AssertionError("Unregistered accessor called"));
        assertSame(wrapped, ECOProviderPatternIntrospection.unwrap(wrapped));
    }

    @Test
    void registeredOptionalContractIsFollowed() {
        ECOPatternWrappers.register(ResourceLocation.fromNamespaceAndPath("neoecoae_test", "wrapper"),
            0, RegisteredWrapper.class, RegisteredWrapper::delegate);
        IPatternDetails base = mock(IPatternDetails.class);
        IPatternDetails wrapped = mock(IPatternDetails.class, withSettings().extraInterfaces(RegisteredWrapper.class));
        when(((RegisteredWrapper) wrapped).delegate()).thenReturn(base);
        assertSame(base, ECOProviderPatternIntrospection.unwrap(wrapped));
    }

    @Test
    void rejectsCyclesAndFailedContracts() {
        IPatternDetails wrapped = mock(IPatternDetails.class,
            withSettings().extraInterfaces(ECOPatternWrappers.Wrapper.class));
        when(((ECOPatternWrappers.Wrapper) wrapped).ecoProviderPattern()).thenReturn(wrapped);
        assertNull(ECOProviderPatternIntrospection.unwrap(wrapped));
        when(((ECOPatternWrappers.Wrapper) wrapped).ecoProviderPattern()).thenReturn(null);
        assertNull(ECOProviderPatternIntrospection.unwrap(wrapped));
        when(((ECOPatternWrappers.Wrapper) wrapped).ecoProviderPattern()).thenThrow(new IllegalStateException());
        assertNull(ECOProviderPatternIntrospection.unwrap(wrapped));
    }

    @Test
    void wrapperRegistrationUsesPriorityAndRejectsDuplicateIds() {
        var low = ResourceLocation.fromNamespaceAndPath("neoecoae_test", "low_priority_wrapper");
        var high = ResourceLocation.fromNamespaceAndPath("neoecoae_test", "high_priority_wrapper");
        IPatternDetails base = mock(IPatternDetails.class);
        IPatternDetails wrong = mock(IPatternDetails.class);
        ECOPatternWrappers.register(low, 0, PriorityWrapper.class, wrapper -> wrong);
        ECOPatternWrappers.register(high, 10, PriorityWrapper.class, wrapper -> base);
        IPatternDetails wrapped = mock(IPatternDetails.class, withSettings().extraInterfaces(PriorityWrapper.class));
        assertSame(base, ECOProviderPatternIntrospection.unwrap(wrapped));
        assertThrows(IllegalArgumentException.class,
            () -> ECOPatternWrappers.register(high, 10, PriorityWrapper.class, wrapper -> wrong));
    }

    public interface Wrapper {
        IPatternDetails delegate();
    }

    public interface RegisteredWrapper {
        IPatternDetails delegate();
    }

    public interface PriorityWrapper {}
}
