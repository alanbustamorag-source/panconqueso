# Procedencia y modificaciones

Bodriosafío es el proyecto de Alan Bustamante. No es un producto oficial de
Mojang o Microsoft.

El overlay Android parte de LeviLaunchroid 1.6.0 de LeviMC/LiteLDev. Conserva
su licencia Apache-2.0 en `licenses/LeviLaunchroid-Apache-2.0.txt` y los avisos
de las dependencias en `licenses/Android-original-notices.txt`. Las
modificaciones del 23-11-2026 preparan el nuevo origen de actualizaciones,
la versión 0.1.6 y la comprobación de compatibilidad con sus módulos nativos obligatorios. El archivo
`android/upstream-original.json` conserva la procedencia histórica original.

Windows conserva sus dependencias y avisos en `windows/vendor` y
`windows/assets/CREDITOS_Y_LICENCIAS.txt`. BuildLimitChanger pertenece a
Zeuroux y se distribuye con su licencia MIT. El módulo nativo propio tiene
sus fuentes en `windows/native-effects`. Las modificaciones de esta
preparación cambian el origen de descarga y limitan la carga nativa a la
compilación exacta ya documentada.

El addon mantiene los avisos de Mojang incluidos en BP/RP. La dependencia
`@minecraft/server` se referencia por nombre y versión; no se incorpora una
copia del juego ni de su ejecutable. No se reasignan licencias a los archivos
de terceros ni se elimina su autoría.
