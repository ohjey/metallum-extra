#version 330 core

#ifdef ALPHA_CUTOUT
uniform sampler2D u_BlockTex;
#endif

in vec2 v_TexCoord;
out vec4 fragColor;

void main() {
#ifdef ALPHA_CUTOUT
    // Leaves and plants cast shadows with holes in them.
    if (texture(u_BlockTex, v_TexCoord).a < 0.1) {
        discard;
    }
#endif
    fragColor = vec4(1.0);
}
