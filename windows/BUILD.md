> Migración del 23-11-2026: nuevo canal alanbustamorag-source/panconqueso. Los nativos son obligatorios. La adaptación a 26.62/26.63 está pendiente del binario exacto y una prueba real.

# Bodriosafio Launcher Windows 0.1.6

El EXE incorpora BuildLimitChanger, BodriosafioEffects.dll y el addon 0.3.15
como recursos. El backend extrae y verifica esos recursos y carga ambos
módulos al pulsar Jugar. BodriosafioInitialize se invoca fuera del loader lock.

Reconstrucción: MinGW-w64 C++17, windres, Python 3 y un compilador C++ del host.
Ejecuta bash build.sh. Las variables BODRIO_CXX, BODRIO_WINDRES,
BODRIO_HOST_CXX y BODRIO_BUILD_DIR permiten elegir herramientas y destino.
El script ejecuta 96 comprobaciones del host y genera el manifiesto
launcher-windows-latest.json con tamaño y SHA-256 del nuevo EXE.

Los fuentes del efecto nativo están en native-effects. Para reconstruir esa
DLL se utilizó Zig cc para x86_64-windows-gnu con -shared -O2 y las bibliotecas
kernel32 y user32. Sustituye assets/BodriosafioEffects.dll por el resultado;
build.sh actualizará la huella incorporada al launcher antes de compilar.

El protocolo --apply-launcher-update conserva los cinco argumentos de 0.1.3:
PID anterior, ruta de destino, hash anterior y hash nuevo, precedidos por la
opción. Se verifican los archivos y se espera el cierre del proceso anterior
antes de conservar una copia, reemplazar el EXE y reabrirlo.

../validation/resultados.json distingue comprobaciones realizadas de las pendientes.
Los fixtures test_game/test_effects/test_windows permiten preparar pruebas
de procesos en Windows; no se ejecutaron en este entorno. No son Minecraft.
La DLL de prueba debe incorporarse solo al recurso 205 del harness, usando
BODRIO_EFFECTS_SHA256 con su propia huella. No se distribuye en el EXE final.
