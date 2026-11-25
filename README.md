# Bodriosafío: launcher completo con módulos nativos

Repositorio privado: alanbustamorag-source/panconqueso.

**Nether ampliado y Anticombustión nativa son requisitos del launcher.**
Se retira la propuesta anterior de iniciar una versión reducida con solo el
addon. Una compilación sin perfil nativo debe mostrar que falta adaptarla;
no debe desactivar los módulos ni anunciar una preparación exitosa.

## Estado real

| Plataforma | Perfil nativo estudiado | Actualización objetivo |
| --- | --- | --- |
| Android ARM64 | 26.66 / motor 1.26.66.01_RC0 | Anticombustión y Nether comprobados en emulación; falta prueba en el dispositivo |
| Windows x64 | Motor identificado como 1.26.60.5 | 26.63: falta verificar el ejecutable exacto y adaptar los nativos |

El motor Android aportado se identificó por SHA-256 y GNU Build ID. Los perfiles y la emulación no equivalen a una prueba dentro del juego. Las huellas y estados están en native-support.json y validation/.

Android usa app 0.1.6 / código 10012. El port de 26.66 incluye 29 parches de límites, 33 referencias de tabla y un HUD ampliado, con comprobaciones de identidad antes de cargar los complementos. La APK de prueba ya está compilada, alineada y firmada con el mismo certificado de 0.1.5 r7; puede actualizarse sobre la instalación anterior. Falta probarla dentro del juego en Android. Los hashes y comprobaciones están en validation/android-26.66-apk.json. Windows 0.1.5 tiene compilación automática, pero
compilar el launcher no adapta sus DLL al motor nuevo ni demuestra que sus
funciones nativas funcionen dentro de Minecraft.

## Contenido

- android/overlay: cambios para aplicar sobre la base Android 0.1.5 r7.
- android/native-effects: fuentes y perfil ARM64 26.66; perfil anterior archivado. No contiene la biblioteca del juego.
- android/prepare_project.py: prepara la base completa localmente desde el
  ZIP original identificado por SHA-256, sin copiar claves ni publicar APK; compila o incorpora el módulo nativo fijado por hash.
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

Los enlaces y sus validaciones de origen usan el nuevo repositorio. La app
ya instalada conserva el origen antiguo hasta instalar una APK nueva.
Las claves y tokens siguen fuera del repositorio y de las aplicaciones.

El repo continúa privado. latest.json está preparado, pero los jugadores sin
acceso no pueden usarlo para descargas automáticas anónimas. Falta resolver
un destino de distribución después de validar los nativos y la entrega.

## Comprobaciones

Ejecutar python3 scripts/validate.py. Los resultados reales están en
validation/resultados.json. Las comprobaciones del host y la compilación
Windows no prueban el arranque, el Nether ampliado ni el efecto nativo dentro
de Minecraft.
