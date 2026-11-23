# Bodriosafío: preparación del nuevo canal

Repositorio privado de trabajo: `alanbustamorag-source/panconqueso`.

Se preparó la migración del addon 0.3.15 y de los canales de actualización.
Android apunta a la próxima versión **0.1.6, código 10012**; Windows, a **0.1.5**.
No se ha publicado una APK ni un EXE nuevo.

## Versiones del juego

| Plataforma | Hotfix estable verificado el 23-11-2026 | Estado de esta preparación |
| --- | --- | --- |
| Android | 26.62 | Código adaptado; falta compilar y probar en el dispositivo |
| Windows | 26.63 | Código adaptado; falta compilar y probar dentro del juego |

Las fuentes oficiales están en [compatibilidad.md](docs/compatibilidad.md).
La preparación reconoce 26.60, 26.62 y 26.63 y conserva el addon incluido sin
cambiar sus UUID ni su jugabilidad. No anuncia compatibilidad con Preview.

Los módulos nativos conservan sus compilaciones exactas anteriores:
Android `1.26.60.4` y Windows `1.26.60.5`. En las versiones nuevas, el código
preparado inicia **modo addon**, sin cargar esos módulos. Por eso el Nether
ampliado y el registro nativo del efecto no se anuncian como disponibles en
los hotfix nuevos. La lógica de la pastilla y su contador pertenece al addon.

## Contenido

- `Bodriosafio_0_3_15.mcaddon`: paquete original, conservado byte por byte.
- `latest.json`: manifiesto preparado para el nuevo repositorio; su descarga
  anónima todavía no está habilitada porque el repositorio sigue privado.
- `android/overlay`: archivos que cambiaron en la preparación; **no es el
  proyecto Android completo ni una APK compilada**.
- `android/tests`: verificaciones del instalador y reglas del perfil.
- `windows`: fuentes, avisos de licencia y dos módulos de recursos revisados;
  el ejecutable antiguo no forma parte de esta publicación.
- `docs`: decisiones sobre distribución, licencias y pendientes.

## Actualizaciones para los jugadores

El código usa el nuevo repositorio tanto en los enlaces como en las
validaciones del origen de descarga. No se coloca ningún token de GitHub
en el launcher. Mientras el repositorio sea privado, las descargas automáticas
sin cuenta de GitHub no funcionarán. Para habilitarlas falta decidir un
destino público de distribución y comprobar las descargas antes de anunciar
una APK o un EXE en sus manifiestos.

La APK Android anterior sigue apuntando al repositorio antiguo: subir este
código no cambia las aplicaciones ya instaladas. Hace falta una nueva
compilación firmada con la misma clave y una instalación manual inicial.

## Validación

Ejecuta `python3 scripts/validate.py`. Comprueba el addon, el origen de los
enlaces, la exclusión de claves, las reglas Android y las pruebas C++ del host.
Los resultados reales de esta preparación se guardan en
`validation/resultados.json`. Las comprobaciones del host no sustituyen una
prueba en Android o dentro de Minecraft para Windows.
