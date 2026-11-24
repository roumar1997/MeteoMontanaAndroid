package com.meteomontana.android.ui.screens.schools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.R
import com.meteomontana.android.domain.usecase.forecast.RockDryOutlook
import com.meteomontana.android.ui.components.SoftPill
import com.meteomontana.android.ui.components.okColor
import com.meteomontana.android.ui.components.rainBlue

/**
 * Pastillas compactas de seca/mojada de la lista de escuelas, con la previsión de `shared` ([RockDryOutlook]):
 * «Mojada · seca en ~6 h», «Seca hasta el viernes» + «Empeora sábado». Mientras llega la previsión, la de siempre.
 */
@Composable
internal fun SchoolDryPill(schoolId: String, dry: Boolean) {
    val vm: RockDryOutlookViewModel = hiltViewModel()
    val outlook by produceState(vm.cached(schoolId), schoolId) { value = vm.outlook(schoolId) }
    val o = outlook
    val blue = rainBlue()
    val green = okColor()
    if (o == null) {
        val c = if (dry) green else blue
        SoftPill(stringResource(if (dry) R.string.schools_card_dry_rock else R.string.schools_card_wet_rock), c, c.copy(alpha = 0.14f))
        return
    }
    val (title, _) = rockDryTexts(o)
    val c = if (o.wet) blue else green
    SoftPill(title, c, c.copy(alpha = 0.14f))
    o.worsensOnDate?.takeIf { !o.wet }?.let { SoftPill(stringResource(R.string.dry_chip_worsens, dayName(it)), blue, blue.copy(alpha = 0.14f)) }
}
