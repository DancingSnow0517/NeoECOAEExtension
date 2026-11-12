package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.ChemicalStack;

/** Only ECO-produced keys are marked shared; public addon factories are never intercepted. */
public final class ECOChemicalKeys {
    private ECOChemicalKeys() {}
    private static final boolean SHAREABLE = SharedChemicalKey.class.isAssignableFrom(MekanismKey.class);
    public static MekanismKey of(ChemicalStack stack) {
        Object epoch = KeyReuse.epoch();
        if (epoch == null || !SHAREABLE || stack.isEmpty()) return MekanismKey.of(stack);
        KeyReuseSlot slot = KeyReuse.slot(epoch, MekanismKey.class, stack.getChemical());
        if (slot == null) return MekanismKey.of(stack);
        MekanismKey key = (MekanismKey) slot.get(epoch);
        if (key != null) return key;
        synchronized (slot) {
            key = (MekanismKey) slot.get(epoch);
            if (key == null) {
                key = MekanismKey.of(stack);
                // Without the optional instance-view adapter it is unsafe to share mutable addon stacks.
                if ((Object) key instanceof SharedChemicalKey shared) {
                    // This fresh upstream key owns its copy; never normalize the caller's mutable stack.
                    key.getStack().setAmount(1);
                    shared.neoecoae$markShared();
                    slot.set(epoch, key);
                }
            }
            return key;
        }
    }
    static AEKey canonicalize(AEKey key) {
        if (!SHAREABLE) return key;
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot = KeyReuse.slot(epoch, MekanismKey.class, key.getPrimaryKey());
        if (slot == null) return key;
        AEKey cached = (AEKey) slot.get(epoch);
        if (cached != null) return key.equals(cached) ? cached : key;
        AEKey owned = of(((MekanismKey) key).getStack());
        return key.equals(owned) ? owned : key;
    }
}
