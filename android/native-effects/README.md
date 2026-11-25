# Anticombustión nativa ARM64 para Minecraft 26.66

El perfil activo corresponde exclusivamente al motor 1.26.66.01_RC0
(SHA-256 y GNU Build ID en profile.json). El perfil 1.26.60.4 anterior se
conserva como referencia en profiles/; requiere su módulo anterior.

Se actualizaron y comprobaron las 29 instrucciones de límites, las 33
referencias a la tabla de efectos y las funciones de registro, consulta y
HUD. La biblioteca comprueba el Build ID y las huellas de instrucciones
antes de cambiar memoria. El launcher comprueba además el SHA-256 de la
biblioteca del juego antes de cargar cualquier complemento nativo.

La emulación ejecuta las instrucciones ARM64 del motor aportado por el
usuario y el módulo compilado: registro propio, resolución por nombre e ID,
namespace de scripts, duración, icono, conservación de efectos normales,
colisión de ID y ampliación del HUD. El complemento original del Nether
mantiene su hash; su detector y su detour v2 se comprobaron con la nueva
estructura de argumentos (offset 0x60).

Estas comprobaciones no sustituyen una prueba en Android ni un mundo real.
Los resultados reproducibles están en ../../validation/.

Requiere Python con pyelftools, numpy, unicorn y ziglang 0.13.0:

```bash
python3 generate_profile.py /ruta/local/libminecraftpe.so
python3 build_native.py --output /ruta/local/libBodriosafioEffects.so
python3 emulate_registration.py /ruta/local/libminecraftpe.so /ruta/local/libc++_shared.so /ruta/local/libBodriosafioEffects.so --output /ruta/local/resultado.json
```

No contiene el motor ni archivos de licencia de Minecraft. Las bibliotecas
del juego se utilizan localmente para verificar el mod.
