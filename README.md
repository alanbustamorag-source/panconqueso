# Bodriosafío: launcher completo con módulos nativos

Repositorio público de fuentes y actualizaciones: alanbustamorag-source/panconqueso.

**Nether ampliado y Anticombustión nativa son requisitos del launcher.**
Se retira la propuesta anterior de iniciar una versión reducida con solo el
addon. Una compilación sin perfil nativo debe mostrar que falta adaptarla;
no debe desactivar los módulos ni anunciar una preparación exitosa.

## Estado real

| Plataforma | Perfil nativo estudiado | Actualización objetivo |
| --- | --- | --- |
| Android ARM64 | Motor identificado como 1.26.60.4 | 26.62: falta verificar el motor exacto y adaptar los nativos |
| Windows x64 | Motor identificado como 1.26.60.5 | 26.63: falta verificar el ejecutable exacto y adaptar los nativos |

Los perfiles no equivalen a una prueba dentro del juego. Hace falta confirmar
la versión completa instalada del usuario. Las huellas y estados están en
native-support.json.

Android 0.1.7 / código 10013 está publicado como APK ARM64 firmado, con
descarga pública y comprobaciones de integridad del actualizador. La publicación
no sustituye una prueba de los módulos nativos dentro del juego.
Windows 0.1.6 tiene compilación automática, pero
compilar el launcher no adapta sus DLL al motor nuevo ni demuestra que sus
funciones nativas funcionen dentro de Minecraft.

## Contenido

- android/overlay: cambios para aplicar sobre la base Android 0.1.5 r7.
- android/native-effects: fuentes y perfil anterior del módulo ARM64; no
  contienen la biblioteca del juego.
- android/prepare_project.py: prepara la base completa localmente desde el
  ZIP original identificado por SHA-256, sin copiar claves ni publicar APK.
- windows: fuentes y módulos propios/con licencia revisados.
- Bodriosafio_0_3_15.mcaddon: addon original, sin cambiar sus UUID ni reglas.
- scripts/inspect_native_game.py: identifica localmente el motor de un APK
  por SHA-256 y GNU Build ID; solo produce metadatos.
- docs: compatibilidad, procedencia y pendientes.

## Identificar una copia local

~~~bash
python3 scripts/inspect_native_game.py /ruta/split_config.arm64_v8a.apk
~~~

También acepta libminecraftpe.so ARM64. No modifica el juego, no lo envía a
Internet ni exporta sus binarios al repositorio. Una coincidencia con el
perfil antiguo no valida un port nuevo ni sustituye una prueba real.

## Nuevo GitHub y distribución

Los enlaces y sus validaciones de origen de Android 0.1.7 usan el nuevo
repositorio. Las versiones anteriores que conserven el origen antiguo necesitan
instalar esta APK una vez para cambiar de canal.
Las claves y tokens siguen fuera del repositorio y de las aplicaciones.

El addon consulta `latest.json` y el launcher Android consulta
`launcher-latest.json`. La APK se distribuye mediante
[la publicación Android 0.1.7](https://github.com/alanbustamorag-source/panconqueso/releases/tag/android-0.1.7).
Ambos canales permiten lectura pública sin credenciales. El actualizador del
launcher comprueba tamaño, SHA-256, paquete, código de versión y certificado.
No ofrece reinstalar la misma revisión ni bajar a una anterior; Android pide
confirmación para instalar una actualización nueva.

## Comprobaciones

Ejecutar python3 scripts/validate.py. Los resultados reales están en
validation/resultados.json. Las comprobaciones del host y la compilación
Windows no prueban el arranque, el Nether ampliado ni el efecto nativo dentro
de Minecraft.

## Barras de descarga

Android 0.1.7 y Windows 0.1.6 muestran una barra con porcentaje al actualizar
el addon o el launcher. El porcentaje avanza con los bytes recibidos y llega
a 100 después de comprobar y guardar el archivo. Los reintentos empiezan de cero.
La pantalla Android de inicio de Minecraft muestra el porcentaje y conserva
el registro técnico para consultar errores.

Las compilaciones se publican como `android-0.1.7` y `windows-0.1.6`.
Las pruebas de descarga cubren interrupciones, archivos incompletos y límites
de tamaño. Falta comprobar la interfaz en los equipos de los jugadores.
