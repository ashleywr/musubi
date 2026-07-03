#version 150

uniform float GameTime;       // fraction of the 24000-tick day; wraps at dawn, never mid-night
uniform vec3 MoonDir;         // world-space unit vector toward the moon's center
uniform float MoonBrightness; // vanilla moon-phase brightness, 0..1
uniform vec3 CameraPos;       // world-space camera position

in vec3 relPos;               // fragment position relative to the camera, world axes
in vec4 vertexColor;          // white; alpha feathers the scan-radius boundary

out vec4 fragColor;

const vec3 MOON_TINT = vec3(0.92, 0.95, 1.00);
const vec3 SKY_TINT  = vec3(0.30, 0.42, 0.62);

// Gradient of one directional sine wavelet; "slope" is the surface slope it
// contributes (amplitude * wavenumber), so tuning stays in normal-space.
vec2 waveGrad(vec2 p, float t, vec2 dir, float wavelength, float slope, float speed) {
    float k = 6.2831853 / wavelength;
    return dir * (slope * cos(k * dot(dir, p) - speed * t));
}

// Analytic normal of a few summed wavelets. Total slope is kept small so the
// moon's image smears into a coherent glitter path instead of dissolving
// into noise.
vec3 waveNormal(vec2 p, float t) {
    vec2 g = vec2(0.0);
    g += waveGrad(p, t, vec2( 0.941,  0.339), 6.0, 0.045, 0.9);
    g += waveGrad(p, t, vec2(-0.573,  0.820), 3.4, 0.028, 1.3);
    g += waveGrad(p, t, vec2( 0.259, -0.966), 2.2, 0.016, 1.7);
    g += waveGrad(p, t, vec2(-0.868, -0.497), 1.3, 0.008, 2.4);
    return normalize(vec3(-g.x, 1.0, -g.y));
}

void main() {
    vec3 viewDir = normalize(relPos);
    // The underside of the surface never mirrors the sky.
    if (viewDir.y >= 0.0) discard;

    float t = GameTime * 1200.0; // seconds of game time (24000 ticks / 20 tps)
    vec3 n = waveNormal(relPos.xz + CameraPos.xz, t);

    // True mirror optics: bounce the view ray off the rippled surface and light
    // the fragment by how well it lines up with the actual moon. The bright path
    // therefore sits between the camera and the moon's azimuth and tracks both.
    vec3 refl = reflect(viewDir, n);
    float alignment = dot(refl, MoonDir);

    // The vanilla moon quad subtends ~5.7 degrees of half-angle (cos ~0.995);
    // the wavelets stretch this disc lengthwise into the glitter path.
    float disc = smoothstep(0.9945, 0.9962, alignment);
    float halo = pow(max(alignment, 0.0), 40.0) * 0.12;

    // Schlick fresnel: strong looking across the water toward the moon, faint
    // looking straight down — how a real water reflection behaves.
    float facing = clamp(dot(-viewDir, n), 0.0, 1.0);
    float fresnel = 0.06 + 0.94 * pow(1.0 - facing, 5.0);

    // A whisper of reflected sky dome so moonlit water reads as a mirror
    // surface rather than a painted-on highlight.
    float horizon = pow(1.0 - clamp(refl.y, 0.0, 1.0), 2.0);
    vec3 color = MOON_TINT * (disc * 1.3 + halo) + SKY_TINT * (0.05 + 0.06 * horizon);

    // Rendered additively (src_alpha, one): alpha scales how much light is added.
    float strength = fresnel * MoonBrightness * vertexColor.a;
    fragColor = vec4(color, strength);
}
