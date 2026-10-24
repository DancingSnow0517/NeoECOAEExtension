import copy
import unittest

from finish_runtime_resources import convert_model_face_data


class ModelFaceDataTest(unittest.TestCase):
    def test_emissive_face_uses_forge_lightmap_without_shading(self):
        original = {"elements": [{"faces": {"north": {
            "texture": "#light", "neoforge_data": {"block_light": 15, "sky_light": 15}}}}]}
        before = copy.deepcopy(original)
        result = convert_model_face_data(original)
        self.assertEqual(before, original)
        element = result["elements"][0]
        self.assertFalse(element["shade"])
        self.assertEqual({"block_light": 15, "sky_light": 15, "ambient_occlusion": False},
                         element["faces"]["north"]["forge_data"])

    def test_mixed_element_preserves_non_emissive_body_and_element_shading(self):
        model = {"elements": [{"shade": True, "faces": {
            "north": {"texture": "#lamp", "neoforge_data": {"block_light": 15}},
            "south": {"texture": "#casing"}}}]}
        result = convert_model_face_data(model)
        self.assertTrue(result["elements"][0]["shade"])
        self.assertEqual(model["elements"][0]["faces"]["south"], result["elements"][0]["faces"]["south"])

    def test_nested_composite_children_and_existing_data_are_idempotent(self):
        model = {"children": {"light": {"elements": [{"faces": {
            "north": {"texture": "#lamp", "forge_data": {"block_light": 15, "color": "ffffffff"}}}}]}}}
        converted = convert_model_face_data(model)
        self.assertEqual(converted, convert_model_face_data(converted))
        self.assertEqual("ffffffff", converted["children"]["light"]["elements"][0]["faces"]["north"]["forge_data"]["color"])

    def test_conflicting_loader_fields_are_rejected(self):
        with self.assertRaises(ValueError):
            convert_model_face_data({"neoforge_data": {}, "forge_data": {}})


if __name__ == "__main__":
    unittest.main()
