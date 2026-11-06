#!/usr/bin/env python3
"""Build the shared E-11-style blaster with native Minecraft cuboids.

Run from any directory. No image tools or external model libraries are required.
The muzzle points toward -Z; the open stock extends toward +Z.
"""

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/tropercraft-mod/models/item/blaster.json"
elements = []


def box(name, start, end, material="body", *, rotation=None):
    element = {
        "name": name,
        "from": start,
        "to": end,
        "faces": {
            face: {"uv": [0, 0, 16, 16], "texture": f"#{material}"}
            for face in ("north", "south", "east", "west", "up", "down")
        },
    }
    if rotation is not None:
        element["rotation"] = rotation
    elements.append(element)


def barrel(name, z_start, z_end, radius, material="metal", *, y=10):
    # Stepped cross-section: narrower top and bottom, wider center.
    # The small overlaps avoid coplanar faces at the joins.
    box(f"{name}_center", [8-radius, y-radius*.55, z_start],
        [8+radius, y+radius*.55, z_end], material)
    box(f"{name}_top", [8-radius*.55, y+radius*.54, z_start+.01],
        [8+radius*.55, y+radius, z_end-.01], material)
    box(f"{name}_bottom", [8-radius*.55, y-radius, z_start+.01],
        [8+radius*.55, y-radius*.54, z_end-.01], material)


# Long ventilated barrel jacket, collar and recessed black muzzle.
barrel("barrel_jacket", -6.7, 5.4, 1.35)
barrel("front_collar", -7.25, -6.55, 1.5, "edge")
barrel("muzzle", -8.3, -7.1, 1.1, "body")
box("muzzle_bore", [7.48, 9.48, -8.32], [8.52, 10.52, -8.29], "recess")

# Dark inset vents sit between raised longitudinal cooling rails.
for side in (-1, 1):
    x_start, x_end = (6.62, 6.68) if side < 0 else (9.32, 9.38)
    for index in range(6):
        z = -5.95 + index * 1.65
        box(f"vent_{side}_{index}", [x_start, 9.6, z],
            [x_end, 10.4, z+1.0], "recess")
    for y in (9.04, 10.72):
        box(f"cooling_rail_{side}_{y}", [x_start-.13, y, -6.3],
            [x_end+.13, y+.24, 4.35], "edge")
box("upper_cooling_rail", [7.72, 11.32, -6.3], [8.28, 11.55, 4.4], "edge")
box("lower_cooling_rail", [7.72, 8.45, -6.3], [8.28, 8.68, 4.4], "body")

# Receiver, side magazine/power pack and small silver fasteners.
barrel("receiver", 5.0, 12.7, 1.65)
barrel("rear_cap", 12.5, 13.35, 1.8, "edge")
box("receiver_top_rail", [7.25, 11.56, 3.2], [8.75, 12.04, 12.4], "body")
box("side_power_pack", [5.8, 8.75, 5.3], [6.55, 10.9, 9.0], "body")
box("power_pack_cover", [5.65, 9.02, 5.65], [5.82, 10.64, 8.65], "metal")
box("ejection_port", [9.64, 9.4, 8.6], [9.72, 10.5, 11.6], "recess")
box("charging_handle", [9.65, 10.2, 11.25], [10.55, 10.55, 11.7], "edge")
for z in (5.95, 8.15):
    box(f"pack_fastener_{z}", [5.6, 9.65, z], [5.66, 9.98, z+.28], "screw")

# Angled pistol grip, grooved rubber surface, trigger and open trigger guard.
grip_rotation = {"origin": [8, 8.6, 10.6], "axis": "x", "angle": -22.5}
box("pistol_grip", [7.18, 3.3, 9.6], [8.82, 8.65, 11.6],
    "body", rotation=grip_rotation)
box("grip_heel", [7.07, 3.15, 9.5], [8.93, 3.55, 11.7],
    "edge", rotation=grip_rotation)
for index in range(4):
    y = 4.15 + index*.8
    for side, x in (("left", 7.12), ("right", 8.83)):
        box(f"grip_groove_{side}_{index}", [x, y, 9.72], [x+.05, y+.18, 11.48],
            "recess", rotation=grip_rotation)
box("trigger", [7.75, 6.0, 8.9], [8.25, 8.45, 9.25], "edge",
    rotation={"origin": [8, 8.4, 9.1], "axis": "x", "angle": 22.5})
