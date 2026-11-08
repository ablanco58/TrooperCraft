#!/usr/bin/env python3
"""Build the native, beveled Star Destroyer mesh (all coordinates are blocks)."""

import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/tropercraft-mod/models/entity/star_destroyer.json"
PARTS = []
BOX_FACES = [[0, 3, 2, 1], [4, 5, 6, 7], [0, 1, 5, 4],
             [3, 7, 6, 2], [0, 4, 7, 3], [1, 2, 6, 5]]


def mesh(name, vertices, faces, material="hull", emissive=False):
    PARTS.append({"name": name, "vertices": vertices, "faces": faces,
                  "material": material, "emissive": emissive})


def box(name, start, end, material="hull", emissive=False):
    x, y, z = start
    xx, yy, zz = end
    mesh(name, [[x,y,z], [xx,y,z], [xx,yy,z], [x,yy,z],
                [x,y,zz], [xx,y,zz], [xx,yy,zz], [x,yy,zz]], BOX_FACES, material, emissive)


def wedge(name, width, front, back, bottom, top, material="hull"):
    mesh(name, [[-width,bottom,back], [width,bottom,back], [0,bottom,front],
                [-width,top,back], [width,top,back], [0,bottom+.2,front]],
         [[0,2,1,1], [3,4,5,5], [0,1,4,3], [1,2,5,4], [2,0,3,5]], material)


def bridge(name, width, front, back, bottom, top, material="hull"):
    vertices = []
    for y, w in ((bottom,width),(top,width-1)):
        vertices += [[-w+3,y,front],[w-3,y,front],[w,y,front+2],
                     [w,y,back-2],[w-2,y,back],[-w+2,y,back],
                     [-w,y,back-2],[-w,y,front+2]]
    faces = [[i,i+8,(i+1)%8+8,(i+1)%8] for i in range(8)]
    faces += [[8,15,14,13],[8,13,12,9],[9,12,11,10],
              [0,5,6,7],[0,1,4,5],[1,2,3,4]]
    mesh(name,vertices,faces,material)


def dome(name, x, y, z, radius):
    vertices, faces = [], []
    segments = 12
    for ring in range(5):
        angle = ring * math.pi / 8
        r = radius * math.cos(angle) if ring < 4 else .03
        for i in range(segments):
            theta = i * math.tau / segments
            vertices.append([x + math.cos(theta)*r, y + math.sin(angle)*radius, z + math.sin(theta)*r])
    for ring in range(4):
        for i in range(segments):
            a = ring*segments+i
            b = ring*segments+(i+1)%segments
            faces.append([a,a+segments,b+segments,b])
    mesh(name, vertices, faces, "hull")


def engine(name, x, y, radius):
    # Recessed blue exhaust within tapered armored bells, rather than floating discs.
    count = 16
    vertices = []
    for z, factor in ((55,1.12),(59,1.06),(63,1.0),(63,.84),(59.2,.72)):
        for i in range(count):
            angle = i*math.tau/count
            vertices.append([x+math.cos(angle)*radius*factor, y+math.sin(angle)*radius*factor,z])
    for ring in range(4):
        faces = [[ring*count+i,ring*count+(i+1)%count,(ring+1)*count+(i+1)%count,(ring+1)*count+i]
                 for i in range(count)]
        mesh(name+f"_bell_{ring}", vertices, faces, "edge" if ring < 2 else "rim")
    for label, factor, z, material in (("exhaust",.72,59.3,"engine"),("core",.42,59.4,"light")):
        points = [[x,y,z]] + [[x+math.cos(i*math.tau/count)*radius*factor,
                               y+math.sin(i*math.tau/count)*radius*factor,z] for i in range(count)]
        mesh(name+"_"+label, points, [[0,i+1,(i+1)%count+1,(i+1)%count+1] for i in range(count)], material, True)


def hull_width(z):
    return 48*(z+82)/140


def top_y(z):
    return .5 + 6.8*(z+82)/140


def bottom_y(z):
    return -.5 - 4.5*(z+82)/140


