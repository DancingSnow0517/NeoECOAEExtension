package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

public enum ECOFastPathFallbackReason {
    FAST_PATH_DISABLED("fast_path_disabled"),
    POST_CRAFTING_EVENT("post_crafting_event"),
    NO_ECO_PATTERN_BUS("no_eco_pattern_bus"),
    INTROSPECTION_UNAVAILABLE("introspection_unavailable"),
    DYNAMIC_SPECIAL("dynamic_special"),
    UNSUPPORTED_PATTERN_TYPE("unsupported_pattern_type"),
    KEY_BUILD_FAILED("key_build_failed"),
    OUTPUT_COUNT_NOT_ONE("output_count_not_one"),
    UNSAFE_EXPECTED_OUTPUT("unsafe_expected_output"),
    UNSAFE_CONTAINER_ITEM("unsafe_container_item"),
    UNSAFE_INPUT("unsafe_input"),
    STATEFUL_ITEM("stateful_item"),
    CACHE_MISS_VERIFYING("cache_miss_verifying"),
    NEGATIVE_CACHE("negative_cache"),
    CACHE_ENTRY_MISMATCH("cache_entry_mismatch"),
    RUNTIME_STACK_CONVERSION_FAILED("runtime_stack_conversion_failed"),
    OUTPUT_MISMATCH("output_mismatch"),
    CONTAINER_MISMATCH("container_mismatch"),
    INPUT_MISMATCH("input_mismatch"),
    CACHE_VALIDATION_REJECTED("cache_validation_rejected"),
    NO_BATCH_OFFER("no_batch_offer"),
    BATCH_AMOUNT_OVERFLOW("batch_amount_overflow"),
    NO_THREAD_SLOT("no_thread_slot"),
    ENERGY_LIMIT("energy_limit"),
    COOLANT_LIMIT("coolant_limit"),
    INVENTORY_LIMIT("inventory_limit"),
    INPUT_RESERVATION_FAILED("input_reservation_failed"),
    PROVIDER_REJECTED("provider_rejected"),
    WORKER_REJECTED("worker_rejected"),
    INVALID_BATCH_REQUEST("invalid_batch_request"),
    ACCOUNTING_FAILED("accounting_failed"),
    LEGACY_SLOW_EXECUTION("legacy_slow_execution");

    private final String code;

    ECOFastPathFallbackReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
