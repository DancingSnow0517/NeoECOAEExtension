import os
from dataclasses import replace

files = os.listdir()
if __name__ == '__main__':
    for file in files:
        if file.endswith('.png'):
            new_name = (file.replace("storage_array_drives", "storage_cell")
                        .replace("a0", "l4").replace("b0", "l6").replace("c0", "l9")
                        .replace("_off", "").replace("_gas", "_chemical"))
            os.rename(file, new_name)
            print(f"Renamed '{file}' to '{new_name}'")