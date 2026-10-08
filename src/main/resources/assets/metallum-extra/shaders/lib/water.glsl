// Water. Needs globals.glsl, color.glsl, noise.glsl, sky.glsl, shadow.glsl, lighting.glsl, fog.glsl and waving.glsl.

// Copies of the world's color and depth made just before translucent terrain is drawn.
uniform sampler2D MxSceneColor;
uniform sampler2D MxSceneDepth;

// How fast red, green and blue light die out per block of water. Red goes first, which is what makes depth blue.
const vec3 MX_WATER_ABSORB = vec3(0.40, 0.16, 0.10);

// Where a pixel of the world image is in view space, given the depth stored for it.
vec3 mx_view_position(vec2 uv, float depth) {
    vec4 view = MxProjInv * vec4(uv * 2.0 - 1.0, depth, 1.0);
    return view.xyz / view.w;
}

// Follows a reflected ray through the copy of the world image and returns what it runs into: rgb is that
// color (linear), a is how sure the hit is (0 for none; fades out near the edge of the screen, beyond which
// nothing is known).
vec4 mx_reflect_scene(vec3 origin, vec3 direction, float jitter) {
    float stride = 0.3;
    vec3 previous = origin;
    vec3 point = origin + direction * stride * (0.5 + jitter);
    for (int i = 0; i < 24; i++) {
        vec4 clip = MxProj * vec4(point, 1.0);
        if (clip.w <= 0.0) {
            break;
        }
        vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            break;
        }
        float depth = textureLod(MxSceneDepth, uv, 0.0).r;
        // Depth 0 is the sky; the ray cannot hit that.
        if (depth > 0.0) {
            // View space looks down -z, so a larger z is nearer the camera.
            float in_front = mx_view_position(uv, depth).z - point.z;
            if (in_front > 0.0 && in_front < stride * 2.0 + 0.8) {
                // Passed through a surface since the last step: halve the gap a few times to find where.
                vec3 near = previous;
                vec3 far = point;
                for (int j = 0; j < 4; j++) {
                    vec3 middle = (near + far) * 0.5;
                    vec4 middle_clip = MxProj * vec4(middle, 1.0);
                    vec2 middle_uv = middle_clip.xy / middle_clip.w * 0.5 + 0.5;
                    float middle_depth = textureLod(MxSceneDepth, middle_uv, 0.0).r;
                    if (middle_depth > 0.0 && mx_view_position(middle_uv, middle_depth).z > middle.z) {
                        far = middle;
                    } else {
                        near = middle;
                    }
                }
                vec4 hit_clip = MxProj * vec4(far, 1.0);
                vec2 hit_uv = hit_clip.xy / hit_clip.w * 0.5 + 0.5;
                vec2 margin = min(hit_uv, 1.0 - hit_uv);
                float sure = smoothstep(0.0, 0.08, min(margin.x, margin.y));
                return vec4(mx_to_linear(textureLod(MxSceneColor, hit_uv, 0.0).rgb), sure);
            }
        }
        previous = point;
        stride *= 1.28;
        point += direction * stride;
    }
    return vec4(0.0);
}

// The water's surface: the broad waves that also move its vertices, plus ripples of two sizes drifting across
// each other. Noise instead of more sine waves, which would line up into a visible grid.
float mx_water_surface(vec2 world, float time) {
    float height = mx_water_height(world, time) * 0.6
         + 1.1 * mx_noise(world * 1.3 + vec2(time * 0.55, time * 0.21))
         + 0.6 * mx_noise(world * 3.1 + vec2(-time * 0.74, time * 0.48));
    // Rain pocks the surface with small, fast, ever-changing dimples.
    float rain = MxSkyAmbient.w;
    if (rain > 0.0) {
        height += rain * 1.4 * (mx_noise(world * 7.0 + vec2(time * 4.1, -time * 3.3)) + mx_noise(world * 9.5 + vec2(-time * 5.2, time * 4.7)) - 1.0);
    }
    return height;
}

