package cn.dancingsnow.neoecoae.platform.nbt;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenericStackNbtTest {
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
            tag.putLong("amount", stack.amount());
            return tag;
        });
        stacks.when(() -> GenericStack.readTag(any(), any())).thenAnswer(invocation -> {
            var tag = invocation.<CompoundTag>getArgument(1);
            var key = resolvable.get(tag.getString("id"));
            return key == null ? null : new GenericStack(key, tag.getLong("amount"));
        });
    }

    @AfterEach
    void releaseKeyCodec() {
        stacks.close();
    }

    @Test
    void roundTripKeepsPositiveEntriesInOrderWithSingleEntryLayout() {
        var stoneStack = new GenericStack(stone, 4);
        var dirtStack = new GenericStack(dirt, 1);
        var zero = new GenericStack(ghost, 0);

        ListTag tag = GenericStackNbt.writePositive(registries, Arrays.asList(stoneStack, null, zero, dirtStack));

        assertEquals(2, tag.size());
        assertEquals(GenericStack.writeTag(registries, stoneStack), tag.getCompound(0));
        assertEquals(List.of(stoneStack, dirtStack),
            GenericStackNbt.readBounded(registries, tag, 2, true, stacks -> true).orElseThrow());
    }

    @Test
    void mapWriterDropsNonPositiveAmounts() {
        var amounts = new LinkedHashMap<AEKey, Long>();
        amounts.put(stone, 4L);
        amounts.put(dirt, 0L);

        ListTag tag = GenericStackNbt.writePositiveAmounts(registries, amounts);

        assertEquals(1, tag.size());
        assertEquals(GenericStack.writeTag(registries, new GenericStack(stone, 4)), tag.getCompound(0));
    }

    @Test
    void rejectsListsLongerThanMaxSize() {
        ListTag tag = GenericStackNbt.writePositive(registries, List.of(
            new GenericStack(stone, 1), new GenericStack(dirt, 1), new GenericStack(stone, 2)));

        assertTrue(GenericStackNbt.readBounded(registries, tag, 3, false, stacks -> true).isPresent());
        assertTrue(GenericStackNbt.readBounded(registries, tag, 2, false, stacks -> true).isEmpty());
    }

    @Test
    void requireNonEmptyRejectsOnlyEmptyLists() {
        ListTag empty = new ListTag();

        assertTrue(GenericStackNbt.readBounded(registries, empty, 4, true, stacks -> true).isEmpty());
        assertEquals(List.of(),
            GenericStackNbt.readBounded(registries, empty, 4, false, stacks -> true).orElseThrow());
    }

    @Test
    void validatorRejectionAndMissingEntriesFailTheWholeRead() {
        ListTag tag = GenericStackNbt.writePositive(registries, List.of(new GenericStack(stone, 1)));

        assertTrue(GenericStackNbt.readBounded(registries, tag, 4, true, stacks -> false).isEmpty());

        // An empty compound decodes to null, which must fail the read rather than be skipped.
        tag.add(new CompoundTag());
        assertTrue(GenericStackNbt.readBounded(registries, tag, 4, true, stacks -> true).isEmpty());
    }
}
