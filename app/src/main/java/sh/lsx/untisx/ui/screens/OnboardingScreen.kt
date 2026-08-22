package sh.lsx.untisx.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.AuthData
import sh.lsx.untisx.data.AuthPrefs
import sh.lsx.untisx.data.ThemeMode
import sh.lsx.untisx.data.ThemePreset
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.School

private const val PAGE_COUNT = 3

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

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifGranted = granted
    }

    LifecycleResumeEffect(Unit) {
        notifGranted = isNotifGranted(context)
        onPauseOrDispose { }
    }

    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> PermissionsPage(
                    notifGranted = notifGranted,
                    onRequestNotifications = {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )

                1 -> LoginPage()

                2 -> ThemePage(
                    theme = theme,
                    onChange = onThemeChange,
                )
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
                TextButton(onClick = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                }) {
                    Text("Back")
                }
            } else {
                Spacer(Modifier.size(0.dp))
            }

            val stepComplete = when (pagerState.currentPage) {
                0 -> notifGranted
                1 -> loggedIn
                else -> true
            }

            if (pagerState.currentPage < PAGE_COUNT - 1) {
                Button(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    enabled = stepComplete,
                ) {
                    Text("Next")
                }
            } else {
                Button(onClick = onFinish, enabled = stepComplete) {
                    Text("Get started")
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
            Box(
                modifier = Modifier
                    .size(width = if (active) 24.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
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
            text = "Welcome to UntisX",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "A better app for your timetable.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = "Notifications (optional)",
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Get notified about schedule changes and other events.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                if (notifGranted) {
                    Text(
                        text = "Granted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(onClick = onRequestNotifications) {
                        Text("Allow notifications")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = "Internet access (required)",
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "UntisX talks to your school's Untis server to load your timetable. No account is created, your credentials are only stored on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var schoolQuery by remember { mutableStateOf("") }
    var selectedSchool by remember { mutableStateOf<School?>(null) }
    var suggestions by remember { mutableStateOf<List<School>>(emptyList()) }
    var suggestionsExpanded by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var tooManyResults by remember { mutableStateOf(false) }

    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signingIn by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val searchCache = remember { mutableMapOf<String, List<School>>() }

    LaunchedEffect(schoolQuery) {
        val query = schoolQuery.trim()
        tooManyResults = false

        if (query.length < 2) {
            suggestions = emptyList()
            suggestionsExpanded = false
            return@LaunchedEffect
        }

        if (selectedSchool?.displayName == schoolQuery) {
            return@LaunchedEffect
        }

        delay(400)

        val cached = searchCache[query]
        if (!cached.isNullOrEmpty()) {
            suggestions = cached
            suggestionsExpanded = true
            return@LaunchedEffect
        }

        searching = true
        try {
            val results = UntisClient.searchSchools(query)
            searchCache[query] = results

            if (query == schoolQuery.trim()) {
                suggestions = results
                suggestionsExpanded = results.isNotEmpty()
            }
        } catch (e: UntisException.TooManyResults) {
            if (query == schoolQuery.trim()) {
                tooManyResults = true
                suggestions = emptyList()
                suggestionsExpanded = false
            }
        } catch (e: UntisException.Network) {
            if (query == schoolQuery.trim()) {
                error = e.message
                suggestionsExpanded = false
            }
        } finally {
            searching = false
        }
    }

    fun signIn() {
        val school = selectedSchool ?: return

        signingIn = true
        error = null

        scope.launch {
            try {
                val result = UntisClient.authenticate(
                    server = school.server,
                    loginName = school.loginName,
                    user = user.trim(),
                    password = password,
                )

                AuthPrefs.save(
                    context,
                    AuthData(
                        server = school.server,
                        loginName = school.loginName,
                        displayName = school.displayName,
                        user = user.trim(),
                        password = password,
                        session = result,
                    ),
                )

                signedIn = true
                error = null
            } catch (e: UntisException.Network) {
                error = e.message
            } catch (e: UntisException.BadCredentials) {
                error = e.message
            } catch (e: UntisException.Api) {
                error = "Untis error ${e.code}: ${e.message}"
            } catch (e: Exception) {
                error = "Unexpected error: ${e.message}"
            } finally {
                signingIn = false
            }
        }
    }

    val sel = selectedSchool

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Connect your Account",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )

            Text(
                text = "Your username and password are encrypted using the Android Keystore and stored only on this device. They are sent exclusively to your school's Untis server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))

        ExposedDropdownMenuBox(
            expanded = suggestionsExpanded,
            onExpandedChange = { suggestionsExpanded = it },
        ) {
            OutlinedTextField(
                value = schoolQuery,
                onValueChange = { value ->
                    schoolQuery = value
                    if (selectedSchool != null && value != selectedSchool?.displayName) {
                        selectedSchool = null
                    }
                    error = null
                },
                label = { Text("Your school") },
                singleLine = true,
                enabled = !signedIn,
                supportingText = when {
                    sel != null -> {
                        {
                            Text("${sel.server}, \"${sel.loginName}\"")
                        }
                    }

                    searching -> {
                        { Text("Searching...") }
                    }

                    tooManyResults -> {
                        { Text("Too many schools match - keep typing.") }
                    }

                    else -> null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable),
            )

            ExposedDropdownMenu(
                expanded = suggestionsExpanded && suggestions.isNotEmpty() && !signedIn,
                onDismissRequest = { suggestionsExpanded = false },
                modifier = Modifier.exposedDropdownSize(),
            ) {
                suggestions.forEach { entry ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(entry.displayName)

                                if (entry.address.isNotBlank()) {
                                    Text(
                                        text = entry.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        },
                        onClick = {
                            selectedSchool = entry
                            schoolQuery = entry.displayName
                            suggestionsExpanded = false
                            keyboard?.hide()
                        },
                    )
                }
            }
        }

        if (searching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = user,
            onValueChange = {
                user = it
                error = null
            },
            label = { Text("Username") },
            singleLine = true,
            enabled = !signedIn,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                error = null
            },
            label = { Text("Password") },
            singleLine = true,
            enabled = !signedIn,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (signedIn) {
            Text(
                text = "Signed in.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Button(
                onClick = ::signIn,
                enabled = !signingIn && sel != null && user.isNotBlank() && password.isNotBlank(),
            ) {
                Text(if (signingIn) "Signing in..." else "Sign in")
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
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            text = "Make it yours",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Mode", style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = theme.mode == mode,
                            onClick = { onChange(theme.copy(mode = mode)) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        ) {
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Color", style = MaterialTheme.typography.titleMedium)

                ThemePreset.entries.forEach { preset ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = theme.preset == preset,
                            onClick = { onChange(theme.copy(preset = preset)) },
                        )

                        Text(preset.label)
                    }
                }
            }
        }
    }
}
