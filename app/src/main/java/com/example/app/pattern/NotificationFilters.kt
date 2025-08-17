package com.example.app.pattern

// Filtro que encapsula a lógica original de `shouldDisplayNotification`.
// Ele decide se a notificação deve ser redesenhada (nova ou com risco diferente).
class ShouldUpdateDisplayFilter : Filter<NotificationContext> {
    private fun getRiskPriority(riskLevel: String): Int {
        return when (riskLevel.lowercase()) {
            "high" -> 3
            "medium" -> 2
            "low" -> 1
            else -> 0
        }
    }

    override fun isMet(context: NotificationContext): Boolean {
        val existingNotif = context.existingNotificationForId
        // Se não há notificação existente para este ID, sempre exiba a nova.
        if (existingNotif == null) {
            return true
        }

        val newRiskLevel = context.newNotification.driver_data?.risk_level ?: "low"
        val existingRiskLevel = existingNotif.driver_data?.risk_level ?: "low"

        val newRiskPriority = getRiskPriority(newRiskLevel)
        val existingRiskPriority = getRiskPriority(existingRiskLevel)

        // Regra: Se o nível de risco mudou (para cima ou para baixo), atualize.
        return newRiskPriority != existingRiskPriority
    }
}

// Filtro que decide se a notificação deve ser apenas "refrescada".
// É a lógica oposta da de cima.
class ShouldRefreshOnlyFilter : Filter<NotificationContext> {
    override fun isMet(context: NotificationContext): Boolean {
        // Só refresca se houver uma notificação existente E a lógica de update não for atendida.
        return context.existingNotificationForId != null && !ShouldUpdateDisplayFilter().isMet(context)
    }
}


// Filtro genérico para verificar o nível de risco.
class RiskLevelFilter(private val requiredLevel: Int) : Filter<NotificationContext> {
    override fun isMet(context: NotificationContext): Boolean {
        return context.intensity == requiredLevel
    }
}

// Filtros de Preferências (permanecem os mesmos)
class LowRiskVisualNotifEnabledFilter : Filter<NotificationContext> {
    override fun isMet(context: NotificationContext): Boolean {
        return context.prefs.getBoolean("notif_visual-baixo", true)
    }
}
class MidRiskVisualNotifEnabledFilter : Filter<NotificationContext> {
    override fun isMet(context: NotificationContext): Boolean {
        return context.prefs.getBoolean("notif_visual-medio", true)
    }
}
class HighRiskVisualNotifEnabledFilter : Filter<NotificationContext> {
    override fun isMet(context: NotificationContext): Boolean {
        return context.prefs.getBoolean("notif_visual-alto", true)
    }
}
class LowRiskSoundNotifEnabledFilter : Filter<NotificationContext> {
    override fun isMet(context: NotificationContext): Boolean {
        return context.prefs.getBoolean("notif_sonora-baixo", true)
    }
}
class MidRiskSoundNotifEnabledFilter : Filter<NotificationContext> {
    override fun isMet(context: NotificationContext): Boolean {
        return context.prefs.getBoolean("notif_sonora-medio", true)
    }
}
class HighRiskSoundNotifEnabledFilter : Filter<NotificationContext> {
    override fun isMet(context: NotificationContext): Boolean {
        return context.prefs.getBoolean("notif_sonora-alto", true)
    }
}