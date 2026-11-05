package com.meteomontana.android.ui.assistant

import com.meteomontana.android.presentation.assistant.AssistantPresenter
import com.meteomontana.android.presentation.assistant.*

/**
 * Sugerencias para seguir la conversación debajo de un mensaje de Android: traduce el mensaje a «qué enseñó» y deja
 * que el presentador compartido (shared) decida las frases. Nada si el mensaje espera algo del usuario.
 */
fun AssistantPresenter.followUps(m: AssistantMessage): List<AssistantPresenter.FollowUp> {
    val waiting = m.isUser || m.retryText != null || m.options.isNotEmpty() ||
        m.pendingActions.any { it.state == PendingAction.State.WAITING }
    val shown = when {
        waiting -> AssistantPresenter.Shown.NOTHING
        m.breakdown != null -> AssistantPresenter.Shown.BREAKDOWN
        m.recommendation != null -> AssistantPresenter.Shown.RECOMMENDATION
        m.weather != null -> AssistantPresenter.Shown.WEATHER
        m.hits.isNotEmpty() -> AssistantPresenter.Shown.HITS
        m.journalEntries.isNotEmpty() -> AssistantPresenter.Shown.JOURNAL
        else -> AssistantPresenter.Shown.NOTHING
    }
    return followUps(shown, m.breakdown)
}
