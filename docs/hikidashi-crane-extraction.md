# Hikidashi Crane Extraction Block

## Overview

The **Hikidashi (引き出し)** is a mid-tier storage extraction block that serves as a viewer and withdrawal station for the textile storage network. It queries nearby Spool Racks, aggregates their contents, and allows players to extract colored silk through an interactive GUI.

**Key characteristic:** The Hikidashi is not a passive storage block—it requires **spiritual energy (REI)** to function and generates **miasma corruption** that must be periodically purified using a **gohei** ritual wand.

## Recipe

**Shaped crafting recipe** (`hikidashi.json`):
```
S S
SWS
S S
```
- **S** = Silk Skein (musubi:skein)
- **W** = Dark Oak Wood (minecraft:dark_oak_wood)

Result: 1× Hikidashi block

## Core Mechanics

### 1. Storage Aggregation

**Block Entity:** `HikidashiBlockEntity`
- Scans for nearby **Spool Racks** within an 8-block radius (`SEARCH_RANGE`)
- Caches rack positions and refreshes every 20 ticks (1 second)
- Queries all non-empty SkeinContents from connected racks
- Presents aggregated inventory in the GUI, sorted by tier then item name

### 2. Interactive GUI (`HikidashiScreen`)

**Access:** Right-click the Hikidashi block

**Features:**
- **Search field** — Filter by item name (case-insensitive, real-time)
- **Tier filter buttons** — "All", "Feather", "Crinkle", "Rinzu"
- **Scrollable list** — Displays up to 10 items per page, mouse-wheel scrolling
- **Extract buttons** — Click to extract 1, Shift+click to extract 64
- **REI cost display** — Shows "REI Cost: 10 per extraction" in UI

### 3. REI Energy System

The Hikidashi requires **REI (Reiryoku/霊力 — spiritual energy)** to operate.

**Capacity:** 1000 REI (receives from adjacent generators, flows every 20 ticks)

**Extraction cost:** 10 REI per extraction action

**Energy decay (passive maintenance):**
- Every 60 seconds (1200 ticks), the block loses REI to spiritual leakage
- **Idle loss:** -5 REI per minute (minimal background decay)
- **Active loss:** -8 REI per minute (tracked 5 seconds after extraction)
- Creates a maintenance loop—cannot simply charge and forget

**Corruption buildup:**
- As REI decays, **corruption accumulates** (1:1 ratio)
- Corruption ranges 0-100
- **When corruption > 20:** Purple miasma particles spawn around the block
- More corruption = more visible miasma (visual warning system)

### 4. Purification Ritual

**Tool:** Gohei (御幣 — Shinto purification wand, musubi:gohei)

**How to purify:**
- Right-click the Hikidashi with a gohei in hand
- Instantly clears all corruption to 0
- Plays mystical purification sound (`RESPAWN_ANCHOR_DEPLETE`)
- Displays message: "✦ Miasma cleansed by gohei ✦"

**Thematic note:** The gohei is the traditional Shinto purification tool, making this a perfect match for the shrine maiden aesthetic. The ritual creates an immersive spiritual maintenance loop.

## Extraction Flow

1. **Player opens GUI** — Right-click Hikidashi
2. **Views aggregated storage** — Searches/filters items as needed
3. **Checks REI cost** — GUI displays "10 REI per extraction"
4. **Clicks extract button** — Sends `HikidashiExtractPayload` to server
5. **Server validates:**
   - Is there sufficient REI? (consume 10 REI)
   - Is the item available in nearby racks?
   - Extract up to requested amount
6. **Returns silk skein** — Item given to player as a Skein with proper SkeinContents
7. **Corruption builds** — Each extraction contributes to miasma decay rate

## Network Packets

### OpenHikidashiScreenPayload (server → client)
- Sent when player right-clicks the block
- Contains: block position, full list of aggregated SkeinContents
- Handler: Opens HikidashiScreen with storage data

### HikidashiExtractPayload (client → server)
- Sent when player clicks extract button
- Contains: block position, item ID, amount to extract
- Handler: Validates energy, extracts from racks, gives item to player

## Block Entity Ticker

**Server-side ticker:** `HikidashiBlockEntity::serverTick`
- Runs every tick on the server
- Handles REI decay (once per 60 seconds)
- Tracks extraction activity (`ticksSinceExtraction`)
- Spawns miasma particles when corrupted
- Updates block entity state

## Renderer

**Block Entity Renderer:** `HikidashiBER`
- Currently a placeholder (TODO: implement crane model)
- Will render the magical crane creature when model is created
- Uses standard BlockEntityRenderer pattern

## Integration Points

### With Spool Racks
- Hikidashi can only extract from Spool Racks with matching item IDs
- Uses `SpoolRackBlockEntity::tryExtract()` for extraction
- Works with the standard skein storage system

### With REI Network
- Acts as a **consumer** of REI energy
- Integrates with the mod's REI capability system
- Energy flows from adjacent generators automatically
- Persistence through NBT (saves REI and corruption on world save)

### With Shrine Maiden Gameplay
- Gohei is the purification tool (fits narrative)
- Creates a maintenance ritual cycle
- Encourages player interaction beyond simple "set it and forget it"

## Technical Implementation

**Main classes:**
- `HikidashiBlock` — Block definition, right-click interactions
- `HikidashiBlockEntity` — Storage querying, REI management, corruption tracking
- `HikidashiScreen` — Client-side GUI with search/filter/extract
- `HikidashiBER` — Renderer (placeholder)
- `OpenHikidashiScreenPayload` — Network packet (server→client)
- `HikidashiExtractPayload` — Network packet (client→server)

**Key methods:**
- `HikidashiBlockEntity::queryStorage()` — Returns filtered list of available skeins
- `HikidashiBlockEntity::extractItem()` — Consumes REI and pulls from racks
- `HikidashiBlockEntity::purify()` — Clears corruption counter
- `HikidashiBlock::useItemOn()` — Handles gohei purification

## Design Philosophy

The Hikidashi intentionally **breaks the "set and forget" storage block archetype** seen in tech mods:

1. **Energy is not infinite** — Requires REI flowing from somewhere
2. **Maintenance is required** — Corruption builds up and must be cleansed
3. **Rituals matter** — Using a gohei to purify reinforces the shrine maiden theme
4. **Visual feedback** — Miasma particles warn players before problems occur

This transforms storage from a *utility* into a *living system* that requires periodic spiritual maintenance, fitting the Japanese shrine aesthetic perfectly.

## Future Enhancements

- [ ] Implement crane model in HikidashiBER
- [ ] Add animation to crane (perching, looking away when observed)
- [ ] Miasma effect on other blocks if corruption reaches 100
- [ ] Gohei enchantment to permanently reduce decay rate
- [ ] Sound effects for extraction (silk shimmer/transform)
