package cn.dancingsnow.neoecoae.multiblock.definition;

@FunctionalInterface
public interface BlockInstruction {
    void accept(MultiBlockContext context);
}
