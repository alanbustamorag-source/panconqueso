#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
compiler="${BODRIO_CXX:-x86_64-w64-mingw32-g++}"
resource_compiler="${BODRIO_WINDRES:-x86_64-w64-mingw32-windres}"
host_compiler="${BODRIO_HOST_CXX:-g++}"
output="${BODRIO_BUILD_DIR:-build}"
mkdir -p -- "$output"
python3 scripts/update_checksums.py
common=(-std=c++17 -O2 -Wall -Wextra -Werror -Wno-cast-function-type -static -static-libgcc -static-libstdc++ -municode -D_WIN32_WINNT=0x0A00)
libraries=(-luser32 -lgdi32 -ldwmapi -lshell32 -lole32 -ladvapi32 -lbcrypt -lversion -luuid -lwinhttp)
"$resource_compiler" -I . --preprocessor "$compiler" --preprocessor-arg=-E --preprocessor-arg=-xc --preprocessor-arg=-DRC_INVOKED launcher.rc -O coff -o "$output/resources.o"
"$compiler" "${common[@]}" -mwindows main.cpp backend.cpp "$output/resources.o" "${libraries[@]}" -Wl,--no-insert-timestamp,--dynamicbase,--nxcompat -o "$output/Bodriosafio_Launcher_Windows_0_1_6.exe"
for unit in profile channel integrated effects_readiness; do
    "$host_compiler" -std=c++17 -O2 -Wall -Wextra -Werror "test_$unit.cpp" -o "$output/test_$unit"
done
"$output/test_profile"
"$output/test_channel" tests/latest_0_3_13.json
"$output/test_integrated" assets/BodriosafioEffects.dll
"$output/test_effects_readiness"
python3 scripts/write_feed.py "$output/Bodriosafio_Launcher_Windows_0_1_6.exe"
