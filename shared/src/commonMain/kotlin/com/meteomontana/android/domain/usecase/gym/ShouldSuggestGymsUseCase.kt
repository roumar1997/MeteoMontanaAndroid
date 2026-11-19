package com.meteomontana.android.domain.usecase.gym

import com.meteomontana.android.domain.model.Current

/**
 * Plan B: en una escuela con la roca mojada se sugieren rocodromos cerca. Reutiliza el criterio que ya
 * existe (`Current.dryRock`), sin umbrales nuevos. Solo si el interruptor `gyms_enabled` esta encendido.
 */
class ShouldSuggestGymsUseCase {
    operator fun invoke(current: Current, gymsEnabled: Boolean): Boolean = gymsEnabled && !current.dryRock

    companion object {
        const val RADIUS_KM = 30.0
        const val LIMIT = 5
    }
}
