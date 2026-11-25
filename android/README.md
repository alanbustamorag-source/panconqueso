# Android 0.1.8 (10014), versión de prueba

El overlay se aplica sobre el proyecto completo 0.1.5 r7; no es un proyecto
Gradle independiente. El canal nuevo es alanbustamorag-source/panconqueso.

## Publicación y actualizaciones

La APK firmada 0.1.7 / 10013 se descarga desde la release `android-0.1.7`.
`launcher-latest.json` contiene su tamaño y SHA-256 reales. El código 10013 no
se ofrece como actualización de sí mismo: las entregas siguientes deben aumentar
`version_code` y conservar la misma clave de firma. El addon usa su propio
`latest.json`. La comprobación de distribución no valida los nativos en el juego.

## Compatibilidad de las fuentes del overlay

Nether y Anticombustión nativos son requisitos. nativeCompatible conserva
solo el perfil de motor 1.26.60.4. Otras compilaciones se rechazan antes de
preparar los módulos o modificar su estado. No se cambia a una edición de
solo addon. En 26.66.1 se identificó el motor ARM64 y se habilitó el actualizador del addon. La adaptación de Anticombustión para ejecutar el juego sigue pendiente.

La pantalla principal utiliza el perfil que el jugador ya seleccionó en Herramientas. Para abrir el juego con un perfil importado de 26.60.4 también se necesita la instalación oficial de Google Play.

Las fuentes del módulo ARM64 están en native-effects. No se distribuye
libminecraftpe.so ni se eliminan los controles de la copia oficial del juego.
La licencia debe evaluarse junto con el flujo completo y las condiciones
del proveedor; un método de compatibilidad con resultado fijo no prueba
por sí solo una infracción.

## Preparar la base completa

~~~bash
python3 android/prepare_project.py /ruta/Bodriosafio_Launcher_Android_0_1_5_Fuentes.zip --output /ruta/nueva/Android_0_1_8
~~~

El script comprueba el SHA-256 y las rutas, aplica el overlay y conserva la
identidad de la app. No copia claves, no ejecuta workflows heredados y no
compila ni publica una APK.

Antes de firmar una entrega, completar el port sobre la compilación exacta
y probar los módulos dentro del juego. Mantener la misma clave de firma fuera
del repositorio para conservar la posibilidad de actualizar la app existente.

Las descargas del addon y del launcher incluyen una barra y porcentaje.
La carga de Minecraft muestra el porcentaje; los detalles técnicos quedan
en el registro. La actualización conserva la firma y la identidad existentes.
