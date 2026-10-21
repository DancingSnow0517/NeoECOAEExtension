package cn.dancingsnow.neoecoae.util;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import static org.mockito.Mockito.*;

/** Encode through AE2 15's actual NBT format, then run its real processing-pattern implementation. */
public final class NativePatternTestSupport {
    private NativePatternTestSupport() {}
    public static AEProcessingPattern processing(List<GenericStack> inputs,List<GenericStack> outputs) {
        var tag=new CompoundTag();
        try {
            var encoder=Class.forName("appeng.crafting.pattern.ProcessingPatternEncoding")
                    .getDeclaredMethod("encodeProcessingPattern",CompoundTag.class,GenericStack[].class,GenericStack[].class);
            encoder.setAccessible(true);
            encoder.invoke(null,tag,inputs.toArray(GenericStack[]::new),outputs.toArray(GenericStack[]::new));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        var definition=mock(AEItemKey.class);
        when(definition.getTag()).thenReturn(tag);
        return new AEProcessingPattern(definition);
    }
}
