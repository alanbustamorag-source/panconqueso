# Compatibilidad: nativos obligatorios

El usuario requiere el launcher completo con Nether ampliado y Anticombustión
nativa. Se elimina la propuesta anterior de funcionar sin ellos.

## Perfiles y límites

| Plataforma | Perfil de motor existente | Hotfix objetivo |
| --- | --- | --- |
| Android ARM64 | 1.26.60.4, con SHA-256 y GNU Build ID fijados | 26.62: falta verificar el motor exacto y adaptar el perfil |
| Windows x64 | 1.26.60.5, con SHA-256 fijado | 26.63: falta verificar el ejecutable exacto y adaptar el perfil |

native-support.json separa los perfiles existentes de los objetivos pendientes.
Que el parser reconozca un número o que el programa compile no demuestra que
una biblioteca nativa funcione en esa compilación. Tampoco hay todavía prueba
real de juego de los perfiles anteriores en este entorno.

## Comportamiento preparado

Android exige un perfil nativo antes de recuperar/preparar el perfil de juego.
El runtime exige que se hayan cargado ambos módulos propios. No deshabilita
los módulos para arrancar con solo el addon.

Windows exige su perfil nativo y no devuelve éxito sin el efecto preparado.
Si Minecraft ya se abrió antes de detectar una incompatibilidad, el launcher
no fuerza su cierre ni entra a un mundo: informa que la preparación falló.

No se amplían las listas de builds ni se cambian offsets suponiendo que una
actualización conserve su disposición de memoria.

## Identificación local

scripts/inspect_native_game.py calcula el SHA-256 del motor ARM64 y lee su
GNU Build ID desde ELF. Acepta un APK split local o la biblioteca suelta.
Los binarios se procesan en un temporal local, no se suben a GitHub y no
forman parte de la salida. Un motor no identificado necesita análisis antes
de producir un módulo para esa compilación.

## Fuentes

- [Bedrock 26.62](https://feedback.minecraft.net/hc/en-us/articles/50159598275725-Minecraft-Bedrock-Edition-26-62-Hotfix-Changelog).
- [Bedrock 26.63](https://feedback.minecraft.net/hc/en-us/articles/50306516691853-Minecraft-Bedrock-Edition-26-63-Hotfix-Changelog): Windows, servidores y PS5.
- [Índice oficial](https://feedback.minecraft.net/hc/en-us/sections/360001186971-Release-Changelogs).
- [LeviLauncher y la copia legítima](https://docs.levimc.org/LeviLauncher/android/).
- [SDK oficial de mods nativos](https://docs.levimc.org/LeviLauncher/android/guide/developer).

Falta confirmar la versión completa instalada del usuario. Estos objetivos
son los hotfix oficiales que se pudieron verificar; no garantizan que sean
la copia instalada ni que los nativos nuevos estén adaptados.
