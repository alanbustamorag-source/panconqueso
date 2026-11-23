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

Para preparar la base completa en una carpeta nueva:

```bash
python3 android/prepare_project.py /ruta/Bodriosafio_Launcher_Android_0_1_5_Fuentes.zip --output /ruta/nueva/Android_0_1_6
```

El preparador exige el SHA-256 de la base revisada, comprueba las rutas del
ZIP, aplica el overlay y conserva el paquete Android. No copia claves, no
ejecuta workflows heredados y no compila ni publica una APK.
