# Water Moon Reflection Shader

A NeoForge core-shader system that renders a real-time specular reflection of the moon on
still-water surfaces at night. It is a true reflection, not a glow decal: the fragment
shader mirrors the view ray off a rippled water normal and lights each fragment by how
well that reflected ray lines up with the moon's actual position in the sky. The bright
"moon path" therefore sits between the camera and the moon's azimuth, elongates toward
the horizon, and moves as the player or the moon moves.

Gameplay purpose: the reflection is the standing cue for which water can host the moon
rite (sky-lit, still source water, on a clear night with a visible moon).

## Components

### Shaders (`assets/musubi/shaders/core/`)
- **water_moon_reflect.vsh** — Vertex shader
  - Passes camera-relative world-space position through to the fragment stage
    (vertices are baked camera-relative on the CPU; camera rotation lives in
    `ModelViewMat`, so the interpolated position stays in world axes with the
    camera at the origin)
- **water_moon_reflect.fsh** — Fragment shader
  - Builds an analytic wave normal from four summed directional sine wavelets
    (world-space XZ + `GameTime`, so ripples are stable as the camera moves)
  - Reflects the view ray off that normal and compares it against `MoonDir`:
    a smoothstepped disc sized to the vanilla moon (~5.7° half-angle) plus a
    soft `pow` halo; the wavelets smear the disc into the glitter path
  - Schlick fresnel — strong looking across the water toward the moon, faint
    looking straight down
  - Faint reflected-sky term so moonlit water reads as a mirror surface
  - Output alpha carries total strength; rendered additively (`SRC_ALPHA, ONE`)
- **water_moon_reflect.json** — Program definition
  - Vertex format: `POSITION_COLOR`
  - Uniforms: `ModelViewMat`, `ProjMat`, `GameTime` (auto-set by vanilla),
    `MoonDir`, `MoonBrightness`, `CameraPos` (set per frame from Java)
  - Note: `blend` blocks in core-shader JSONs are ignored in 1.21.1; blending is
    configured via `RenderSystem` at draw time

## The Moon Rite (gameplay tie-in)

Throwing (Q-dropping) a **feather** into the moon's reflection completes the rite:
one feather is consumed and a **bone tama** rises out of the water at the splash
point, with the burst VFX and chime.

The shader cannot report anything back from the GPU, so the server independently
recomputes the same optics via `MoonReflectionMath` (shared client/server code in
`com.sanhiruzu.musubi.util`):

- `moonDirection` — the moon vector both the shader uniforms and the rite use
- `isMoonlitNight` / `isReflectiveWater` — the shared environment gates
- `isInReflection(eye, surfacePoint)` — mirrors the eye→splash ray across the
  water plane and tests alignment against the moon within `COS_GLITTER_PATH`
  (~17° half-angle: the ~5.7° moon disc plus up to ~11° of deflection from the
  shader's ripple slopes — i.e. everywhere the thrower can see sparkle)

Because a specular reflection is viewer-dependent, the check is anchored to the
**thrower's** eye at the moment of splash-down — each player's valid throw zone is
exactly the path they see on their own screen. `MoonRite` (in
`com.sanhiruzu.musubi.rite`) listens to `EntityTickEvent.Post`, catches
player-thrown feather `ItemEntity`s on first water contact (one evaluation per
throw, flagged in persistent data), and fires the reward + `MoonRiteVfxPayload`.

Geometry consequence worth knowing: from shore (eye ~1.6 above water, Q-drop lands
~2–4 blocks out), the throw angle is ~28° below horizontal, so shore throws succeed
while the moon is low-to-mid sky (roughly elevation 11–45°, early/mid night). Near
midnight the moon is overhead and the reflection sits at the thrower's feet — you
must stand over the water and drop the feather almost straight down. The visual
path always shows the truth, so the mechanic is self-teaching.

### Java Classes (`com.sanhiruzu.musubi.client.render`)
- **WaterMoonShader** — Shader lifecycle and math
  - `registerShader(RegisterShadersEvent)` — loads `musubi:water_moon_reflect`
    with `DefaultVertexFormat.POSITION_COLOR`
  - `moonDirection(level, partialTick)` — world-space moon vector derived from
    `getSunAngle` (vanilla sun sits at `(-sin a, cos a, 0)`; the moon is opposite)
  - `updateUniforms(level, cameraPos, partialTick)` — pushes `MoonDir`,
    `MoonBrightness` (moon phase), `CameraPos`
  - `shouldApply(level, partialTick)` — overworld-type sky, night (13000–23000),
    not raining, moon phase bright enough, moon above the horizon
- **MoonWaterRenderer** — Geometry and draw
  - Hooks `RenderLevelStageEvent.Stage.AFTER_WEATHER`
  - Rescans for water every 20 ticks: topmost sky-lit **source** water in each
    column within 32 blocks horizontally (+4/−16 vertically of the camera)
  - Each frame bakes one overlay quad per surface (camera-relative, lifted
    0.004 above the fluid height to avoid z-fighting); vertex alpha feathers
    the outer 6 blocks so the effect fades instead of ending at a hard square
  - Draw state: additive blend, depth test **on** (terrain occludes the
    reflection correctly), depth write off, cull off
- **MoonReflectionVfx** — Ritual bursts only
  - The old ambient glow discs were removed in favor of the shader reflection;
    this class now renders only the one-shot rite-completion burst + ripple

## Tuning

Shader (`water_moon_reflect.fsh`):
- Wavelet table in `waveNormal` — wavelength / slope / speed of each ripple
  component; total slope controls how long the glitter path stretches
- `disc` smoothstep thresholds — apparent moon size
- Fresnel floor (`0.06`) — how visible the reflection is when looking straight down

Renderer (`MoonWaterRenderer`):
- `SCAN_RADIUS` (32) / `SCAN_UP` / `SCAN_DOWN` — water search window
- `SCAN_INTERVAL` (20 ticks) — rescan cadence
- `EDGE_FEATHER` (6 blocks) — boundary fade band

## Limitations

- Only the moon and a faint sky dome are reflected — no terrain/entity
  reflections (that would need screen-space reflection or a planar-render pass)
- Moon direction assumes the vanilla celestial track (no custom-sky mods)
- Flowing water and roofed-over water intentionally never reflect
- Incompatible-by-design with full shader packs (Iris/Oculus replace core
  shaders); the effect simply won't stack

## Future Improvements

- Star-field shimmer as a secondary reflection layer
- Wind/weather-driven wave amplitude
- Config for scan radius and reflection strength
