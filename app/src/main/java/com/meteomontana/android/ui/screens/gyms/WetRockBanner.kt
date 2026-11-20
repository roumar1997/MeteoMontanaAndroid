package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.ui.components.rainBlue

/** Item de la lista de Escuelas: sale solo si la mayoría de las escuelas del filtro de distancia están mojadas hoy. */
fun androidx.compose.foundation.lazy.LazyListScope.wetRockBannerItem(dryFlags: List<Boolean?>, onClick: () -> Unit) {
    if (!com.meteomontana.android.domain.usecase.gym.WetRockAdvisor.isMostlyWet(dryFlags)) return
    item(key = "wet-banner") { WetRockBanner(onClick, Modifier.padding(horizontal = 12.dp).padding(top = 8.dp)) }
}

/** «Hoy la roca está mojada cerca de ti · ¿Rocódromo?»: al tocarlo, Escuelas pasa a la capa de rocódromos. */
@Composable
fun WetRockBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val rain = rainBlue()
    Row(
        modifier.fillMaxWidth().clip(shape).background(rain.copy(alpha = 0.12f), shape).border(1.dp, rain.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.WaterDrop, null, tint = rain, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.gym_wet_banner), modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Icon(Icons.Outlined.KeyboardArrowRight, null, tint = rain)
    }
}
