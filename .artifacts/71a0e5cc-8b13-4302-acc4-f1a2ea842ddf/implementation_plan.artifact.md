# Plan de Implementación - Corrección de Error 2004 (Sincronización de Cabeceras)

El error `2004` indica que el servidor (YouTube o Archive.org) rechazó la conexión. Vamos a sincronizar exactamente las cabeceras de resolución con las de reproducción.

## User Review Required

> [!WARNING]
> YouTube ha empezado a validar que las cabeceras `X-YouTube-Client-Name` y `X-YouTube-Client-Version` estén presentes incluso en las peticiones de los fragmentos de video/audio (`googlevideo.com`). Las añadiremos dinámicamente.

## Proposed Changes

### [Network / Resolver]

#### [MODIFY] [InnertubeResolver.kt](file:///D:/Android/AndroidStudioProjects/Auto_Music/app/src/main/java/com/danielsalas/auto_music/player/InnertubeResolver.kt)
- Expandir `ResolvedStream` para incluir un `Map<String, String>` de cabeceras adicionales.
- Poblar estas cabeceras basándose en el cliente de YouTube utilizado (VR, TV, etc.).

### [Player]

#### [MODIFY] [MusicService.kt](file:///D:/Android/AndroidStudioProjects/Auto_Music/app/src/main/java/com/danielsalas/auto_music/player/MusicService.kt)
- Actualizar la lógica de `ResolvingDataSource` para inyectar todas las cabeceras proporcionadas por el `ResolvedStream`.
- Eliminar cabeceras globales de `DefaultHttpDataSource` que puedan causar conflictos (como un `User-Agent` genérico de Chrome cuando el stream es de VR).

## Verification Plan

### Manual Verification
- Reproducir "With or Without You".
- Abrir el "Network Inspector" de Android Studio si es necesario para confirmar que las peticiones a `googlevideo.com` llevan las cabeceras del cliente VR/TV.
- Confirmar que el flujo pasa a Archive.org si YouTube devuelve un 403.
