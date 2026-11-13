package com.meteomontana.android.ui.screens.notifications

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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Notification
import com.meteomontana.android.domain.usecase.notifications.NotificationGroup
import com.meteomontana.android.ui.components.userRing
import com.meteomontana.android.ui.theme.EyebrowTextStyle

/** Estado del botón de seguir de un aviso de «nuevo seguidor». */
enum class FollowUi { NONE, FOLLOWING, REQUESTED }

// Color del iconito de la esquina según el tipo (mismos valores que NotificationsView.swift).
private val CornerBlue = Color(0xFF3B7DD8)
private val CornerRed = Color(0xFFD64545)
private val CornerGreen = Color(0xFF4F7A3A)
private val CornerGold = Color(0xFFC9A24A)

private fun cornerIcon(type: String): Pair<ImageVector, Color> = when (type) {
    "NEW_FOLLOWER", "FOLLOW_REQUEST", "FOLLOW_ACCEPTED" -> Icons.Filled.PersonAdd to CornerBlue
    "FEED_LIKE", "FEED_COMMENT_LIKE" -> Icons.Filled.Favorite to CornerRed
    "FEED_COMMENT" -> Icons.Filled.ChatBubble to CornerGreen
    "FEED_MENTION" -> Icons.Filled.AlternateEmail to CornerGreen
    "MEETUP_NEW" -> Icons.Filled.Groups to CornerGold
    else -> Icons.Filled.Notifications to CornerGold
}

/** «**usuario** acción · hace X». Los avisos antiguos (sin actor) siguen con título y cuerpo. */
@Composable
internal fun notificationText(g: NotificationGroup): AnnotatedString {
    val n = g.lead
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    val muted = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val age = notificationDateLabel(n.createdAt)
    val who = n.actorName
    val subject = n.subject?.takeIf { it.isNotBlank() }
    val many = g.others > 0
    return buildAnnotatedString {
        if (n.type == "TEAM_MESSAGE" || who == null) {
            withStyle(bold) { append(n.title) }
            n.body?.takeIf { it.isNotBlank() }?.let { append(" · "); append(it) }
        } else {
            withStyle(bold) { append(who) }
            if (many) {
                append(" ${stringResource(R.string.notif_and)} ")
                withStyle(bold) { append(stringResource(R.string.notif_n_more, g.others)) }
            }
            append(" ")
            append(
                when (n.type) {
                    "NEW_FOLLOWER" -> stringResource(R.string.notif_act_follow)
                    "FOLLOW_REQUEST" -> stringResource(R.string.notif_act_request)
                    "FOLLOW_ACCEPTED" -> stringResource(R.string.notif_act_accepted)
                    "FEED_LIKE" -> when {
                        many && subject != null -> stringResource(R.string.notif_act_like_many_of, subject)
                        many -> stringResource(R.string.notif_act_like_many)
                        subject != null -> stringResource(R.string.notif_act_like_of, subject)
                        else -> stringResource(R.string.notif_act_like)
                    }
                    "FEED_COMMENT" ->
                        if (subject != null) stringResource(R.string.notif_act_comment_of, subject)
                        else stringResource(R.string.notif_act_comment)
                    "FEED_COMMENT_LIKE" ->
                        if (many) stringResource(R.string.notif_act_comment_like_many)
                        else stringResource(R.string.notif_act_comment_like)
                    "FEED_MENTION" -> stringResource(R.string.notif_act_mention)
                    "MEETUP_NEW" ->
                        if (subject != null) stringResource(R.string.notif_act_meetup_of, subject)
                        else stringResource(R.string.notif_act_meetup)
                    else -> n.body.orEmpty()
                }
            )
        }
        withStyle(muted) { append(" · $age") }
    }
}

@Composable
internal fun NotificationRow(
    g: NotificationGroup,
    follow: FollowUi,
    onToggleFollow: () -> Unit,
    onClick: () -> Unit
) {
    val n = g.lead
    val unread = g.unread
    val bg = if (unread) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.background
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NotificationAvatar(n)
        Text(
            notificationText(g),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        NotificationTrailing(n, follow, onClick, onToggleFollow)
    }
}

/** Foto redonda real (con la aureola de su título) o la inicial; iconito del tipo en la esquina. */
@Composable
private fun NotificationAvatar(n: Notification) {
    val size = 44.dp
    Box(Modifier.size(size + 4.dp)) {
        val shape = CircleShape
        when {
            n.type == "TEAM_MESSAGE" -> androidx.compose.foundation.Image(
                painterResource(R.drawable.logo_cumbre), contentDescription = null,
                modifier = Modifier.align(Alignment.CenterStart).size(size).clip(shape)
            )
            n.actorPhotoUrl != null -> AsyncImage(
                model = n.actorPhotoUrl, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterStart)
                    .userRing(n.actorUid, size).size(size).clip(shape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            )
            else -> Box(
                Modifier.align(Alignment.CenterStart).size(size).clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val initial = (n.actorName ?: n.title).trim().firstOrNull()?.uppercase().orEmpty()
                Text(initial, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (n.type != "TEAM_MESSAGE") {
            val (icon, tint) = cornerIcon(n.type)
            Box(
                Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape)
                    .background(tint)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(11.dp)) }
        }
    }
}

@Composable
private fun NotificationTrailing(n: Notification, follow: FollowUi, onOpen: () -> Unit, onToggleFollow: () -> Unit) {
    val terra = MaterialTheme.colorScheme.primary
    when {
        n.type == "NEW_FOLLOWER" && n.actorUid != null -> {
            val following = follow != FollowUi.NONE
            Text(
                when (follow) {
                    FollowUi.NONE -> stringResource(R.string.profile_follow)
                    FollowUi.FOLLOWING -> stringResource(R.string.profile_following)
                    FollowUi.REQUESTED -> stringResource(R.string.profile_requested)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (following) MaterialTheme.colorScheme.onSurface else Color.White,
                maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (following) MaterialTheme.colorScheme.surfaceVariant else terra)
                    .clickable(onClick = onToggleFollow)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        n.type == "MEETUP_NEW" -> Text(
            stringResource(R.string.notif_view),
            style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(terra)
                .clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp)
        )
        n.thumbUrl != null -> AsyncImage(
            model = n.thumbUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen)
        )
        n.type == "TEAM_MESSAGE" || n.type == "FOLLOW_REQUEST" || n.type == "FOLLOW_ACCEPTED" ->
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Cabecera de tramo: HOY / ESTA SEMANA / ANTES. */
@Composable
internal fun NotificationSectionHeader(label: String) {
    Text(
        label, style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

internal fun sectionLabelRes(s: com.meteomontana.android.domain.usecase.notifications.NotificationSection): Int = when (s) {
    com.meteomontana.android.domain.usecase.notifications.NotificationSection.TODAY -> R.string.notif_section_today
    com.meteomontana.android.domain.usecase.notifications.NotificationSection.THIS_WEEK -> R.string.notif_section_week
    com.meteomontana.android.domain.usecase.notifications.NotificationSection.EARLIER -> R.string.notif_section_earlier
}
