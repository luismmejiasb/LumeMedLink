package com.luismejias.lumemedlink.features.auth.flow

/**
 * The sign-in flow's copy, in Spanish, taken from LumeMed's flow so the two apps of the family speak the
 * same way. Resolved here and handed to the kit as ready strings (§5: the app translates; the kit never
 * does). It moves to the app's localization layer when that exists; until then this is the one place.
 *
 * Where LumeMed says "Face ID", this app says "biometría": it runs on Android as well, and a screen must
 * not know its platform (§4).
 */
internal object AuthCopy {
    const val APP_NAME = "LumeMedLink"
    const val BACK = "Volver"
    const val RUT_LABEL = "RUT"
    const val RUT_PLACEHOLDER = "Ingresa tu RUT"
    const val RUT_INVALID = "RUT inválido"
    const val PASSWORD_LABEL = "Contraseña"
    const val PASSWORD_PLACEHOLDER = "Tu contraseña"
    const val PASSWORD_SHOW = "Mostrar contraseña"
    const val PASSWORD_HIDE = "Ocultar contraseña"
    const val FORGOT_PASSWORD = "¿Olvidaste tu contraseña?"
    const val SIGN_IN = "Ingresar"
    const val SIGNING_IN = "Ingresando…"
    const val WRONG_CREDENTIALS = "RUT o contraseña incorrectos"

    const val MFA_TITLE = "Verifica tu identidad"
    const val MFA_BODY = "Ingresa el código de 6 dígitos de tu aplicación de autenticación."
    const val CODE_LABEL = "Código de verificación"
    const val CODE_CLEAR = "Borrar código"
    const val VERIFY = "Verificar"
    const val VERIFYING = "Verificando…"
    const val WRONG_CODE = "Código incorrecto"

    const val TOTP_TITLE = "Vincula tu autenticador"
    const val TOTP_BODY =
        "Escanea el código QR con tu aplicación de autenticación, o escribe la clave de configuración."
    const val TOTP_QR = "Código QR para vincular"
    const val TOTP_KEY = "Clave de configuración"
    const val TOTP_KEY_FOOTNOTE = "Escríbela en tu aplicación si no puede escanear el código."
    const val TOTP_REPLACES = "Si ya tenías un autenticador vinculado, este lo reemplaza."
    const val TOTP_LOADING = "Preparando tu código…"
    const val CONTINUE = "Continuar"
    const val TOTP_CONFIRM_TITLE = "Confirma la vinculación"
    const val TOTP_CONFIRM_BODY = "Ingresa el código de 6 dígitos que muestra tu aplicación de autenticación."
    const val CONFIRM = "Confirmar"
    const val CONFIRMING = "Confirmando…"

    const val BIOMETRIC_TITLE = "Activa la biometría"
    const val BIOMETRIC_BODY = "Usarás tu huella o tu rostro para volver a entrar a LumeMedLink."
    const val BIOMETRIC_PERSONAL =
        "Este teléfono debe ser sólo tuyo: cualquier huella o rostro registrado en él podrá abrir tu sesión."
    const val BIOMETRIC_ACTIVATE = "Activar biometría"
    const val BIOMETRIC_ACTIVATING = "Activando…"
    const val BIOMETRIC_UNAVAILABLE_TITLE = "La biometría no está disponible"
    const val BIOMETRIC_UNAVAILABLE_MESSAGE =
        "LumeMedLink necesita una huella o tu rostro registrados en este teléfono. Regístralos en los ajustes " +
            "del teléfono y vuelve a ingresar."
    const val SESSION_NOT_SAVED = "No pudimos guardar tu sesión en este teléfono. Vuelve a ingresar."

    const val RECOVERY_TITLE = "¿Olvidaste tu contraseña?"
    const val RECOVERY_BODY = "Ingresa tu RUT para recibir un código y crear una contraseña nueva."
    const val SEND_CODE = "Enviar código"
    const val SENDING = "Enviando…"
    const val RESET_CODE_TITLE = "Revisa tu correo"
    const val RESET_CODE_BODY = "Ingresa el código de 6 dígitos que te enviamos."
    const val NEW_PASSWORD_TITLE = "Crea una contraseña nueva"
    const val NEW_PASSWORD_BODY = "La usarás la próxima vez que ingreses."
    const val NEW_PASSWORD_LABEL = "Contraseña nueva"
    const val NEW_PASSWORD_REPEAT = "Repite la contraseña"
    const val PASSWORDS_DIFFER = "Las contraseñas no coinciden"
    const val SAVE_PASSWORD = "Guardar contraseña"
    const val SAVING = "Guardando…"
    const val PASSWORD_SAVED = "Contraseña actualizada. Ingresa con tu contraseña nueva."

    const val UNLOCK_TITLE = "Hola de nuevo"
    const val UNLOCK_BODY = "Usa tu huella o tu rostro para volver a entrar."
    const val UNLOCK_ACTION = "Ingresar"
    const val UNLOCK_USE_PASSWORD = "Usar contraseña"
    const val UNLOCK_NOT_YOU = "¿No eres tú?"
    const val UNLOCK_CHANGE_ACCOUNT = "Cambiar de cuenta"
    const val UNLOCK_TRY_AGAIN = "Inténtalo de nuevo."

    const val UNAVAILABLE_TITLE = "No pudimos conectar"
    const val UNAVAILABLE_MESSAGE = "Revisa tu conexión e inténtalo de nuevo."
    const val RATE_LIMITED_TITLE = "Demasiados intentos"
    const val RATE_LIMITED_MESSAGE = "Espera unos minutos antes de volver a intentarlo."
    const val RETRY = "Reintentar"
    const val CLOSE = "Cerrar"
    const val FAILED_SHORT = "No se pudo"
    const val DONE = "Listo"

    fun attemptsLeft(remaining: Int): String =
        if (remaining == 1) "Te queda 1 intento." else "Te quedan $remaining intentos."
}
