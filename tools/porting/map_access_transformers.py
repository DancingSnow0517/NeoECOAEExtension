"""Map imported Mojang-named AT members to target Forge SRG names using generated mappings."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
mapping = ROOT / "build/moddev/artifacts/intermediateToNamed.srg"
target = ROOT / "src/main/resources/META-INF/accesstransformer.cfg"
fields = {}
methods = {}
for line in mapping.read_text(encoding="utf-8").splitlines():
    parts = line.split()
    if parts and parts[0] == "FD:":
        fields[parts[2]] = parts[1].rsplit("/", 1)[1]
    elif parts and parts[0] == "MD:":
        methods[parts[3] + parts[4]] = parts[1].rsplit("/", 1)[1] + parts[2]

result = ["# Target: Minecraft 1.20.1 / Forge SRG; generated from intermediateToNamed.srg."]
count = 0
for line in target.read_text(encoding="utf-8").splitlines():
    declaration = line.split("#", 1)[0].split()
    if not declaration:
        continue
    access, owner, member = declaration
    if member.startswith(("f_", "m_")):
        raise SystemExit("Access transformers already use SRG names; refusing to remap twice.")
    key = owner.replace(".", "/") + "/" + member
    value = (methods if "(" in member else fields).get(key)
    if value is None:
        raise SystemExit(f"No target mapping for {owner} {member}")
    result.append(f"{access} {owner} {value} # {member}")
    count += 1
target.write_text("\n".join(result) + "\n", encoding="utf-8")
print(f"Mapped {count} access transformer declarations against target Minecraft mappings.")
