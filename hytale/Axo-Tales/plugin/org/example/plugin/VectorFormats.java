package org.example.plugin;

import javax.annotation.Nullable;
import org.joml.Vector3dc;

final class VectorFormats {
    private VectorFormats() {
    }

    static String shortString(@Nullable Vector3dc vector) {
        if (vector == null) {
            return "null";
        }
        return String.format("(%.3f, %.3f, %.3f)", vector.x(), vector.y(), vector.z());
    }
}
