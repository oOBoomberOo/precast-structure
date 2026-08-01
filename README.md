# precast-structure

Architectury (Fabric + NeoForge) Minecraft 1.21.1 mod that lets players scan a framed build into a blueprint and print it into a placeable pre-cast structure item.

## Crafting

| Result | Recipe |
| --- | --- |
| Platform Floor ×4 | Iron ingot + smooth stone |
| Perimeter Fence ×3 | Sticks + yellow dye + black dye |
| Perimeter Fence Gate | Sticks + yellow dye + black dye |
| Metal Scaffold ×4 | Iron ingot + iron nuggets |
| Structure Scanner | Iron + glass pane + lapis + redstone |
| Structure Printer | Iron + hopper + crafting table + redstone |
| Empty Blueprint ×2 | Paper + blue dye + lapis |

Craft a filled **Blueprint** + **Empty Blueprint** together to clone it. Craft a filled **Blueprint** alone to clear it back into an Empty Blueprint.

Blueprints are filled by the scanner (consumes one Empty Blueprint); pre-cast structures come from the printer.

## Minimal gameplay loop

1. Build a filled rectangle of **Platform Floor** blocks (at least 3×3).
2. Place **Perimeter Fence** blocks one block above the platform border.
3. Place a vertical **Metal Scaffold** pillar on exactly one platform corner to set the height.
4. Place a **Structure Scanner** orthogonally adjacent to the platform (any side).
5. Build your structure inside the framed volume (above the floor, inside the fence ring).
6. Right-click the scanner while carrying an **Empty Blueprint** to consume it and receive a filled **Blueprint** item.
7. Put the blueprint in a **Structure Printer** with the required materials to produce a **Pre-cast Structure** item.
8. Hold the pre-cast item to see a ghost placement preview, then right-click to place the stored structure if the target area only contains replaceable blocks.
