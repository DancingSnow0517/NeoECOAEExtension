package cn.dancingsnow.neoecoae.api.me.bigorder;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import java.math.BigInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("neoecoae")
@PrefixGameTestTemplate(false)
public final class ECOExactInventoryGameTest {
    @GameTest(template = "empty")
    public static void registeredKeyRetainsExactBalanceAcrossNbt(GameTestHelper helper) {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var expected = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(17));
        var original = new ECOExactInventory(ignored -> {});
        original.setEnabled(true);
        original.insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        original.insert(key, 17, Actionable.MODULATE);

        var restored = new ECOExactInventory(ignored -> {});
        restored.readFromNBT(original.writeToNBT());
        helper.assertTrue(restored.isEnabled(), "exact inventory was not enabled after NBT restore");
        helper.assertTrue(restored.amount(key).equals(expected), "exact amount changed across NBT restore");
        helper.assertTrue(
                restored.extract(key, Long.MAX_VALUE, Actionable.MODULATE) == Long.MAX_VALUE,
                "extract did not return the requested amount");
        helper.assertTrue(restored.amount(key).equals(BigInteger.valueOf(17)), "remaining balance was truncated");
        helper.succeed();
    }
}
