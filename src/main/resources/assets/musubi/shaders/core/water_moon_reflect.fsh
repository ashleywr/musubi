#version 150

uniform float GameTime;       // fraction of the 24000-tick day; wraps at dawn, never mid-night
uniform vec3 MoonDir;         // world-space unit vector toward the moon's center
uniform float MoonBrightness; // vanilla moon-phase brightness, 0..1
uniform vec3 CameraPos;       // world-space camera position

in vec3 relPos;               // fragment position relative to the camera, world axes
in vec4 vertexColor;          // white; alpha feathers the scan-radius boundary

out vec4 fragColor;

// Pale silver-blue — moonlight on water, not daylight.
const vec3 MOON_TINT = vec3(0.88, 0.93, 1.00);

// Gradient of one directional sine wavelet; "slope" is the surface slope it
// contributes (amplitude * wavenumber), so tuning stays in normal-space.
vec2 waveGrad(vec2 p, float t, vec2 dir, float wavelength, float slope, float speed) {
    float k = 6.2831853 / wavelength;
    return dir * (slope * cos(k * dot(dir, p) - speed * t));
}

// Barely-there ripples — the surface is a near-perfect mirror so the moon holds a
// clean round shape; the tiny slope only gives the disc a living, watery edge.
vec3 waveNormal(vec2 p, float t) {
    vec2 g = vec2(0.0);
    g += waveGrad(p, t, vec2( 0.941,  0.339), 7.0, 0.006, 0.35);
    g += waveGrad(p, t, vec2(-0.573,  0.820), 4.5, 0.004, 0.45);
    return normalize(vec3(-g.x, 1.0, -g.y));
}

void main() {
    vec3 viewDir = normalize(relPos);
    // The underside of the surface never mirrors the sky.
    if (viewDir.y >= 0.0) discard;

    float t = GameTime * 1200.0; // seconds of game time (24000 ticks / 20 tps)
    vec3 n = waveNormal(relPos.xz + CameraPos.xz, t);

    // Mirror the view ray; where it lands on the moon we draw the moon's disc.
    // Because the water plane is near-horizontal, the round disc foreshortens into
    // an ellipse stretched toward the viewer — exactly how a real reflection sits.
    vec3 refl = reflect(viewDir, n);
    float alignment = dot(refl, MoonDir);

    // Crisp round disc at the moon's angular radius (exaggerated a touch so the
    // reflected moon reads clearly), with a narrow soft rim from the ripples.
    float disc = smoothstep(0.9880, 0.9918, alignment);
    // Faint limb glow hugging the disc so it doesn't end on a hard line.
    float glow = pow(max(alignment, 0.0), 120.0) * 0.5;

    // Schlick fresnel with a high floor so the reflection stays visible even when
    // peering steeply down at the surface.
    float facing = clamp(dot(-viewDir, n), 0.0, 1.0);
    float fresnel = 0.5 + 0.5 * pow(1.0 - facing, 5.0);

    vec3 color = MOON_TINT * (disc * 2.6 + glow);

    // Rendered additively (src_alpha, one): alpha scales how much light is added.
    // Moon brightness floored so half-phase nights still show a disc.
    float moon = mix(0.6, 1.0, MoonBrightness);
    float strength = (disc + glow * 0.5) * fresnel * moon * vertexColor.a;
    fragColor = vec4(color, strength);
}
