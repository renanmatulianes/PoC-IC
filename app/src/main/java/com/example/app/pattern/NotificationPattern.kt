package com.example.app.pattern

import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import com.example.app.Direction
import com.example.app.MainActivity
import com.example.app.Objects
import com.example.app.model.Notification

// O "sangue" do sistema. Passa todos os dados e dependências necessários.
data class NotificationContext(
    // Dados do evento atual
    val newNotification: Notification,
    val direction: Direction,
    val intensity: Int,
    val objectType: Objects,
    val objectId: String,

    // Estado anterior para o MESMO objeto
    val existingNotificationForId: Notification?,

    // Passamos a MainActivity para que os Effects possam modificar seu estado público ('internal')
    val mainActivity: MainActivity,

    // Dependências do Android
    val prefs: SharedPreferences,
    val handler: Handler,

    // Gerenciadores de animação (não de estado)
    val pulseAnimators: MutableMap<Direction, ValueAnimator>,
    val arrowAnimators: MutableMap<Direction, ValueAnimator>
)

// --- Interfaces (Contratos do Padrão) ---
interface Filter<T> {
    fun isMet(context: T): Boolean
}

interface Effect<T> {
    fun apply(context: T)
}

// --- Filtros Compostos ---
class AndFilter<T>(private val filters: List<Filter<T>>) : Filter<T> {
    override fun isMet(context: T): Boolean {
        return filters.all { it.isMet(context) }
    }
}

// --- Classe de Regra e Orquestrador ---
class NotificationRule(
    val name: String,
    private val filter: Filter<NotificationContext>,
    private val effects: List<Effect<NotificationContext>>
) {
    fun process(context: NotificationContext) {
        if (filter.isMet(context)) {
            effects.forEach { it.apply(context) }
        }
    }
}

class NotificationOrchestrator {
    private val rules = mutableListOf<NotificationRule>()

    fun addRule(rule: NotificationRule) {
        rules.add(rule)
    }

    fun process(context: NotificationContext) {
        rules.forEach { it.process(context) }
    }
}