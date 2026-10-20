/**
 * ECO FastPath batch planning and execution.
 *
 * <p>This package owns recipe classification, exact batch limits, reusable/stateful
 * simulation, input extraction plans and the single acceptance transaction. It is
 * part of the main Mod source tree, not a separate artifact.</p>
 *
 * <p>AE2 and optional-mod objects are intentionally confined to the execution
 * boundary here. The higher-level CPU calls this package through the ECO-owned
 * facade/dispatch contracts; a rejected batch must retain all ownership, while an
 * indeterminate acceptance enters reconciliation.</p>
 */
package cn.dancingsnow.neoecoae.crafting.execution.fastpath;
