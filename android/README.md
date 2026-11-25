# Android 0.1.6 / 10012 para Minecraft 26.66 ARM64

El overlay se aplica sobre el proyecto completo 0.1.5 r7. El canal nuevo es
alanbustamorag-source/panconqueso. Conserva la identidad de la app, las
correcciones de almacenamiento, la pastilla 0.3.15 y la barra de carga.

Nether y Anticombustión nativos son requisitos. El perfil activo corresponde
al motor 1.26.66.01_RC0 aportado por el usuario, verificado por SHA-256 y GNU
Build ID. Otro motor se rechaza antes de cargar complementos; no se cambia
a una edición de solo addon.

Las instrucciones de registro, namespace de scripts, duración, icono y HUD
se comprobaron en emulación. El detector del Nether encuentra el constructor
nativo nuevo; el detour compilado se comprobó con sus argumentos reales y
conserva las alturas de las otras dimensiones. La APK de prueba está compilada y firmada con el mismo certificado del launcher anterior; el registro de la entrega está en validation/android-26.66-apk.json. Queda la prueba dentro del juego en Android.

## Preparar la base completa

```bash
python3 android/prepare_project.py /ruta/Bodriosafio_Launcher_Android_0_1_5_Fuentes.zip --output /ruta/nueva/Android_0_1_6
```

Requiere ziglang 0.13.0 para compilar el módulo. También admite
--native-effects-library con una biblioteca ya compilada que coincida con
el hash fijado. Comprueba el ZIP base, aplica el overlay y copia el módulo
actualizado. No copia claves ni publica una APK. El proyecto Gradle requiere
JDK 21, Android SDK 36 y Build Tools 36.0.0.

Las fuentes y metadatos propios están en native-effects. No se distribuye
libminecraftpe.so ni se modifican los controles de la copia oficial del juego.
Mantener la misma clave de firma fuera del repositorio permite actualizar
la app existente. La prueba en un mundo real sigue siendo necesaria.
