package cn.dancingsnow.neoecoae.api.me.pattern;

/** Optional execution guarantees, separate from ordinary {@code IPatternDetails} compatibility. */
public record ECOExecutionOptimization(boolean verifiedAssemblerFastPath) {
    public static final ECOExecutionOptimization NONE = new ECOExecutionOptimization(false);
    public static final ECOExecutionOptimization VERIFIED_ASSEMBLER = new ECOExecutionOptimization(true);
}
