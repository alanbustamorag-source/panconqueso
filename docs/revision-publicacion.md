# Revisión previa a distribución

Revisión técnica del 23-11-2026. No es una garantía de ausencia de bloqueos.

## Reglas consultadas

- [Uso aceptable de GitHub, apartado 3](https://docs.github.com/en/site-policy/acceptable-use-policies/github-acceptable-use-policies): derechos de terceros y comprobaciones de licencias.
- [Condiciones de GitHub, B.3 y D.3](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service): requisitos de las cuentas y derechos sobre lo publicado.
- [Distribución de binarios mediante releases](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github).
- [EULA de Minecraft](https://www.minecraft.net/en-us/eula) y [modificaciones permitidas](https://www.minecraft.net/en-us/usage-guidelines): distribuir el mod por separado del juego.
- [Recurso y licencia de BuildLimitChanger](https://github.com/Zeuroux/BuildLimitChanger).

## Incluido en la preparación

| Elemento | Procedencia y comprobación |
| --- | --- |
| Addon 0.3.15 | Paquete del proyecto, SHA-256 `900687d53f42d257fcd0642cde8a49952aad696e42b99b467c747879f5e255e3`; conserva los avisos de Mojang presentes en los packs |
| Overlay Android | Cambios del proyecto sobre la base LeviLaunchroid; se conserva su licencia Apache-2.0 y sus avisos |
| Código Windows | Código del proyecto y avisos de sus dependencias; no incluye Minecraft.Windows.exe |
| BuildLimitChanger Windows | MIT; coincide con el digest de la publicación v1.1 de Zeuroux: `b6f1d16b1d455c6127b1c9f91b3b400c9a7c1c734c52381bc52387ca626afcf8` |
| BodriosafioEffects.dll | Fuente correspondiente en `windows/native-effects`; SHA-256 `24ca8d216db6a23acc5f6ec5ccd99735abb5eb1a05bc6ecc1d155fcf9335ea6a`; su finalidad es el efecto propio del mod |

No se considera que una DLL o un EXE sea una infracción por su extensión.
Se revisan su procedencia, licencia y comportamiento. Los módulos nativos
quedan limitados a sus compilaciones exactas anteriores.

## Elementos retenidos

- Clave privada de firma, contraseñas y tokens: fuera del repositorio y de
  cualquier paquete de distribución.
- APK Android 0.1.5 existente: no se sube como entrega de esta migración.
- Módulo `minecraft` del proyecto Android completo: no se incorpora a este
  overlay. En la base revisada, `StubStore.hasVerifiedLicense()` devuelve
  `true` y `MainActivity.checkLicense()` devuelve `0`. También existe una
  validación exterior que exige instalación desde Google Play. Estas
  observaciones no permiten afirmar por sí solas una infracción, pero hace
  falta aclarar el mecanismo y sus derechos antes de redistribuir la APK.
- APK/EXE y bibliotecas originales del juego: excluidos.
- EXE Windows antiguo: contiene el origen antiguo; no se presenta como una
  compilación del código migrado.

## Publicación pendiente

1. Completar la revisión del mecanismo de licencia de la base Android.
2. Aplicar el overlay sobre la base completa y revisar el diff.
3. Compilar y probar el arranque y los packs en el juego objetivo.
4. Firmar Android con la misma clave fuera de GitHub.
5. Decidir un destino de descarga accesible a los jugadores. El repositorio
   actual continúa privado y las aplicaciones no contienen credenciales.
6. Subir primero el binario comprobado y generar después su manifiesto con
   el tamaño, SHA-256 y código reales.

La causa de la suspensión de la cuenta anterior no se conoce. Las condiciones
permiten una cuenta personal gratuita por persona y ofrecen un procedimiento
de apelación. Cambiar nombres de archivos o de repositorio no determina que
una cuenta nueva cumpla esas condiciones.
