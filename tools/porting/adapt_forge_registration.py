"""Adapt the actual Forge Registrate generic signatures without changing registrations."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def last_type_argument(text, token, replacement):
    cursor = 0
    while (start := text.find(token + '<', cursor)) >= 0:
        content = start + len(token) + 1
        depth, end, comma = 1, content, -1
        while depth:
            char = text[end]
            if char == '<': depth += 1
            elif char == '>': depth -= 1
            elif char == ',' and depth == 1: comma = end
            end += 1
        if comma >= 0:
            new = replacement + '<' + text[comma + 1:end - 1].strip() + '>'
            text = text[:start] + new + text[end:]
            cursor = start + len(new)
        else:
            cursor = end
    return text

def main():
    changed = 0
    for source in (ROOT / 'src').rglob('*.java'):
        before = source.read_text(encoding='utf-8')
        after = last_type_argument(before, 'RegistryEntry', 'RegistryEntry')
        after = last_type_argument(after, 'DeferredHolder', 'RegistryObject')
        after = after.replace('import net.minecraftforge.registries.DeferredHolder;',
                              'import net.minecraftforge.registries.RegistryObject;')
        after = after.replace('getDelegate().value()', 'get()')
        if before != after:
            source.write_text(after, encoding='utf-8', newline='\n')
            changed += 1
    print(f'Updated {changed} Forge registry signatures.')

if __name__ == '__main__':
    main()
