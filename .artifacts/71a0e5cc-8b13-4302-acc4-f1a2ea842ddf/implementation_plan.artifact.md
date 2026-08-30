# Plan de Implementación: Motor Metrolist y Letras "Pro"

El objetivo es sustituir los métodos de resolución fallidos por la técnica de Metrolist (WEB_REMIX + poToken) e integrar la visualización de letras en un reproductor rediseñado.

## User Review Required

> [!CAUTION]
> Vamos a eliminar los métodos de Archive.org y Piped del buscador principal para ganar velocidad y estabilidad. Si YouTube bloquea el poToken, fallará más rápido en lugar de reintentar infinitamente.

## Proposed Changes

### [Network / Resolver]

#### [MODIFY] [InnertubeResolver.kt](file:///D:/Android/AndroidStudioProjects/Auto_Music/app/src/main/java/com/danielsalas/auto_music/player/InnertubeResolver.kt)
- **Simplificación Extrema**: Eliminar Archive.org, Piped y Proxies.
- **Identidad WEB_REMIX**: Usar solo `WEB_REMIX` (es el que Metrolist usa con éxito).
- **Manejo de Errores**: Si falla la resolución, devolver una URL vacía y un estado que el reproductor entienda para no entrar en bucle.

#### [MODIFY] [Innertube.kt](file:///D:/Android/AndroidStudioProjects/Auto_Music/app/src/main/java/com/danielsalas/auto_music/data/remote/Innertube.kt)
- Refinar el cuerpo del POST `player` para inyectar el `poToken` en el lugar exacto.

### [UI / Player]

#### [MODIFY] [MainActivity.kt](file:///D:/Android/AndroidStudioProjects/Auto_Music/app/src/main/java/com/danielsalas/auto_music/MainActivity.kt)
- **Rediseño del Reproductor**: Carátula compacta (200dp) + área de letras de alta legibilidad.
- **Configuración**: Implementar el toggle de "Descargar letras" de forma atractiva.

### [Data / Repository]

#### [MODIFY] [MusicRepository.kt](file:///D:/Android/AndroidStudioProjects/Auto_Music/app/src/main/java/com/danielsalas/auto_music/data/MusicRepository.kt)
- Corregir errores de sintaxis previos.
- Asegurar que la descarga de audio y letra está sincronizada.

## Verification Plan

### Manual Verification
- Intentar reproducir cualquier canción.
- El Logcat debe mostrar `Level 0: Trying WEB_REMIX...` seguido de `✅ Success`.
- Abrir letras y comprobar el auto-scroll.
