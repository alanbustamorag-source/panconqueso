# Compatibilidad de la preparación

Consulta realizada el 23 de noviembre de 2026.

Fuentes oficiales:

- [Bedrock 26.62, 5 de noviembre](https://feedback.minecraft.net/hc/en-us/articles/50159598275725-Minecraft-Bedrock-Edition-26-62-Hotfix-Changelog).
- [Bedrock 26.63, 11 de noviembre](https://feedback.minecraft.net/hc/en-us/articles/50306516691853-Minecraft-Bedrock-Edition-26-63-Hotfix-Changelog).
- [Índice oficial de versiones estables](https://feedback.minecraft.net/hc/en-us/sections/360001186971-Release-Changelogs).

La publicación 26.63 menciona Windows, servidores y PlayStation 5; no anuncia
ese hotfix para Android. Se usa 26.62 como objetivo Android verificable y
26.63 para Windows. No se interpretan publicaciones de usuarios o versiones
Preview como confirmación oficial de una versión estable posterior.

Reconocer el número de una versión no demuestra que el motor arranque con
el cargador ni que un módulo nativo funcione. La ejecución en ambos sistemas
queda pendiente. Los offsets nativos existentes no se han regenerado ni
se han ampliado sus listas de versiones por suposición.

## Cambio Android

`ProfileRules.compatible` admite 26.60, 26.62 y 26.63 con sus revisiones internas.
`nativeCompatible` solo admite 26.60.4 / 1.26.60.4. En otra versión admitida,
se deshabilitan únicamente los dos módulos propios de Bodriosafío y se prepara
el addon. La comprobación del runtime ya no exige un módulo que se decidió
no cargar. Se conservan las comprobaciones de integridad, UUID y recuperación
de las transacciones del instalador.

## Cambio Windows

Se reconocen los formatos directos y codificados de los hotfix 26.62 y 26.63.
La ruta de ejecución devuelve el modo addon antes de extraer DLL, escribir
la configuración del Nether o realizar una inyección, salvo en 1.26.60.5.
La interfaz distingue ambos resultados.

## Confirmación que falta

Antes de generar una entrega para un dispositivo que muestre otra versión,
registrar su número completo y contrastarlo con la publicación oficial
correspondiente. Para reactivar nativos hace falta validar el binario exacto
de esa compilación; no debe subirse el ejecutable ni la biblioteca del juego
al repositorio para hacer ese análisis.
