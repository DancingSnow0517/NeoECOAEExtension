/**
 * Server-owned CPU runtime, dispatch, accounting and recovery.
 * Provider acceptance transfers ownership; an indeterminate result requires reconciliation.
 * All world/network mutations stay on the owning server thread.
 * NBT and AE2 calls here currently target 1.21.1.
 */
package cn.dancingsnow.neoecoae.crafting.execution;
