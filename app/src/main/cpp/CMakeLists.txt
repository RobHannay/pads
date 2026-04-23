cmake_minimum_required(VERSION 3.22.1)
project(rubberband-jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(RB ${CMAKE_CURRENT_SOURCE_DIR}/rubberband)

# All sources needed for a self-contained rubberband build (R2 + R3 + JNI)
# using the bundled KissFFT-free BQResampler and built-in FFT (no external deps).
set(RB_SOURCES
    ${RB}/src/RubberBandStretcher.cpp
    ${RB}/src/RubberBandLiveShifter.cpp
    ${RB}/src/rubberband-c.cpp
    ${RB}/src/faster/AudioCurveCalculator.cpp
    ${RB}/src/faster/CompoundAudioCurve.cpp
    ${RB}/src/faster/HighFrequencyAudioCurve.cpp
    ${RB}/src/faster/PercussiveAudioCurve.cpp
    ${RB}/src/faster/R2Stretcher.cpp
    ${RB}/src/faster/SilentAudioCurve.cpp
    ${RB}/src/faster/StretcherChannelData.cpp
    ${RB}/src/faster/StretcherProcess.cpp
    ${RB}/src/finer/R3Stretcher.cpp
    ${RB}/src/finer/R3LiveShifter.cpp
    ${RB}/src/common/Allocators.cpp
    ${RB}/src/common/BQResampler.cpp
    ${RB}/src/common/FFT.cpp
    ${RB}/src/common/Log.cpp
    ${RB}/src/common/mathmisc.cpp
    ${RB}/src/common/Profiler.cpp
    ${RB}/src/common/Resampler.cpp
    ${RB}/src/common/StretchCalculator.cpp
    ${RB}/src/common/sysutils.cpp
    ${RB}/src/common/Thread.cpp
    ${RB}/src/common/VectorOpsComplex.cpp
    ${RB}/src/jni/RubberBandStretcherJNI.cpp
)

add_library(rubberband-jni SHARED ${RB_SOURCES})

target_include_directories(rubberband-jni PRIVATE
    ${RB}
    ${RB}/src
    ${CMAKE_CURRENT_SOURCE_DIR}/shim
)

target_compile_options(rubberband-jni PRIVATE
    -O3
    -ffast-math
    -ftree-vectorize
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -Wno-unused-parameter
    -Wno-unused-variable
    -Wno-unused-function
)

target_compile_definitions(rubberband-jni PRIVATE
    USE_BQRESAMPLER
    USE_BUILTIN_FFT
    LACK_POSIX_MEMALIGN
    USE_OWN_ALIGNED_MALLOC
    LACK_SINCOS
    NO_TIMING
    NO_TIMING_COMPLETE_NOOP
    NO_THREADING
    NO_THREAD_CHECKS
    NO_EXCEPTIONS
)

find_library(log-lib log)
target_link_libraries(rubberband-jni ${log-lib})
