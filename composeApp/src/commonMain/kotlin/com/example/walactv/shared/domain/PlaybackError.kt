package com.example.walactv.shared.domain

enum class PlaybackErrorType {
    NETWORK,
    CODEC_INCOMPATIBLE,
    STREAM_UNAVAILABLE,
    TIMEOUT,
    DRM,
    GENERIC,
}

data class PlaybackError(
    val type: PlaybackErrorType,
    val title: String,
    val message: String,
)

fun categorizePlaybackError(
    errorType: PlaybackErrorType,
    errorMessage: String,
): PlaybackError {
    return when (errorType) {
        PlaybackErrorType.CODEC_INCOMPATIBLE -> PlaybackError(
            type = PlaybackErrorType.CODEC_INCOMPATIBLE,
            title = "Calidad no soportada",
            message = "Este dispositivo no soporta el formato de video",
        )
        PlaybackErrorType.NETWORK -> PlaybackError(
            type = PlaybackErrorType.NETWORK,
            title = "Sin conexion",
            message = "Verifica tu conexion a internet",
        )
        PlaybackErrorType.STREAM_UNAVAILABLE -> PlaybackError(
            type = PlaybackErrorType.STREAM_UNAVAILABLE,
            title = "Contenido no disponible",
            message = "El stream no esta disponible actualmente",
        )
        PlaybackErrorType.TIMEOUT -> PlaybackError(
            type = PlaybackErrorType.TIMEOUT,
            title = "Tiempo de espera agotado",
            message = "El servidor esta tardando en responder",
        )
        PlaybackErrorType.DRM -> PlaybackError(
            type = PlaybackErrorType.DRM,
            title = "Error de contenido protegido",
            message = "El contenido requiere DRM no disponible",
        )
        PlaybackErrorType.GENERIC -> PlaybackError(
            type = PlaybackErrorType.GENERIC,
            title = "Error de reproduccion",
            message = errorMessage.ifBlank { "Ocurrio un error al reproducir el contenido" },
        )
    }
}
