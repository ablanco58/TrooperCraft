#!/usr/bin/env python3
"""Native Minecraft geometry inspired by the two supplied references; no external model runtime."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/tropercraft-mod'
FACES = ['north', 'south', 'east', 'west', 'up', 'down']

def element(name, a, b, material, angle=None, tint=False):
    part = {'name': name, 'from': a, 'to': b, 'faces': {f: {'uv':[0,0,16,16], 'texture':'#'+material, **({'tintindex':0} if tint else {})} for f in FACES}}
    if angle is not None: part['rotation'] = {'origin':[8,7,10], 'axis':'x', 'angle':angle}
    return part

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2)+'\n')

textures = {'metal':'minecraft:block/netherite_block', 'black':'minecraft:block/black_concrete', 'edge':'minecraft:block/gray_concrete',
            'wood':'minecraft:block/dark_oak_planks', 'lens':'minecraft:block/black_stained_glass', 'brass':'minecraft:block/raw_gold_block', 'particle':'minecraft:block/netherite_block'}
parts=[]
def gun(name,a,b,mat='metal',angle=None): parts.append(element(name,a,b,mat,angle))
gun('receiver',[6.65,7.0,5.0],[9.35,10.2,12.1])
gun('receiver_upper',[6.9,10.1,4.8],[9.1,10.8,11.5],'black')
gun('lower_frame',[6.8,6.1,6.2],[9.2,7.3,12.4],'black')
gun('wooden_grip',[6.9,1.1,10.0],[9.1,7.1,13.0],'wood',22.5)
gun('grip_backstrap',[6.75,1.1,12.6],[9.25,7.2,13.05],'black',22.5)
gun('grip_medallion_left',[6.84,3.4,11.0],[6.94,4.2,11.8],'brass',22.5)
gun('grip_medallion_right',[9.06,3.4,11.0],[9.16,4.2,11.8],'brass',22.5)
gun('barrel_root',[7.1,8.05,1.0],[8.9,9.65,5.0],'black')
gun('barrel',[7.35,8.35,-2.7],[8.65,9.35,1.4])
gun('muzzle_horizontal',[6.35,8.05,-5.5],[9.65,9.65,-1.8])
gun('muzzle_vertical',[6.8,7.6,-5.5],[9.2,10.1,-1.8])
gun('muzzle_face',[6.65,7.8,-5.62],[9.35,9.9,-5.5],'edge')
gun('muzzle_bore',[7.35,8.5,-5.66],[8.65,9.25,-5.6],'black')
gun('barrel_collar',[6.75,7.9,-2.0],[9.25,9.9,-.8],'edge')
for side in [-1,1]:
    for z in [-4.9,-3.9,-2.9]:
        x=6.31 if side<0 else 9.66
        gun('muzzle_vent', [x,8.4,z], [x+.04,8.8,z+.45], 'black')
gun('scope_tube',[7.1,12.25,1.0],[8.9,13.75,12.3],'black')
gun('scope_objective',[6.8,11.95,-.5],[9.2,14.05,2.4])
gun('scope_rear',[6.95,12.1,10.5],[9.05,13.95,12.9])
gun('scope_front_glass',[7.1,12.2,-.55],[8.9,13.8,-.5],'lens')
gun('scope_rear_glass',[7.2,12.35,12.9],[8.8,13.7,12.94],'lens')
for z in [4.4,9.2]:
    gun('scope_mount',[7.2,10.6,z],[8.8,12.35,z+1.1],'edge')
    gun('scope_ring',[6.95,12.0,z-.1],[9.05,14.0,z+1.2])
gun('scope_adjustment',[7.4,13.7,5.8],[8.6,14.65,7.1],'edge')
gun('magazine',[7.0,4.4,4.8],[9.0,7.0,6.35],'black')
gun('trigger_guard_front',[7.55,4.55,7.0],[8.45,6.8,7.5],'edge')
gun('trigger_guard_bottom',[7.55,4.3,7.1],[8.45,4.8,10.5],'edge')
gun('trigger_guard_rear',[7.55,4.5,10.0],[8.45,6.2,10.5],'edge')
gun('trigger',[7.75,5.3,8.4],[8.25,6.9,8.9],'black')
for x in [6.5,9.35]:
    gun('receiver_pin',[x,7.75,8.4],[x+.15,9.1,9.8],'edge')
    gun('rear_pin',[x,8.0,11.1],[x+.15,9.15,12.2],'black')
model={'credit':'Native DL-44 silhouette based on the supplied Han Solo reference; reduced output is a gameplay calibration.', 'textures':textures,'elements':parts,
       'display':{'firstperson_righthand':{'rotation':[0,0,0],'translation':[.7,1.2,0],'scale':[.7,.7,.7]},
                  'firstperson_lefthand':{'rotation':[0,0,0],'translation':[.7,1.2,0],'scale':[.7,.7,.7]},
                  'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,1.2,.4],'scale':[.65,.65,.65]},
                  'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,1.2,.4],'scale':[.65,.65,.65]},
                  'gui':{'rotation':[15,135,0],'translation':[0,0,0],'scale':[.72,.72,.72]},
                  'ground':{'rotation':[0,0,90],'translation':[0,2,0],'scale':[.45,.45,.45]},
                  'fixed':{'rotation':[0,90,0],'translation':[0,0,0],'scale':[.65,.65,.65]}}}
write(ASSETS/'models/item/dl44.json',model)

armor=[]
def box(slot,bone,name,a,size,material='plate'):
    armor.append({'slot':slot,'bone':bone,'name':name,'from':a,'size':size,'material':material})
box('head','head','helmet_crown',[-4.55,-8.8,-4.55],[9.1,2.0,9.1])
box('head','head','rear_shell',[-4.35,-7.0,-1.0],[8.7,7.3,5.35])
for x in [-4.35,1.8]: box('head','head','cheek_guard',[x,-6.9,-4.5],[2.55,6.8,4.0])
box('head','head','long_faceplate',[-1.65,-5.7,-4.85],[3.3,5.8,1.4])
box('head','head','inner_visor',[-4.1,-6.95,-4.35],[8.2,2.0,.65],'joint')
for x in [-3.35,1.7]: box('head','head','optic',[x,-6.6,-4.8],[1.65,1.0,.35],'joint')
box('head','head','nose_ridge',[-.45,-5.6,-5.15],[.9,3.2,.35],'edge')
for x in [-1.1,.55]: box('head','head','mouth_vent',[x,-2.65,-5.1],[.55,1.85,.15],'joint')
for x in [-4.9,4.2]: box('head','head','temple_module',[x,-6.5,-.7],[.7,2.4,2.2],'edge')
box('chest','body','undersuit',[-4.12,-.1,-2.15],[8.24,12.2,4.3],'joint')
box('chest','body','upper_breastplate',[-4.5,-.2,-2.65],[9.0,4.5,5.3])
for x in [-4.45,.2]: box('chest','body','pectoral_plate',[x,2.9,-3.1],[4.25,2.7,1.3])
box('chest','body','spine_pack',[-3.0,1.0,2.3],[6.0,8.9,1.0])
for y in [5.9,7.7,9.5]:
    for x in [-3.5,.2]: box('chest','body','abdominal_segment',[x,y,-2.5],[3.3,1.4,.8])
box('chest','body','rank_badge',[-1.15,1.4,-2.72],[2.3,.75,.13],'emblem')
box('chest','body','rank_badge_center',[-.45,1.0,-2.74],[.9,.75,.15],'emblem')
for bone,side in [('right_arm',-1),('left_arm',1)]:
    x=-3.2 if side<0 else -1.2
    box('chest',bone,'sleeve',[x,-.15,-2.15],[4.4,12.3,4.3],'joint')
    box('chest',bone,'shoulder',[x-.35,-.6,-2.65],[5.1,4.0,5.3])
    box('chest',bone,'upper_arm',[x,2.5,-2.5],[4.4,2.5,5.0])
    box('chest',bone,'forearm',[x-.1,6.1,-2.55],[4.6,4.3,5.1])
    box('chest',bone,'elbow_piston',[x+.3,4.8,-2.4],[3.8,1.0,4.8],'edge')
    box('chest',bone,'glove',[x,10.1,-2.4],[4.4,2.1,4.8],'joint')
box('legs','body','utility_belt',[-4.45,10.6,-2.7],[8.9,2.0,5.4])
box('legs','body','buckle',[-1.1,10.8,-2.95],[2.2,1.4,.4],'edge')
for bone in ['right_leg','left_leg']:
    box('legs',bone,'leg_underlayer',[-2.16,-.1,-2.15],[4.32,7.8,4.3],'joint')
    box('legs',bone,'thigh',[-2.35,-.1,-2.5],[4.7,5.9,5.0])
    box('legs',bone,'thigh_rib',[-1.5,.65,-2.75],[3.0,4.3,.4],'edge')
    box('legs',bone,'knee_pad',[-2.5,5.6,-3.05],[5.0,2.0,1.6])
    box('feet',bone,'shin_underlayer',[-2.15,7.4,-2.15],[4.3,4.9,4.3],'joint')
    box('feet',bone,'greave',[-2.4,7.5,-2.6],[4.8,3.6,5.2])
    box('feet',bone,'shin_ridge',[-.65,7.7,-2.95],[1.3,3.3,.5],'edge')
    box('feet',bone,'boot',[-2.45,10.8,-3.4],[4.9,1.7,5.9])
    box('feet',bone,'sole',[-2.55,12.0,-3.55],[5.1,.6,6.15],'joint')
write(ASSETS/'models/entity/salvaged_armor.json',{'credit':'Wearable salvage plating inspired by the supplied olive mechanical armor reference; original adaptation for this mod.', 'parts':armor})

# Native 3D inventory icons, using the same silhouette and palette as the worn armor.
icon_specs={
 'salvaged_helmet':[('plate',[3,4,3],[13,13,13]),('joint',[4,8,2.9],[12,10,3]),('edge',[7,4,2.7],[9,8,3])],
 'salvaged_chestplate':[('plate',[4,3,6],[12,13,10]),('plate',[0,6,5.5],[4,13,10.5]),('plate',[12,6,5.5],[16,13,10.5]),('joint',[5,3,5.7],[11,7,6]),('emblem',[7,10,5.6],[9,11,6])],
 'salvaged_leggings':[('plate',[3,10,6],[13,14,10]),('plate',[3,1,6],[7.5,11,10]),('plate',[8.5,1,6],[13,11,10]),('joint',[3,0,5.8],[7.5,3,6]),('joint',[8.5,0,5.8],[13,3,6])],
 'salvaged_boots':[('plate',[2,3,6],[7,12,10]),('plate',[9,3,6],[14,12,10]),('plate',[2,1,3],[7,5,10]),('plate',[9,1,3],[14,5,10]),('joint',[2,0,3],[7,1,10]),('joint',[9,0,3],[14,1,10])]
}
for name,spec in icon_specs.items():
    write(ASSETS/f'models/item/{name}.json',{'textures':{'plate':'minecraft:block/white_concrete','joint':'minecraft:block/black_concrete','edge':'minecraft:block/gray_concrete','emblem':'minecraft:block/red_terracotta','particle':'minecraft:block/white_concrete'},
          'elements':[element(name,a,b,mat,tint=mat=='plate') for mat,a,b in spec],
          'display':{'gui':{'rotation':[15,25,0],'scale':[.9,.9,.9]},'ground':{'scale':[.5,.5,.5]},'fixed':{'rotation':[0,180,0]}}})
print(f'Generated DL-44: {len(parts)} elements; wearable armor: {len(armor)} parts; four item models.')
