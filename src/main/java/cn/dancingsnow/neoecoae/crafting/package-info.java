/**
 * ECO's internal crafting module, compiled as part of the main 1.21.1 mod.
 *
 * <p>Planning, execution, exact amounts, display and the CraftingTree live here.
 * Public integration contracts remain in {@code cn.dancingsnow.neoecoae.api.me}.
 * This package is not an independent artifact or a version-neutral API. In particular,
 * planner/execution models still use the 1.21.1 AE2 resource and pattern contracts.</p>
 *
 * <p>Amount and format code must remain JDK-only. Server planning and execution must
 * never depend on client screens or renderers. Graph projection/layout consumes
 * snapshots; only {@code crafting.graph.client} owns Minecraft rendering.</p>
 */
package cn.dancingsnow.neoecoae.crafting;