# Eight-sided cross sections give both dorsal and ventral faces a beveled silhouette.
sections = []
for z in (-81.5,-64,-40,-16,8,32,58):
    w, top, bottom = hull_width(z), top_y(z), bottom_y(z)
    trench = min(1.1, (top-bottom)*.25)
    sections.append([[-w*.94,top,z],[w*.94,top,z],[w,trench,z],[w,-trench,z],
                     [w*.92,bottom,z],[-w*.92,bottom,z],[-w,-trench,z],[-w,trench,z]])
for segment, (a,b) in enumerate(zip(sections, sections[1:])):
    for side in range(8):
        # A physical dark trench runs between the two armored halves.
        material = ("hull", "panel", "rim", "ventral", "ventral", "ventral", "rim", "panel")[side]
        mesh(f"hull_{segment}_{side}", a+b,
             [[side,side+8,(side+1)%8+8,(side+1)%8]], material)
mesh("bow_cap", sections[0], [[0,1,2,7],[7,2,3,6],[6,3,4,5]], "edge")
mesh("stern_bulkhead", sections[-1], [[0,7,2,1],[7,6,3,2],[6,5,4,3]], "edge")

# Broad, overlapping terraces form the characteristic stepped superstructure.
wedge("dorsal_spine", 4.5, -66, 49, 2, 8, "panel")
wedge("terrace_lower", 29, -23, 51, 6, 11.4, "edge")
wedge("terrace_lower_roof", 28.5, -22, 50.5, 7, 12, "hull")
wedge("terrace_middle", 22.5, -1, 50, 11, 16.8, "edge")
wedge("terrace_middle_roof", 22, 0, 49.5, 12, 17.4, "panel")
wedge("terrace_upper", 15.5, 18, 48, 17, 22, "hull")
box("command_neck", [-5.5,21,33], [5.5,31,43], "panel")
box("command_neck_front", [-4.2,23,31.5], [4.2,29,33], "edge")
bridge("bridge_lower", 19,32,43,29.5,32.2,"edge")
bridge("bridge", 21,32,44,32,36.8)
bridge("bridge_roof", 20.5,32.5,43.5,36.8,37.5,"panel")
for x in range(-17,18,2):
    box(f"bridge_window_{x}", [x,33.5,31.94], [x+1.2,34.1,32.02], "window", True)
for x in (-14.5,14.5):
    box(f"radar_pedestal_{x}", [x-2,37,36], [x+2,39,40], "edge")
    dome(f"shield_dome_{x}", x,39,38,3.3)
box("sensor_mast", [-.3,37.5,38], [.3,43,38.6], "edge")
box("sensor_crossbar", [-2,41,38], [2,41.4,38.6], "panel")

# Belly details stay shallow and use restrained contrast, so they read from the ground.
wedge("ventral_keel", 3.5, -65, 6, -4.5, -1.2, "panel")
box("hangar_recess", [-10,-5.25,14], [10,-5.05,39], "rim")
for x in (-11,11):
    box(f"hangar_side_{x}", [x-1,-6.7,12], [x+1,-3.5,41], "panel")
    box(f"hangar_guide_{x}", [x-.2,-6.76,15], [x+.2,-6.7,37], "light", True)
for z in (12,40):
    box(f"hangar_lip_{z}", [-10,-6.7,z-1], [10,-3.5,z+1], "edge")
# Suspended crossbeams and inset docking bays add depth to the dark opening.
for z in (18,26,34):
    box(f"hangar_beam_{z}", [-9.8,-5.8,z], [9.8,-5.25,z+.45], "edge")
    for x in (-8,8):
        box(f"hangar_beacon_{x}_{z}", [x-.3,-5.87,z+.5], [x+.3,-5.8,z+1.8], "engine", True)
box("aft_keel", [-5,-7.3,43], [5,-4,55], "panel")
box("aft_keel_inset", [-3.5,-7.36,45], [3.5,-7.3,53], "edge")

