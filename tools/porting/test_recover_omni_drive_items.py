import tempfile
import unittest
from pathlib import Path
import nbtlib
import recover_omni_drive_items as recovery


class OmniRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.world = self.root / "world"
        self.backup = self.root / "backup"
        self.output = self.root / "recovered"
        for folder, ident in ((self.world, "minecraft:air"),
                              (self.backup, "neoecoae:eco_quantum_omni_cell_256m")):
            (folder / "region").mkdir(parents=True)
            (folder / "data/ae_universal_cell_data").mkdir(parents=True)
            nbtlib.File({"data": nbtlib.Compound({"inventory": nbtlib.Compound({
                "entries": nbtlib.List[nbtlib.Compound]([])})})}).save(
                    folder / "data/ae_universal_cell_data/00000000-0000-0000-0000-000000000001.dat", gzipped=True)
            self.write_drive(folder, ident, "00000000-0000-0000-0000-000000000001")

    @staticmethod
    def write_drive(folder, ident, uuid):
        drive = nbtlib.Compound({"id": nbtlib.String("neoecoae:eco_drive"),
            "x": nbtlib.Int(9), "y": nbtlib.Int(-59), "z": nbtlib.Int(3),
            "cellStack": nbtlib.Compound({"id": nbtlib.String(ident), "Count": nbtlib.Byte(1),
                "tag": nbtlib.Compound({"ae_universal_cell_uuid": nbtlib.String(uuid),
                                      "unchanged": nbtlib.Int(42)})})})
        path = folder / "region/r.0.0.mca"
        path.write_bytes(b"\0" * 8192)
        recovery.write_chunk(path, 0, nbtlib.File({"block_entities": nbtlib.List[nbtlib.Compound]([drive])}))

    def test_recovers_only_item_id_and_preserves_both_source_worlds_and_inventory(self):
        current_before = recovery.fingerprint(self.world)
        backup_before = recovery.fingerprint(self.backup)
        report = recovery.prepare(self.world, self.backup, self.output)
        self.assertEqual(1, len(report["restored_drives"]))
        self.assertEqual(current_before, recovery.fingerprint(self.world))
        self.assertEqual(backup_before, recovery.fingerprint(self.backup))
        _, chunk = next(recovery.chunks(self.output / "region/r.0.0.mca"))
        disk = chunk["block_entities"][0]["cellStack"]
        self.assertEqual("neoecoae:eco_quantum_omni_cell_256m", str(disk["id"]))
        self.assertEqual(42, disk["tag"]["unchanged"])
        relative = "data/ae_universal_cell_data/00000000-0000-0000-0000-000000000001.dat"
        self.assertEqual((self.world / relative).read_bytes(), (self.output / relative).read_bytes())

    def test_rejects_a_backup_with_a_different_uuid(self):
        self.write_drive(self.backup, "neoecoae:eco_quantum_omni_cell_256m", "00000000-0000-0000-0000-000000000002")
        with self.assertRaisesRegex(ValueError, "No exact backup match"):
            recovery.prepare(self.world, self.backup, self.output)
        self.assertFalse(self.output.exists())

    def test_rejects_missing_authoritative_inventory(self):
        (self.world / "data/ae_universal_cell_data/00000000-0000-0000-0000-000000000001.dat").unlink()
        with self.assertRaisesRegex(ValueError, "Missing current Omni inventory"):
            recovery.prepare(self.world, self.backup, self.output)

    def test_never_replaces_a_known_live_item(self):
        self.write_drive(self.world, "minecraft:diamond", "00000000-0000-0000-0000-000000000001")
        with self.assertRaisesRegex(ValueError, "No air-converted"):
            recovery.prepare(self.world, self.backup, self.output)

    def test_rejects_existing_or_nested_output_paths(self):
        for output in (self.world, self.world / "nested", self.root):
            with self.assertRaises(ValueError):
                recovery.prepare(self.world, self.backup, output)
        self.output.mkdir()
        with self.assertRaisesRegex(ValueError, "new directory"):
            recovery.prepare(self.world, self.backup, self.output)


if __name__ == "__main__":
    unittest.main()
