package dev.cramsoft.sallexplore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Azul = Color(0xFF0033A0)

@Composable
fun ChatTikTokOverlay(
    personajeId   : String,
    personajeInfo : PersonajeInfo?,
    onCerrar      : () -> Unit,
    vm            : ChatViewModel = viewModel()
) {
    val mensajes by vm.mensajes.collectAsState()
    val enviando by vm.enviando.collectAsState()
    var input    by remember { mutableStateOf("") }
    val listado   = rememberLazyListState()

    LaunchedEffect(mensajes.size) {
        if (mensajes.isNotEmpty()) listado.animateScrollToItem(mensajes.size - 1)
    }

    // El Box ocupa toda la pantalla y aplica imePadding al nivel raíz
    // para que el teclado empuje TODO el contenido exactamente una vez
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()   // ← aquí y solo aquí
    ) {

        // ── Gradiente fondo ────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.60f)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )

        // ── Info personaje en chat (parte superior) ────────
        if (personajeInfo != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.90f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = personajeInfo.nombre,
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 13.sp
                        )
                        Text(
                            text     = personajeInfo.descripcionCorta,
                            color    = Color.White.copy(alpha = 0.70f),
                            fontSize = 10.sp
                        )
                    }
                    // Botón cerrar
                    IconButton(
                        onClick  = onCerrar,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
            }
        }

        // ── Lista de mensajes ──────────────────────────────
        LazyColumn(
            state   = listado,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.90f)
                .heightIn(max = 360.dp)
                .padding(start = 12.dp, bottom = 68.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(mensajes, key = { it.id }) { msg ->
                BurbujaMensaje(msg = msg, nombrePersonaje = personajeInfo?.nombre ?: personajeId)
            }
        }

        // ── Barra de input pegada al fondo ─────────────────
        // Sin imePadding aquí — ya está en el Box padre
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.80f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value         = input,
                onValueChange = { input = it },
                placeholder   = { Text("Pregunta algo…", color = Color.Gray, fontSize = 14.sp) },
                modifier      = Modifier.weight(1f),
                singleLine    = true,
                shape         = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = Color.White.copy(alpha = 0.15f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
                    focusedTextColor        = Color.White,
                    unfocusedTextColor      = Color.White,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor             = Azul
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick  = {
                    if (input.isNotBlank() && !enviando) {
                        vm.enviar(personajeId, input.trim())
                        input = ""
                    }
                },
                enabled  = !enviando && input.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .background(if (enviando) Color.Gray else Azul, CircleShape)
            ) {
                if (enviando) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun BurbujaMensaje(msg: MensajeChat, nombrePersonaje: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.esUsuario) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (msg.esUsuario) Azul.copy(alpha = 0.88f)
                    else Color.Black.copy(alpha = 0.68f),
                    shape = RoundedCornerShape(
                        topStart    = 16.dp, topEnd     = 16.dp,
                        bottomStart = if (msg.esUsuario) 16.dp else 4.dp,
                        bottomEnd   = if (msg.esUsuario) 4.dp  else 16.dp
                    )
                )
                .widthIn(max = 280.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text     = if (msg.esUsuario) "Tú" else nombrePersonaje,
                    fontSize = 10.sp,
                    color    = if (msg.esUsuario) Color.White.copy(alpha = 0.70f)
                    else Azul.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(3.dp))
                if (msg.enCarga) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(12.dp),
                            color       = Color.White,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Escribiendo…", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                } else {
                    Text(msg.texto, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}