# Armor tiles conform to the slopes; narrow gaps reveal the base plating.
for row,z in enumerate(range(-58,50,9)):
    z1,z2 = z,z+7.8
    for side in (-1,1):
        for band in range(3):
            inner = .12+band*.265
            outer = inner+.24
            for underside in (False,True):
                # Leave the central underside hangar and raised upper terraces clear.
                if not underside and z > -18 and band < 2:
                    continue
                if underside and z > 7 and band == 0:
                    continue
                surface = bottom_y if underside else top_y
                bias = -.13 if underside else .13
                points = [[side*hull_width(zz)*fraction,surface(zz)+bias,zz]
                          for zz,fraction in ((z1,inner),(z1,outer),(z2,outer),(z2,inner))]
                mesh(f"armor_{underside}_{side}_{row}_{band}", points,
                     [[0,3,2,1]] if (side == 1) != underside else [[0,1,2,3]],
                     "panel" if (row+band)%4 == 0 else ("ventral" if underside else "hull"))
        # Sidewall frames and tiny windows break up the continuous equatorial trench.
        x1,x2 = side*hull_width(z1),side*hull_width(z2)
        mesh(f"trench_frame_{side}_{row}",
             [[x1,-1.2,z1],[x1,1.2,z1],[x2,1.2,z2],[x2,-1.2,z2]],
             [[0,1,2,3]] if side == 1 else [[0,3,2,1]], "edge")
        for dz in (2,4.5,6):
            zz = z+dz
            x = side*(hull_width(zz)+.03)
            box(f"trench_window_{side}_{row}_{dz}", [x-.08,-.15,zz], [x+.08,.15,zz+.65], "window", True)
        # Repeated underside equipment bays, inset channels, and subtle light markers.
        if z > -35:
            xx = side*hull_width(z+3)*.76
            yy = bottom_y(z+3)
            box(f"belly_bay_{side}_{row}", [xx-1.4,yy-.65,z+2], [xx+1.4,yy-.08,z+5.5], "edge")
            box(f"belly_bay_rib_{side}_{row}", [xx-.9,yy-.8,z+3], [xx+.9,yy-.6,z+3.35], "panel")

for side in (-1,1):
    for z in (19,30,41):
        x = side*hull_width(z)*.79
        y = top_y(z)
        box(f"turret_mount_{side}_{z}", [x-2,y,z-1.8], [x+2,y+1,z+1.8], "edge")
        box(f"turret_{side}_{z}", [x-1.4,y+1,z-1.2], [x+1.4,y+2.3,z+1.2], "panel")
        for dx in (-.65,.65):
            box(f"barrel_{side}_{z}_{dx}", [x+dx-.16,y+1.5,z-4.5], [x+dx+.16,y+1.85,z-.7], "edge")
    for z in (28,38,48):
        x = side*10
        box(f"deck_machinery_{side}_{z}", [x-1,18,z], [x+1,19.2,z+3], "edge")

# Three primary engines and four smaller auxiliary nozzles.
for x in (-22,0,22):
    engine(f"main_engine_{x}", x,1,6.2 if x else 7)
for x,y in ((-36,1),(36,1),(-11,4),(11,4)):
    engine(f"aux_engine_{x}",x,y,2.5)

model = {
    "description": "Beveled Imperial destroyer with ventral hangar, layered armor and recessed engine bells",
    "materials": {
        "hull": "minecraft:textures/block/light_gray_concrete.png",
        "panel": "minecraft:textures/block/smooth_stone.png",
        "ventral": "minecraft:textures/block/light_gray_concrete.png",
        "edge": "minecraft:textures/block/gray_concrete.png",
        "rim": "minecraft:textures/block/black_concrete.png",
        "window": "minecraft:textures/block/light_blue_concrete.png",
        "engine": "minecraft:textures/block/light_blue_concrete.png",
        "light": "minecraft:textures/block/white_concrete.png",
    },
    "parts": PARTS,
}

if __name__ == "__main__":
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(model, separators=(",", ":")) + "\n")
    print(f"Generated {OUTPUT.relative_to(ROOT)}: {len(PARTS)} parts")
