package cn.dancingsnow.neoecoae.mixins.keyreuse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.slf4j.LoggerFactory;

/** Inspect bytecode without initializing optional mods. Unrecognized adapters retain upstream factories. */
public final class KeyReuseMixinPlugin implements IMixinConfigPlugin {
    private final Map<String, Boolean> supported = new HashMap<>();

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("ResourceKeyOwnerMixin")) return true;
        String family = mixinClassName.contains("Chemical") || mixinClassName.contains("Mekanism") ? "chemical"
            : mixinClassName.contains("Energy") || mixinClassName.contains("Flux") ? "energy"
            : (mixinClassName.contains("AEItem") || mixinClassName.contains("InternedTag")) ? "item" : "fluid";
        return supported.computeIfAbsent(family, this::inspect);
    }

    private boolean inspect(String family) {
        String type = switch (family) {
            case "item" -> "appeng.api.stacks.AEItemKey";
            case "fluid" -> "appeng.api.stacks.AEFluidKey";
            case "chemical" -> "me.ramidzkh.mekae2.ae2.MekanismKey";
            default -> "com.glodblock.github.appflux.common.me.key.FluxKey";
        };
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(type);
            boolean valid;
            if (family.equals("item")) {
                valid = has(node, "of", "(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEItemKey;")
                    && has(node, "serializeStackCaps", "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/nbt/CompoundTag;")
                    && has(node, "<init>", "(Lnet/minecraft/world/item/Item;Lappeng/api/stacks/AEItemKey$InternedTag;Lappeng/api/stacks/AEItemKey$InternedTag;)V");
                ClassNode interned = MixinService.getService().getBytecodeProvider().getClassNode("appeng.api.stacks.AEItemKey$InternedTag");
                valid &= interned.fields.stream().anyMatch(field -> field.name.equals("tag") && field.desc.equals("Lnet/minecraft/nbt/CompoundTag;"));
                valid &= node.fields.stream().anyMatch(field -> field.name.equals("maxStackSize") && field.desc.equals("I"));
                valid &= has(node, "fromPacket", "(Lnet/minecraft/network/FriendlyByteBuf;)Lappeng/api/stacks/AEItemKey;")
                    && writesStackSize(node)
                    && callsFactory(node, "of", "(Lnet/minecraft/world/item/ItemStack;)Lappeng/api/stacks/AEItemKey;")
                    && callsFactory(node, "of", "(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEItemKey;")
                    && callsFactory(node, "fromTag", "(Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEItemKey;");
            } else if (family.equals("fluid")) {
                valid = has(node, "of", "(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEFluidKey;")
                    && has(node, "<init>", "(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/nbt/CompoundTag;)V");
            } else {
                String stack = family.equals("chemical") ? "Lmekanism/api/chemical/ChemicalStack;" : "Lcom/glodblock/github/appflux/common/me/key/type/EnergyType;";
                valid = has(node, "<init>", "(" + stack + ")V")
                    && has(node, "of", "(" + stack + ")L" + type.replace('.', '/') + ";");
                if (family.equals("chemical")) valid &= has(node, "getStack", "()" + stack) && has(node, "hashCode", "()I");
            }
            if (!valid) LoggerFactory.getLogger("neoecoae/keyreuse").warn("AE key reuse adapter disabled for unrecognized {} API; retaining value-based upstream factories", type);
            return valid;
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Exception | LinkageError failure) {
            LoggerFactory.getLogger("neoecoae/keyreuse").warn("Cannot inspect AE key reuse adapter {}; retaining upstream factories", type, failure);
            return false;
        }
    }

    private static boolean has(ClassNode node, String name, String descriptor) {
        return node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor));
    }

    private static boolean writesStackSize(ClassNode node) {
        for (var method : node.methods) {
            if (!method.name.equals("of") || !method.desc.equals("(Lnet/minecraft/world/item/ItemStack;)Lappeng/api/stacks/AEItemKey;")) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                    && field.owner.equals(node.name) && field.name.equals("maxStackSize") && field.desc.equals("I")) return true;
            }
        }
        return false;
    }

    private static boolean callsFactory(ClassNode node, String name, String descriptor) {
        for (var method : node.methods) {
            if (!method.name.equals(name) || !method.desc.equals(descriptor)) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                    && call.owner.equals(node.name) && call.name.equals("of")
                    && call.desc.equals("(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEItemKey;")) return true;
            }
        }
        return false;
    }

    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
