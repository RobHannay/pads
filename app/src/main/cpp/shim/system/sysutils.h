// Shim header. VectorOpsComplex.cpp (rubberband v4.0.0) includes
// "system/sysutils.h" but the actual header lives at common/sysutils.h.
// Keeping this shim means we don't have to patch the vendored submodule.
#pragma once
#include "common/sysutils.h"