box("trigger_guard_front", [7.64, 5.25, 7.25], [8.36, 8.55, 7.65], "body")
box("trigger_guard_bottom", [7.64, 5.15, 7.35], [8.36, 5.57, 11.15], "body")

# Two scope mounts, tubular optic, front/rear rims and inset dark lenses.
for z in (6.0, 10.1):
    box(f"scope_mount_{z}", [7.45, 11.9, z], [8.55, 13.65, z+.65], "edge")
barrel("scope_tube", 4.6, 11.85, .72, "body", y=14.15)
barrel("scope_front_bell", 3.35, 5.3, 1.12, "metal", y=14.15)
barrel("scope_front_rim", 3.15, 3.55, 1.2, "body", y=14.15)
barrel("scope_eyepiece", 11.2, 12.7, 1.0, "body", y=14.15)
box("scope_front_lens", [7.38, 13.53, 3.12], [8.62, 14.77, 3.16], "lens")
box("scope_rear_lens", [7.45, 13.6, 12.69], [8.55, 14.7, 12.73], "lens")
box("scope_adjustment", [8.6, 13.75, 7.6], [9.17, 14.55, 8.4], "edge")

# Front iron sight and folding skeleton stock, with real open space inside.
box("front_sight_base", [7.15, 11.15, -5.8], [8.85, 11.65, -4.95], "body")
box("front_sight_post", [7.8, 11.6, -5.6], [8.2, 12.65, -5.15], "edge")
for side, x in (("left", 6.78), ("right", 8.82)):
    box(f"stock_hinge_{side}", [x-.15, 9.35, 11.9], [x+.55, 10.55, 13.7], "edge")
    box(f"stock_upper_arm_{side}", [x, 9.8, 13.1], [x+.4, 10.35, 20.3], "body")
    box(f"stock_diagonal_arm_{side}", [x, 9.35, 13.3], [x+.4, 9.85, 20.65], "metal",
        rotation={"origin": [8, 9.6, 13.4], "axis": "x", "angle": 22.5})
box("stock_butt_plate", [6.65, 6.3, 20.0], [9.35, 10.75, 20.65], "body")
box("stock_butt_pad", [6.78, 6.45, 20.63], [9.22, 10.6, 20.9], "recess")

model = {
    "credit": "TroperCraft: E-11-style model based on the supplied blaster reference",
    "texture_size": [16, 16],
    "textures": {
        "body": "minecraft:block/black_concrete",
        "metal": "minecraft:block/netherite_block",
        "edge": "minecraft:block/gray_concrete",
        "recess": "minecraft:block/black_wool",
        "screw": "minecraft:block/light_gray_concrete",
        "lens": "minecraft:block/black_terracotta",
        "particle": "minecraft:block/netherite_block",
    },
    "elements": elements,
    "display": {
        # HeldItemFeatureRenderer already rotates the item into the raised hand.
        # Another -90 degrees here would turn the barrel vertically/backwards.
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 1.25, .75], "scale": [.72, .72, .72]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 1.25, .75], "scale": [.72, .72, .72]},
        "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [1, 1.5, 0], "scale": [.68, .68, .68]},
        "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [1, 1.5, 0], "scale": [.68, .68, .68]},
        "gui": {"rotation": [20, 135, 0], "translation": [0, -1, 0], "scale": [.54, .54, .54]},
        "ground": {"rotation": [0, 0, 90], "translation": [0, 1.8, 0], "scale": [.4, .4, .4]},
        "fixed": {"rotation": [0, 90, 0], "translation": [0, -1, 0], "scale": [.55, .55, .55]},
    },
}

if __name__ == "__main__":
    # Keep vectors and individual faces on one line, like hand-edited models.
    encoded = json.dumps(model, indent=2)
    encoded = re.sub(r"\[\s+([-\d.,\s]+)\]",
                     lambda match: json.dumps(json.loads(match.group(0))), encoded)
    encoded = re.sub(r'\{\s+"uv": (\[[^\]]+\]),\s+"texture": ("[^"]+")\s+\}',
                     r'{"uv": \1, "texture": \2}', encoded)
    OUTPUT.write_text(encoded + "\n", encoding="utf-8")
    print(f"Generated {OUTPUT.relative_to(ROOT)} ({len(elements)} cuboids)")
