package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;

/** Logical transaction carried by bounded fragments. Zero is a deletion, never a visible amount. */
public record ECOExactStoragePayload(boolean reset, Map<AEKey, BigInteger> amounts) {
    private static final int MAX_ENTRIES = 1_000_000;
    private static final int MAX_INTEGER_BYTES = 65536;

    public ECOExactStoragePayload {
        amounts = Map.copyOf(amounts);
    }

    public static ECOExactStoragePayload difference(Map<AEKey, BigInteger> before, Map<AEKey, BigInteger> after) {
        Map<AEKey, BigInteger> delta = new HashMap<>();
        after.forEach((key, value) -> {
            if (!value.equals(before.get(key))) delta.put(key, value);
        });
        before.keySet().forEach(key -> {
            if (!after.containsKey(key)) delta.put(key, BigInteger.ZERO);
        });
        return new ECOExactStoragePayload(false, delta);
    }

    public Map<AEKey, BigInteger> apply(Map<AEKey, BigInteger> previous) {
        Map<AEKey, BigInteger> result = reset ? new HashMap<>() : new HashMap<>(previous);
        amounts.forEach((key, value) -> {
            if (value.signum() == 0) result.remove(key);
            else result.put(key, value);
        });
        return Map.copyOf(result);
    }

    public static void encode(ECOExactStoragePayload payload, FriendlyByteBuf buffer) {
        if (payload.amounts.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many exact amounts");
        buffer.writeBoolean(payload.reset);
        buffer.writeVarInt(payload.amounts.size());
        payload.amounts.forEach((key, amount) -> {
            if (amount.signum() < 0 || amount.toByteArray().length > MAX_INTEGER_BYTES)
                throw new IllegalArgumentException("Invalid exact amount");
            AEKey.writeKey(buffer, key);
            buffer.writeByteArray(amount.toByteArray());
        });
    }

    public static ECOExactStoragePayload decode(FriendlyByteBuf buffer) {
        boolean reset = buffer.readBoolean();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Invalid exact inventory size");
        Map<AEKey, BigInteger> amounts = new HashMap<>();
        for (int i = 0; i < size; i++) {
            AEKey key = AEKey.readKey(buffer);
            BigInteger amount = new BigInteger(buffer.readByteArray(MAX_INTEGER_BYTES));
            if (key == null || amount.signum() < 0 || amounts.put(key, amount) != null)
                throw new IllegalArgumentException("Invalid exact inventory entry");
        }
        return new ECOExactStoragePayload(reset, amounts);
    }

    public void applyToMenu(appeng.menu.me.common.MEStorageMenu menu) {
        Client.apply(menu, this);
    }

    private static final class Client {
        private static void apply(appeng.menu.me.common.MEStorageMenu storageMenu, ECOExactStoragePayload payload) {
            var menu = (cn.dancingsnow.neoecoae.api.me.ECOExactStorageMenu) storageMenu;
            menu.neoecoae$setExactAmounts(payload.apply(menu.neoecoae$getExactAmounts()));
            if (storageMenu.getClientRepo() instanceof appeng.client.gui.me.common.Repo repo
                    && storageMenu.getConfigManager().getSetting(appeng.api.config.Settings.SORT_BY)
                            == appeng.api.config.SortOrder.AMOUNT) repo.updateView();
        }
    }
}
