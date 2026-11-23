# Preparación Android 0.1.6 / 10012

`overlay` contiene archivos para aplicar sobre la versión completa del
proyecto 0.1.5 revisión 7. No es un proyecto Gradle independiente.

Cambios preparados:

- Origen de actualizaciones y validaciones de URL migrados a
  `alanbustamorag-source/panconqueso`.
- Objetivo estable Android 26.62 y reconocimiento de 26.60/26.62/26.63.
- Modo addon para hotfix nuevos; conserva los nativos solo en 1.26.60.4.
- Próxima versión de app 0.1.6, código 10012. No cambia el paquete Android.

Aplicar cada archivo de `overlay` conservando su ruta relativa, revisar el
diff y completar los pasos pendientes de `docs/revision-publicacion.md`.
La APK nueva aún no se ha compilado ni firmado. La firma privada se mantiene
fuera del repositorio. No usar la APK antigua como si contuviera estos cambios.
