# Places every ECO block with an LDLib2 UI next to the player and opens it (dev only:
# uses /neoecoae debug_open_ui). Check the client log for "unknown sync value" / decode errors.
scoreboard objectives add guitest dummy
execute unless entity @p run return 0
scoreboard players add #t guitest 1
execute if score #t guitest matches 300 as @p at @s run setblock ~2 ~1 ~1 neoecoae:integrated_working_station
execute if score #t guitest matches 320 as @p at @s run neoecoae debug_open_ui ~2 ~1 ~1
execute if score #t guitest matches 360 as @p at @s run setblock ~2 ~1 ~2 neoecoae:input_hatch
execute if score #t guitest matches 380 as @p at @s run neoecoae debug_open_ui ~2 ~1 ~2
execute if score #t guitest matches 420 as @p at @s run setblock ~2 ~1 ~3 neoecoae:output_hatch
execute if score #t guitest matches 440 as @p at @s run neoecoae debug_open_ui ~2 ~1 ~3
execute if score #t guitest matches 480 as @p at @s run setblock ~2 ~1 ~4 neoecoae:crafting_pattern_bus
execute if score #t guitest matches 500 as @p at @s run neoecoae debug_open_ui ~2 ~1 ~4
execute if score #t guitest matches 540 as @p at @s run setblock ~2 ~1 ~5 neoecoae:crafting_system_l9
execute if score #t guitest matches 560 as @p at @s run neoecoae debug_open_ui ~2 ~1 ~5
execute if score #t guitest matches 600 as @p at @s run setblock ~2 ~1 ~6 neoecoae:computation_system_l9
execute if score #t guitest matches 620 as @p at @s run neoecoae debug_open_ui ~2 ~1 ~6
execute if score #t guitest matches 660 as @p at @s run setblock ~2 ~1 ~7 neoecoae:storage_system_l9
execute if score #t guitest matches 680 as @p at @s run neoecoae debug_open_ui ~2 ~1 ~7
execute if score #t guitest matches 720 as @p at @s run setblock ~2 ~1 ~8 neoecoae:large_integrated_working_station_input_hatch
execute if score #t guitest matches 740 as @p at @s run neoecoae debug_open_ui ~2 ~1 ~8
execute if score #t guitest matches 780 as @p at @s run setblock ~2 ~1 ~9 neoecoae:large_integrated_working_station_output_hatch
execute if score #t guitest matches 800 as @p at @s run neoecoae debug_open_ui ~2 ~1 ~9
execute if score #t guitest matches 840 as @p at @s run setblock ~2 ~1 ~10 neoecoae:storage_interface
execute if score #t guitest matches 860 as @p at @s run neoecoae debug_open_ui ~2 ~1 ~10
execute if score #t guitest matches 900 as @p at @s run setblock ~2 ~1 ~11 neoecoae:crafting_interface
execute if score #t guitest matches 920 as @p at @s run neoecoae debug_open_ui ~2 ~1 ~11
execute if score #t guitest matches 960 as @p at @s run setblock ~2 ~1 ~12 neoecoae:computation_interface
execute if score #t guitest matches 980 as @p at @s run neoecoae debug_open_ui ~2 ~1 ~12
execute if score #t guitest matches 1020 run say guitest-done
