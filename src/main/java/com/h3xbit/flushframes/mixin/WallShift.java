package com.h3xbit.flushframes.mixin;

// Shared between AttachItemFrameMixin (decides this at placement/direction-change time) and
// WallFrameSyncMixin (recomputes it when the wall's own connections change) so both reference the
// same numbers without either mixin depending on the other.
final class WallShift {
    static final double WALL_PILER = -0.718F;
    static final double WALL_FLAT = -0.78F;
}