// The normal of the rippled surface at a point, for a face that looks up. Ripples flatten out with distance,
// where they would only shimmer.
vec3 mx_water_normal(vec2 world, float time, float distance_to) {
    const float STEP = 0.06;
    float here = mx_water_surface(world, time);
    vec2 slope = vec2(mx_water_surface(world + vec2(STEP, 0.0), time) - here,
                      mx_water_surface(world + vec2(0.0, STEP), time) - here) / STEP;
    float calm = 0.075 / (1.0 + distance_to * 0.07);
    return normalize(vec3(-slope.x * calm, 1.0, -slope.y * calm));
}

// `tint`: the biome's water color, as a display color. `position`: camera-relative world space. `flat_normal`:
// the face's own normal. `light`: block light and sky light. `fog`: the game's fog amount here.
// The result is alpha-blended over what is already drawn: clear where the water is shallow, its own color where
// it is deep, and mirror-like at a shallow viewing angle.
vec4 mx_water(vec3 tint, vec3 position, vec3 flat_normal, vec2 light, float fog) {
    vec2 uv = gl_FragCoord.xy * MxScreen.zw;
    float distance_to = length(position);
    vec3 view = position / distance_to;
    vec3 world = position + MxCameraPos.xyz;
    float time = MxCameraPos.w;
    bool top = flat_normal.y > 0.7;

    vec3 normal = flat_normal;
    if (top && MxParams2.y > 0.5) {
        normal = mx_water_normal(world.xz, time, distance_to);
    }

    // How much water the eye looks through here: none at the shore, a lot over deep water. Through a side face
    // (where water meets air at a cliff or the edge of a pool) the eye looks a long way into the body; that is
    // capped, so such a face reads as water and not as a wall.
    float behind_depth = textureLod(MxSceneDepth, uv, 0.0).r;
    float through = max(length(mx_view_position(uv, behind_depth)) - distance_to, 0.0);
    if (!top) {
        through = min(through, 5.0);
    }
    vec3 passes = exp(-through * MX_WATER_ABSORB);
    float clear = (passes.r + passes.g + passes.b) / 3.0;

    // The light the water itself scatters back: the biome's color, lit like anything else. Water sends back only
    // a small part of the light that falls on it, which is what makes deep water dark and lets what it mirrors
    // stand out; lit as brightly as land it turns to milk. Deep water loses some of its color as well.
    // A shadow falls on the bottom, which is drawn with it, and where the bottom shows through, that is the one
    // shadow to see: the water above it takes the shadow only as far as the bottom is hidden. Shading the
    // surface as well would draw the same shadow twice, at two depths.
    float facing = mx_facing(flat_normal, 0.0);
    float sunlit = facing > 0.0 ? mx_shadow(position, flat_normal) : 1.0;
    float shadow = mix(1.0, sunlit, 1.0 - clear);
    vec3 scatter = mx_lit(mx_to_linear(tint), vec3(0.0, 1.0, 0.0), position, light, facing, shadow);
    scatter = mix(scatter, vec3(mx_luminance(scatter)) * 0.85, 0.30 * (1.0 - clear));
    vec3 body = mx_tonemap(scatter * (0.10 + 0.06 * clear) * MxParams.y);

    // What the surface mirrors: the sky, the sun's glint, and with reflections on, the world itself. Real water
    // mirrors only a few percent until seen almost edge-on; this mirrors a good deal more at every angle, the
    // way shader packs do, so that whatever stands by the water shows in it even from above.
    float cosine = clamp(dot(-view, normal), 0.0, 1.0);
    float mirror = 0.14 + 0.86 * pow(1.0 - cosine, 2.2);
    if (!top) {
        mirror *= 0.4;
    }
    vec3 mirrored = reflect(view, normal);
    if (top) {
        mirrored.y = max(mirrored.y, 0.02);
    }
    mirrored = normalize(mirrored);
    float towards_light = max(dot(mirrored, MxLightDir.xyz), 0.0);
    float glint = pow(towards_light, 900.0) * 70.0 + pow(towards_light, 80.0) * 0.7;
    vec3 sky = mx_sky(mirrored) + MxLightColor.rgb * glint * sunlit * MxLightDir.w;
    // Under a roof or deep in a cave there is no sky to mirror.
    vec3 reflection = mx_tonemap(sky * MxParams.y) * smoothstep(0.1, 0.9, light.y);
    if (MxParams2.z > 0.5) {
        vec4 hit = mx_reflect_scene(mat3(MxView) * position, mat3(MxView) * mirrored, mx_dither(gl_FragCoord.xy));
        reflection = mix(reflection, hit.rgb, hit.a);
    }

    // Blend: the image behind keeps `clear * (1 - mirror)` of itself; the rest is body and reflection.
    float alpha = 1.0 - clear * (1.0 - mirror);
    vec3 over = (body * (1.0 - clear) * (1.0 - mirror) + reflection * mirror) / max(alpha, 1.0e-3);

    // Fog, as on land.
    float amount = mx_fog_total(position, fog, 1.0);
    over = mix(over, mx_tonemap(mx_sky(view) * MxParams.y), amount);
    alpha = mix(alpha, 1.0, amount);

    return vec4(mx_to_display(mx_grade(over)), alpha);
}

