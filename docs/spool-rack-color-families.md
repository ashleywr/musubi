# Spool Rack Color Families

## Current behavior

- A rack slot stores a compact `SkeinContents` payload, not a live `ItemStack`.
- Extraction reconstructs a fresh `SkeinItem` stack on the server and hands that to the player.
- Storage now groups by `needleTier + color-family key`, so same-tier items that resolve to the same color family share a peg family.
- Legacy raw rack data still loads.

## Why this shape

- Keeps the block entity small and easy to serialize.
- Preserves full skein data when the player removes the skein from the wall.
- Lets the wall read as physical storage instead of a generic inventory.

## Next steps / TODOs

- Replace the fallback deterministic item-color hash with a canonical palette lookup if the art direction wants fixed family colors.
- Decide whether the four-slot rack is the final ceiling or if the rack should become a true per-color family map.
- Add a player-facing indicator for which family a peg currently represents, likely via tooltip/Jade text and a small visual accent on the model.
