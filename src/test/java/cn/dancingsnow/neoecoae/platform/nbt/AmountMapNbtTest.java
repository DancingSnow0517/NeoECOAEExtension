package cn.dancingsnow.neoecoae.platform.nbt;

import cn.dancingsnow.neoecoae.platform.nbt.AmountMapNbt.InvalidEntryPolicy;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AmountMapNbtTest {
    private final HolderLookup.Provider registries = mock(HolderLookup.Provider.class);
    private final AEKey stone = mock(AEKey.class);
    private final AEKey dirt = mock(AEKey.class);
    private final AEKey ghost = mock(AEKey.class);
    private final Map<AEKey, String> ids = Map.of(stone, "stone", dirt, "dirt", ghost, "ghost");
    private final Map<String, AEKey> resolvable = Map.of("stone", stone, "dirt", dirt);
    private MockedStatic<GenericStack> stacks;

    @BeforeEach
    void stubKeyCodec() {
        stacks = mockStatic(GenericStack.class, CALLS_REAL_METHODS);
        // "ghost" is written like any key but never resolves, which is how an unknown or removed key looks on load.
        stacks.when(() -> GenericStack.writeTag(any(), any())).thenAnswer(invocation -> {
            var stack = invocation.<GenericStack>getArgument(1);
            var tag = new CompoundTag();
            tag.putString("id", ids.get(stack.what()));
            return tag;
        });
        stacks.when(() -> GenericStack.readTag(any(), any())).thenAnswer(invocation -> {
            var key = resolvable.get(invocation.<CompoundTag>getArgument(1).getString("id"));
            return key == null ? null : new GenericStack(key, 1);
        });
    }

    @AfterEach
    void releaseKeyCodec() {
        stacks.close();
    }

    @Test
    void roundTripKeepsEveryAmountAndUsesKeyAmountLayout() {
        var amounts = new LinkedHashMap<AEKey, BigInteger>();
        amounts.put(stone, BigInteger.TEN.pow(20));
        amounts.put(dirt, BigInteger.valueOf(7));

        var list = AmountMapNbt.write(amounts, registries);

        assertEquals(2, list.size());
        assertEquals(Set.of("key", "amount"), list.getCompound(0).getAllKeys());
        assertEquals("100000000000000000000", list.getCompound(0).getString("amount"));
        var restored = new HashMap<AEKey, BigInteger>();
        AmountMapNbt.read(list, registries, restored, InvalidEntryPolicy.THROW);
        assertEquals(amounts, restored);
    }

    @Test
    void skipDropsUnresolvedEntriesWithoutParsingTheirAmountAndKeepsZero() {
        var list = new ListTag();
        list.add(entry("ghost", "not-a-number"));
        list.add(entry("stone", "0"));
        var target = new HashMap<AEKey, BigInteger>();

        AmountMapNbt.read(list, registries, target, InvalidEntryPolicy.SKIP);

        assertEquals(Map.of(stone, BigInteger.ZERO), target);
    }

    @Test
    void throwRejectsUnresolvedKey() {
        var list = new ListTag();
        list.add(entry("ghost", "1"));

        assertThrows(IllegalArgumentException.class,
                () -> AmountMapNbt.read(list, registries, new HashMap<>(), InvalidEntryPolicy.THROW));
    }

    @Test
    void throwRejectsZeroAmount() {
        var list = new ListTag();
        list.add(entry("stone", "0"));

        assertThrows(IllegalArgumentException.class,
                () -> AmountMapNbt.read(list, registries, new HashMap<>(), InvalidEntryPolicy.THROW));
    }

    @Test
    void bothPoliciesRejectMalformedAmounts() {
        for (var policy : InvalidEntryPolicy.values()) {
            for (var malformed : List.of("", "-1", "1.5", "abc")) {
                var list = new ListTag();
                list.add(entry("stone", malformed));
                assertThrows(IllegalArgumentException.class,
                        () -> AmountMapNbt.read(list, registries, new HashMap<>(), policy), policy + " " + malformed);
            }
        }
    }

    private static CompoundTag entry(String keyId, String amount) {
        var key = new CompoundTag();
        key.putString("id", keyId);
        var entry = new CompoundTag();
        entry.put("key", key);
        entry.putString("amount", amount);
        return entry;
    }
}
