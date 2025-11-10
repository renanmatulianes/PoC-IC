package com.example.app.rules.filters

import com.example.app.ZonaTipo
import com.example.app.rules.context.NotificationContext

/**
 * Filtro que verifica se uma notificação TIM contém texto
 * que corresponde a um tipo de zona específico.
 */
class ZoneTypeFilter(private val targetZoneType: ZonaTipo) : Filter {

    override fun isMet(context: NotificationContext): Boolean {
        // Se não houver notificação TIM no contexto, a condição não é atendida.
        val advisoryText = context.timNotification?.dataFrames?.firstOrNull()?.content?.advisoryText
            ?: return false

        return when (targetZoneType) {
            ZonaTipo.CRIANCA -> advisoryText.contains("crianças", ignoreCase = true) ||
                    advisoryText.contains("escolar", ignoreCase = true)
            ZonaTipo.CICLISTA -> advisoryText.contains("ciclista", ignoreCase = true) ||
                    advisoryText.contains("ciclistas", ignoreCase = true)
        }
    }
}