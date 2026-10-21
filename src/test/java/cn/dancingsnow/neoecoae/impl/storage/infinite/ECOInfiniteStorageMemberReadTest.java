package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class ECOInfiniteStorageMemberReadTest {
    @Test
    void readsDoNotCopyLargePayloadOrMutateComponent() {
        var tag = new CompoundTag();
        var domain = UUID.randomUUID();
        tag.putBoolean("neoecoae_infinite_member", true);
        tag.putUUID("neoecoae_infinite_domain", domain);
        var payload = new CompoundTag();
        payload.putIntArray("contents", new int[10000]);
        tag.put("payload", payload);
        var data = spy(tag.copy());
        for (int i = 0; i < 1000; i++) {
            assertTrue(ECOInfiniteStorageMember.isSealedData(data));
            assertTrue(ECOInfiniteStorageMember.isMemberData(data));
            assertNull(ECOInfiniteStorageMember.migrationId(data));
            assertEquals(domain, ECOInfiniteStorageMember.domainId(data).orElseThrow());
        }
        verify(data, never()).copy();
        assertEquals(tag, data.copy());
    }

    @Test
    void replacementComponentIsVisibleImmediately() {
        assertFalse(ECOInfiniteStorageMember.isSealedData(new CompoundTag()));
        var tag = new CompoundTag();
        var migration = UUID.randomUUID();
        tag.putUUID("neoecoae_migration_id", migration);
        var data = tag.copy();
        assertTrue(ECOInfiniteStorageMember.isSealedData(data));
        assertEquals(migration, ECOInfiniteStorageMember.migrationId(data));
        tag.putString("neoecoae_migration_id", "malformed");
        data = tag.copy();
        assertFalse(ECOInfiniteStorageMember.isSealedData(data));
        assertNull(ECOInfiniteStorageMember.migrationId(data));
    }

    @Test
    void emptyDataAndMissingDomainsKeepExistingSemantics() {
        assertFalse(ECOInfiniteStorageMember.isSealed(null));
        var tag = new CompoundTag();
        tag.putBoolean("neoecoae_infinite_member", true);
        var data = tag.copy();
        assertTrue(ECOInfiniteStorageMember.isSealedData(data));
        assertTrue(ECOInfiniteStorageMember.domainId(data).isEmpty());
        assertFalse(ECOInfiniteStorageMember.isSealedData(new CompoundTag()));
        assertFalse(ECOInfiniteStorageMember.isMemberData(new CompoundTag()));
    }

    @Test
    void returningToNormalStorageEndsTheMembershipIdentityLifetime() {
        ItemStack stack = new ItemStack(Items.STONE);
        UUID domain = UUID.randomUUID();
        ECOInfiniteStorageMember.markMember(stack, domain);
        UUID identity = ECOInfiniteStorageMember.getIdentity(stack).orElseThrow();
        assertTrue(ECOInfiniteStorageMember.isMemberOf(stack, domain));

        ECOInfiniteStorageMember.clearMember(stack);

        assertFalse(ECOInfiniteStorageMember.isSealed(stack));
        assertTrue(ECOInfiniteStorageMember.getIdentity(stack).isEmpty());
        ECOInfiniteStorageMember.markMember(stack, domain);
        assertNotEquals(identity, ECOInfiniteStorageMember.getIdentity(stack).orElseThrow());
    }

    @Test
    void exposesMigrationDomainForSafeReinsertion() {
        ItemStack stack = new ItemStack(Items.STONE);
        UUID domain = UUID.randomUUID();
        ECOInfiniteStorageMember.beginMigration(stack, domain);
        assertEquals(domain, ECOInfiniteStorageMember.getMigrationDomainId(stack).orElseThrow());
    }
}