// The surface seen from under water. Looking up, the world above shows through a window of light; towards its
// edge the surface turns to a mirror of the water below, and past it (about 49 degrees from straight up) it
// reflects everything, since light at that angle cannot leave the water. The ripples bend both the window and
// what shows through it.
vec4 mx_water_below(vec3 tint, vec3 position, vec3 flat_normal, vec2 light, vec4 game_fog) {
    float distance_to = length(position);
    vec3 view = position / distance_to;
    vec3 world = position + MxCameraPos.xyz;
    float time = MxCameraPos.w;

    // The face's normal already points down, at the camera; ripple it like the top side, turned over.
    vec3 normal = flat_normal;
    if (flat_normal.y < -0.7 && MxParams2.y > 0.5) {
        normal = -mx_water_normal(world.xz, time, distance_to);
    }
    float cosine = clamp(dot(-view, normal), 0.0, 1.0);
    float sine_out = 1.33 * sqrt(max(1.0 - cosine * cosine, 0.0));
    float window = 1.0 - smoothstep(0.70, 1.0, sine_out);

    // Through the window: the sky in the direction the light really comes from, so the ripples bend it, with
    // the sun's glint. The world above (trees, the shore) is already behind the surface in the picture and shows
    // through by the alpha.
    float open = smoothstep(0.1, 0.9, light.y);
    vec3 out_dir = refract(view, normal, 1.33);
    if (dot(out_dir, out_dir) < 0.5) {
        out_dir = vec3(0.0, 1.0, 0.0);
    }
    float glint = pow(max(dot(out_dir, MxLightDir.xyz), 0.0), 40.0) * 0.6;
    vec3 above = mx_tonemap((mx_sky(out_dir) * 1.2 + 0.08 + MxLightColor.rgb * glint * MxLightDir.w) * MxParams.y) * open;

    // Past the window: a mirror of the water and the bottom below.
    vec3 below = mx_to_linear(game_fog.rgb) * 0.55;
    vec3 reflection = below;
    if (MxParams2.z > 0.5) {
        vec3 mirrored = reflect(view, normal);
        vec4 hit = mx_reflect_scene(mat3(MxView) * position, mat3(MxView) * mirrored, mx_dither(gl_FragCoord.xy));
        reflection = mix(below, mx_tonemap(hit.rgb * MxParams.y), hit.a * 0.8);
    }

    vec3 color = mix(reflection, above, window);
    float alpha = mix(0.92, 0.42, window);

    // The game's water fog, so the surface far off fades like everything else.
    float amount = clamp(distance_to / 50.0, 0.0, 1.0) * game_fog.a;
    color = mix(color, mx_to_linear(game_fog.rgb), amount);
    alpha = mix(alpha, 1.0, amount);
    return vec4(mx_to_display(color), alpha);
}
