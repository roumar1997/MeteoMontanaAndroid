package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Ok
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.terraFillColor

// Piezas redondeadas del perfil (propio y ajeno) y de Estadisticas: iconos en cuadradito tintado, como los nuevos Ajustes.

private val Purple = Color(0xFF7B3FB0)

/** Cuadradito redondeado con un icono del color dado, sobre su propio tinte. */
@Composable
internal fun TintedIcon(icon: ImageVector, tint: Color, size: Int = 36) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size / 2 + 2).dp))
    }
}

/** Fila redondeada con icono de color, etiqueta, cuenta opcional y flecha (Escuelas, Proyectos... de Estadisticas). */
@Composable
internal fun ProfileLinkRow(icon: ImageVector, tint: Color, label: String, count: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TintedIcon(icon, tint)
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        count?.let {
            Text("$it", fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = tint,
                modifier = Modifier.clip(CircleShape).background(tint.copy(alpha = 0.13f)).padding(horizontal = 10.dp, vertical = 2.dp))
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp)
    }
}

@Composable
private fun MiniTile(icon: ImageVector, tint: Color, label: String, count: Int?, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surface, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        TintedIcon(icon, tint, 30)
        Text(count?.toString() ?: "›", fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, color = MaterialTheme.colorScheme.onBackground)
        Text(label.uppercase(), fontFamily = Mono, fontSize = 8.sp, letterSpacing = 0.8.sp, maxLines = 1, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Tres fichas pequenas bajo los numeros: Publicaciones, Escuelas y Proyectos (con su cuenta). Publicaciones no tiene
 * cuenta barata en el servidor, asi que lleva solo la flecha ([posts] null). Proyectos solo cuando [onProjects] no es null
 * (el de otra persona no lo enseña).
 */
@Composable
internal fun ProfileMiniTiles(
    posts: Int?, onPosts: () -> Unit, schools: Int, onSchools: () -> Unit,
    projects: Int, onProjects: (() -> Unit)?, modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MiniTile(Icons.Outlined.Newspaper, Purple, stringResource(R.string.feed_posts_section), posts, onPosts, Modifier.weight(1f))
        MiniTile(Icons.Outlined.Landscape, Ok, stringResource(R.string.profile_schools), schools, onSchools, Modifier.weight(1f))
        if (onProjects != null) MiniTile(Icons.Outlined.Flag, terraFillColor(), stringResource(R.string.profile_projects), projects, onProjects, Modifier.weight(1f))
    }
}

/** Boton pequeño naranja «+ Entreno» que cabe en la fila de abajo del perfil. */
@Composable
internal fun SmallAddWorkoutPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(terraFillColor()).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.profile_add_workout_short), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
    }
}

