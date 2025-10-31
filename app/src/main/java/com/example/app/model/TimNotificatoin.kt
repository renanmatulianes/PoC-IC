package com.example.app.model

import com.squareup.moshi.Json

// Estrutura principal da mensagem TIM (continua igual)
data class TimNotification(
    @Json(name = "msgID") val msgID: Int,
    @Json(name = "packetID") val packetID: String,
    @Json(name = "dataFrames") val dataFrames: List<DataFrame>
)

// DataFrame agora contém um objeto 'Content'
data class DataFrame(
    @Json(name = "content") val content: Content
)

// Nova classe para representar o objeto 'content' aninhado
data class Content(
    @Json(name = "advisoryText") val advisoryText: String?
)