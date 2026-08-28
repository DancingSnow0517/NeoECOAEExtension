package cn.dancingsnow.neoecoae.api.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class IntegrationInstanceTest {
    @Test
    void identityUsesOnlyStableRegistrationFields() {
        IntegrationInstance first = new IntegrationInstance("example", TestIntegration.class.getName());
        IntegrationInstance sameRegistration = new IntegrationInstance("example", TestIntegration.class.getName());
        IntegrationInstance differentClass = new IntegrationInstance("example", OtherIntegration.class.getName());
        int hashBeforeLoading = first.hashCode();

        first.newInstance();

        assertEquals(hashBeforeLoading, first.hashCode());
        assertEquals(sameRegistration, first);
        assertNotEquals(differentClass, first);
    }

    public static final class TestIntegration {
        public TestIntegration() {
        }

        public void apply() {
        }
    }

    public static final class OtherIntegration {
        public OtherIntegration() {
        }
    }
}
