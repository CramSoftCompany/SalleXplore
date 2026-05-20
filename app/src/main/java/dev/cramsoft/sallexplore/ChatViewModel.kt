package dev.cramsoft.sallexplore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class MensajeChat(
    val id        : String  = UUID.randomUUID().toString(),
    val texto     : String,
    val esUsuario : Boolean,
    val enCarga   : Boolean = false
)

class ChatViewModel : ViewModel() {

    val sessionId = UUID.randomUUID().toString()

    private val repository = ChatRepository()

    private val _mensajes  = MutableStateFlow<List<MensajeChat>>(emptyList())
    val  mensajes: StateFlow<List<MensajeChat>> = _mensajes.asStateFlow()

    private val _enviando  = MutableStateFlow(false)
    val  enviando: StateFlow<Boolean> = _enviando.asStateFlow()

    fun enviar(personaje: String, texto: String) {
        if (texto.isBlank() || _enviando.value) return

        viewModelScope.launch {
            _enviando.value = true

            // 1. Mensaje del usuario
            agregar(MensajeChat(texto = texto, esUsuario = true))

            // 2. Placeholder del personaje (streaming)
            val idAI = UUID.randomUUID().toString()
            agregar(MensajeChat(id = idAI, texto = "", esUsuario = false, enCarga = true))

            var acumulado = ""

            repository.chatStream(personaje, sessionId, texto)
                .catch { e -> actualizar(idAI, "⚠ ${e.message}", enCarga = false) }
                .collect { evento ->
                    when (evento) {
                        is ChatEvento.Token -> {
                            acumulado += evento.texto
                            actualizar(idAI, acumulado, enCarga = false)
                        }
                        is ChatEvento.Hecho  -> { /* stream terminó */ }
                        is ChatEvento.Error  -> actualizar(idAI, "⚠ ${evento.mensaje}", enCarga = false)
                    }
                }

            _enviando.value = false
        }
    }

    private fun agregar(msg: MensajeChat) {
        _mensajes.value = _mensajes.value + msg
    }

    private fun actualizar(id: String, texto: String, enCarga: Boolean) {
        _mensajes.value = _mensajes.value.map {
            if (it.id == id) it.copy(texto = texto, enCarga = enCarga) else it
        }
    }
}