package sh.lsx.untisx.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppLanguage
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.LanguagePrefs
import sh.lsx.untisx.data.notifications.NotificationScheduler
import sh.lsx.untisx.ui.components.LanguageSettingsContent
import sh.lsx.untisx.ui.components.ThemeAccentSettingsContent
import sh.lsx.untisx.ui.components.ThemeModeSettingsContent
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appBackground
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.theme.appScreenContainerColor
import sh.lsx.untisx.ui.util.AppMotion
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled
import sh.lsx.untisx.ui.util.motionEffects
import sh.lsx.untisx.ui.util.motionSpatial
import kotlin.math.abs

private const val PAGE_COUNT = 4

@Composable
fun OnboardingScreen(
    theme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    loggedIn: Boolean,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    var notifGranted by remember { mutableStateOf(isNotifGranted(context)) }
    var language by remember { mutableStateOf(LanguagePrefs.get(context)) }
    var introVisible by remember { mutableStateOf(false) }
    val animations = LocalAnimationsEnabled.current
    val ctaEffects = motionEffects<Float>()
    val ctaSpatial = motionSpatial<IntOffset>()

    LaunchedEffect(Unit) {
        introVisible = true
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifGranted = granted
    }

    LifecycleResumeEffect(Unit) {
        notifGranted = isNotifGranted(context)
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().appBackground(),
        containerColor = appScreenContainerColor(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val pageOffset = pagerState.getOffsetDistanceInPages(page)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = pageOffset * 48.dp.toPx()
                            alpha = (1f - abs(pageOffset) * 0.4f).coerceIn(0f, 1f)
                        },
                ) {
                    when (page) {
                        0 -> WelcomePage(
                            language = language,
                            animateIn = introVisible,
                            onLanguageChange = { newLanguage ->
                                language = newLanguage
                                LanguagePrefs.save(context, newLanguage)
                            },
                        )

                        1 -> PermissionsPage(
                            notifGranted = notifGranted,
                            onRequestNotifications = {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                        )

                        2 -> LoginScreen()

                        3 -> ThemePage(
                            theme = theme,
                            onChange = onThemeChange,
                        )
                    }
                }
            }

            PageIndicator(
                pageCount = PAGE_COUNT,
                currentPage = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = Haptics.haptic {
                        scope.launch {
                            if (animations) {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            } else {
                                pagerState.scrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    }) {
                        Text(stringResource(R.string.back))
                    }
                } else {
                    Spacer(Modifier.size(0.dp))
                }

                val stepComplete = when (pagerState.currentPage) {
                    2 -> loggedIn
                    else -> true
                }

                val lastPage = pagerState.currentPage == PAGE_COUNT - 1

                Button(
                    onClick = Haptics.haptic {
                        if (lastPage) {
                            onFinish()
                        } else {
                            scope.launch {
                                if (animations) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                } else {
                                    pagerState.scrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        }
                    },
                    enabled = stepComplete,
                ) {
                    AnimatedContent(
                        targetState = lastPage,
                        transitionSpec = {
                            (fadeIn(animationSpec = ctaEffects) +
                                slideInVertically(animationSpec = ctaSpatial) { height -> height / 2 }) togetherWith
                                (fadeOut(animationSpec = ctaEffects) +
                                    slideOutVertically(animationSpec = ctaSpatial) { height -> -height / 2 })
                        },
                        label = "onboardingCta",
                    ) { last ->
                        Text(
                            if (last) stringResource(R.string.onboarding_get_started)
                            else stringResource(R.string.next),
                        )
                    }
                }
            }
        }
    }
}

private fun isNotifGranted(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            val animations = LocalAnimationsEnabled.current
            val targetWidth = if (active) 24.dp else 8.dp
            val targetColor = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }

            val width by animateDpAsState(
                targetValue = targetWidth,
                animationSpec = if (animations) AppMotion.spatial() else snap(),
                label = "indicatorWidth",
            )

            val color by animateColorAsState(
                targetValue = targetColor,
                animationSpec = if (animations) AppMotion.effects() else snap(),
                label = "indicatorColor",
            )

            Box(
                modifier = Modifier
                    .size(width = if (animations) width else targetWidth, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (animations) color else targetColor),
            )
        }
    }
}

@Composable
private fun WelcomePage(
    language: AppLanguage,
    animateIn: Boolean,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    val logoProgress by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 200f),
        label = "logoIntro",
    )
    val animations = LocalAnimationsEnabled.current
    val progress = if (animations) logoProgress else 1f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = stringResource(R.string.untisx_logo),
            modifier = Modifier
                .size(196.dp)
                .graphicsLayer {
                    scaleX = 0.85f + 0.15f * progress
                    scaleY = 0.85f + 0.15f * progress
                    alpha = progress
                },
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_welcome),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(86.dp))

        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = appCardShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_language_title),
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(Modifier.height(10.dp))

                LanguageSettingsContent(
                    language = language,
                    onChange = onLanguageChange,
                )
            }
        }
    }
}

@Composable
private fun PermissionsPage(
    notifGranted: Boolean,
    onRequestNotifications: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = appCardShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_notifications_title),
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.onboarding_notifications_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                if (notifGranted) {
                    Text(
                        text = stringResource(R.string.onboarding_notifications_granted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(onClick = Haptics.haptic(onRequestNotifications)) {
                        Text(stringResource(R.string.onboarding_notifications_allow))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ExactAlarmCard()

            Spacer(Modifier.height(12.dp))
        }

        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = appCardShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_internet_title),
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.onboarding_internet_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExactAlarmCard() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(NotificationScheduler.canScheduleExactAlarms(context)) }

    LifecycleResumeEffect(Unit) {
        granted = NotificationScheduler.canScheduleExactAlarms(context)
        onPauseOrDispose { }
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = appCardShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.onboarding_exact_alarm_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.onboarding_exact_alarm_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            if (granted) {
                Text(
                    text = stringResource(R.string.onboarding_exact_alarm_granted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(
                    onClick = Haptics.haptic {
                        NotificationScheduler.openExactAlarmSettings(context)
                    },
                ) {
                    Text(stringResource(R.string.onboarding_exact_alarm_allow))
                }
            }
        }
    }
}

@Composable
private fun ThemePage(
    theme: AppTheme,
    onChange: (AppTheme) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.onboarding_theme_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(Modifier.height(24.dp))

        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = appCardShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.settings_theme_mode),
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(Modifier.height(12.dp))

                ThemeModeSettingsContent(theme = theme, onChange = onChange)
            }
        }

        Spacer(Modifier.height(12.dp))

        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = appCardShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.settings_accent_color),
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(Modifier.height(12.dp))

                ThemeAccentSettingsContent(theme = theme, onChange = onChange)
            }
        }
    }
}
