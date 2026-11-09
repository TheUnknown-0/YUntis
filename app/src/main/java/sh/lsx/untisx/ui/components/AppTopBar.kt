package sh.lsx.untisx.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.ui.util.motionEffects
import sh.lsx.untisx.ui.util.tapHaptic

@Composable
internal fun AppTopBar(
    title: String,
    isTimetable: Boolean,
    onMenuToggle: () -> Unit,
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
    titleHighlight: Boolean = false,
) {
    val effectsSpec = motionEffects<Float>()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = isTimetable,
            transitionSpec = {
                fadeIn(animationSpec = effectsSpec) togetherWith
                    fadeOut(animationSpec = effectsSpec)
            },
            label = "topBarLeading",
        ) { timetable ->
            if (timetable) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = stringResource(R.string.menu),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .tapHaptic(onMenuToggle)
                        .padding(8.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .tapHaptic(onBack)
                        .padding(8.dp),
                )
            }
        }

        AnimatedContent(
            targetState = title,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                fadeIn(animationSpec = effectsSpec) togetherWith
                    fadeOut(animationSpec = effectsSpec)
            },
            label = "topBarTitle",
        ) { text ->
            Text(
                text = if (onTitleClick != null) "$text ▾" else text,
                style = MaterialTheme.typography.titleLarge,
                color = if (titleHighlight) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onTitleClick != null) {
                            Modifier.clip(RoundedCornerShape(12.dp)).tapHaptic(onTitleClick).padding(vertical = 4.dp)
                        } else {
                            Modifier
                        },
                    ),
                textAlign = TextAlign.Center,
            )
        }

        AnimatedContent(
            targetState = isTimetable,
            transitionSpec = {
                fadeIn(animationSpec = effectsSpec) togetherWith
                    fadeOut(animationSpec = effectsSpec)
            },
            label = "topBarTrailing",
        ) { timetable ->
            if (timetable) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = stringResource(R.string.profile),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .tapHaptic(onProfile)
                        .padding(8.dp),
                )
            } else {
                Spacer(Modifier.size(40.dp))
            }
        }
    }
}
