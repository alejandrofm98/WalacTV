package com.example.walactv

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

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

internal fun categorizePlaybackError(
    error: PlaybackException?,
    isVodMode: Boolean,
    hasQualityOptions: Boolean,
    hasNextChannel: Boolean = false,
): PlaybackError {
    if (error == null) {
        return PlaybackError(
            type = PlaybackErrorType.GENERIC,
            title = "Error de reproduccion",
            message = "No se pudo reproducir el contenido",
        )
    }

    val errorMessage = error.toString().lowercase()
    val errorCode = error.errorCode

    return when {
        errorMessage.contains("no_exceeds_capabilities") ||
        errorMessage.contains("decoder failed") ||
        errorMessage.contains("dolby-vision") -> {
            PlaybackError(
                type = PlaybackErrorType.CODEC_INCOMPATIBLE,
                title = "Calidad no soportada",
                message = "Este dispositivo no soporta el formato de video",
            )
        }

        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
        errorMessage.contains("network") ||
        errorMessage.contains("connection") ||
        errorMessage.contains("socket") -> {
            PlaybackError(
                type = PlaybackErrorType.NETWORK,
                title = "Sin conexion",
                message = "Verifica tu conexion a internet",
            )
        }

        errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
        errorMessage.contains("404") ||
        errorMessage.contains("403") ||
        errorMessage.contains("stream not found") ||
        errorMessage.contains("not found") -> {
            PlaybackError(
                type = PlaybackErrorType.STREAM_UNAVAILABLE,
                title = "Contenido no disponible",
                message = "El stream no esta disponible actualmente",
            )
        }

        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
        errorMessage.contains("timeout") ||
        errorMessage.contains("buffering") -> {
            PlaybackError(
                type = PlaybackErrorType.TIMEOUT,
                title = "Tiempo de espera agotado",
                message = "El servidor esta tardando en responder",
            )
        }

        errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
        errorMessage.contains("drm") -> {
            PlaybackError(
                type = PlaybackErrorType.DRM,
                title = "Error de contenido protegido",
                message = "El contenido requiere DRM no disponible",
            )
        }

        else -> {
            PlaybackError(
                type = PlaybackErrorType.GENERIC,
                title = "Error de reproduccion",
                message = "Ocurrio un error al reproducir el contenido",
            )
        }
    }
}
