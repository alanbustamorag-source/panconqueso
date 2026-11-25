# Anticombustión nativa ARM64: perfil de referencia

Fuentes del proyecto conservadas desde la base Android 0.1.5 r7.
effects.c, profile.h y profile.json corresponden al motor identificado
como 1.26.60.4, no a un port de 26.62.

El módulo comprueba el GNU Build ID y las huellas de las instrucciones antes
de aplicar parches. No debe retirarse esa comprobación para hacerlo cargar
en otra compilación. Las direcciones de registro, factoría, tabla, HUD y
vectores de efectos necesitan validarse sobre el motor nuevo.

No se incluyen libminecraftpe.so, una APK de Minecraft ni archivos de licencia
del juego. Los perfiles contienen metadatos y huellas para el mod.
