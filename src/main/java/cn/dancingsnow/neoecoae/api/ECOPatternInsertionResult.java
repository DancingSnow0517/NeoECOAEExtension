package cn.dancingsnow.neoecoae.api;

/** Result of attempting to place an encoded pattern into an ECO pattern bus. */
public enum ECOPatternInsertionResult {
    INSERTED,
    ALREADY_PRESENT,
    NO_SPACE,
    INCOMPATIBLE,
    NO_TARGET
}
