package cn.dancingsnow.neoecoae.compat.useless;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalBigIntegerTarget;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOUselessExactCraftingDispatchTest {
    @BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }

    @Test void recipePatternsAndUnknownTargetsRetainNativeAdmission() {
        var core = mock(MultiblockAlloyFurnaceCoreBlockEntity.class);
        var target = new OmniversalBigIntegerTarget(core, "test");
        var context = new ECOBatchDispatchContext(mock(IPatternDetails.class),
            List.of(), List.of(), List.of(), null, null);
        assertNull(ECOUselessExactCraftingDispatch.prepare(target, context, BigInteger.TEN));
        assertNull(ECOUselessExactCraftingDispatch.prepare(new Object(), context, BigInteger.TEN));
        verifyNoInteractions(core);
    }

}
