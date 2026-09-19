/**
 * ECO CPU ownership, scheduling, runtime and output accounting. Inventory and
 * Provider mutation remain on the owning server thread; rejected submissions
 * retain ownership and indeterminate submissions require reconciliation.
 */
package cn.dancingsnow.neoecoae.crafting.execution;
