import tempfile
import unittest
from pathlib import Path
import uuid

import nbtlib

import audit_legacy_storage as audit


class HeaderRecoveryTest(unittest.TestCase):
    def fixture(self, root, amount=12):
        identity = uuid.uuid4()
        folder = root / "world" / "data" / "neoecoae_cells"
        folder.mkdir(parents=True)
        path = folder / f"{identity}.dat"
        entry = nbtlib.Compound({"key": nbtlib.Compound({"id": nbtlib.String("minecraft:diamond")}), "amount": nbtlib.Long(amount)})
        entries = nbtlib.List[nbtlib.Compound]([entry])
        damaged = nbtlib.Compound({"format": nbtlib.Int(1), "entries": entries})
        valid = nbtlib.Compound({"format": nbtlib.Int(1), "entries": nbtlib.List[nbtlib.Compound]([entry]),
                               "cell": nbtlib.IntArray([int.from_bytes(identity.bytes[i:i + 4], "big", signed=True) for i in range(0, 16, 4)]),
                               "revision": nbtlib.Long(4)})
        self.write(path, damaged)
        backup = path.with_name(path.name + ".20.4.2.bak")
        self.write(backup, valid)
        return path, backup, valid

    @staticmethod
    def write(path, data):
        nbtlib.File({"data": data}, gzipped=True).save(path)

    def test_prepares_matching_headers_without_touching_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path, backup, valid = self.fixture(root)
            original, preserved = path.read_bytes(), backup.read_bytes()
            report = audit.audit(root / "world")
            self.assertEqual(1, report["summary"]["header_repairs_available"])
            audit.prepare(root / "world", root / "recovery", report)
            prepared = root / "recovery" / "prepared" / "data" / "neoecoae_cells" / path.name
            self.assertEqual(preserved, prepared.read_bytes())
            self.assertEqual(original, path.read_bytes())
            self.assertEqual(preserved, backup.read_bytes())
            self.assertEqual(original, (root / "recovery" / "originals" / "data" / "neoecoae_cells" / path.name).read_bytes())
            self.assertEqual(audit.canonical(valid), audit.canonical(audit.read(prepared)[1]))

    def test_different_inventory_cannot_roll_back(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path, backup, valid = self.fixture(root)
            valid["entries"][0]["amount"] = nbtlib.Long(10)
            self.write(backup, valid)
            report = audit.audit(root / "world")
            self.assertEqual(0, report["summary"]["header_repairs_available"])
            self.assertEqual(1, report["summary"]["blocked_cells"])

    def test_wrong_identity_or_additional_state_cannot_be_repaired(self):
        for fault in ("identity", "metadata"):
            with self.subTest(fault=fault), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                path, backup, valid = self.fixture(root)
                if fault == "identity":
                    valid["cell"] = nbtlib.IntArray([0, 0, 0, 0])
                else:
                    valid["metadata"] = nbtlib.Compound({"restore": nbtlib.String("pending")})
                self.write(backup, valid)
                self.assertEqual(0, audit.audit(root / "world")["summary"]["header_repairs_available"])

    def test_never_exports_inside_source_world_or_overwrites_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            report = audit.audit(root / "world")
            with self.assertRaises(ValueError):
                audit.prepare(root / "world", root / "world" / "repair", report)
            (root / "existing").mkdir()
            with self.assertRaises(FileExistsError):
                audit.prepare(root / "world", root / "existing", report)

    def test_changed_source_invalidates_prepared_repair(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path, _, _ = self.fixture(root)
            report = audit.audit(root / "world")
            path.write_bytes(b"changed")
            with self.assertRaises(ValueError):
                audit.prepare(root / "world", root / "recovery", report)


if __name__ == "__main__":
    unittest.main()
