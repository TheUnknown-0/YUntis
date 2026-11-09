package sh.lsx.untisx.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.lsx.untisx.R
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountUnavailableException
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.School
import sh.lsx.untisx.ui.util.AppMotion
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled
import sh.lsx.untisx.ui.util.UiText
import sh.lsx.untisx.ui.util.motionEffects
import sh.lsx.untisx.ui.util.motionSpatial
import sh.lsx.untisx.ui.util.uiTextString
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
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
    var showPassword by remember { mutableStateOf(false) }
    var signingIn by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }

    val errorShake = remember { Animatable(0f) }
    val animations = LocalAnimationsEnabled.current
    val effectsSpec = motionEffects<Float>()
    val scaleSpec = motionSpatial<Float>()

    LaunchedEffect(error, animations) {
        if (animations && error != null) {
            errorShake.snapTo(0f)

            listOf(-14f, 11f, -7f, 4f, 0f).forEach { target ->
                errorShake.animateTo(target, tween(durationMillis = 45, easing = LinearEasing))
            }
        }
    }

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
                error = UiText.Res(R.string.error_network)
                suggestionsExpanded = false
            }
        } catch (e: Exception) {
            suggestions = emptyList()
            suggestionsExpanded = false
        } finally {
            searching = false
        }
    }

    fun signIn() {
        val school = selectedSchool ?: return
        val signedInUser = user.trim()

        signingIn = true
        error = null

        scope.launch {
            try {
                val result = UntisClient.authenticate(
                    server = school.server,
                    loginName = school.loginName,
                    user = signedInUser,
                    password = password,
                )

                AccountPrefs.addAccount(
                    context,
                    AuthData(
                        server = school.server,
                        loginName = school.loginName,
                        displayName = school.displayName,
                        user = signedInUser,
                        session = result,
                    ),
                    password,
                )

                signedIn = true
                error = null
                onDone?.invoke()
            } catch (e: UntisException.Network) {
                error = UiText.Res(R.string.error_network)
            } catch (e: UntisException.BadCredentials) {
                error = UiText.Res(R.string.error_bad_credentials)
            } catch (e: AccountUnavailableException) {
                error = UiText.Res(R.string.error_account_unavailable)
            } catch (e: UntisException.Api) {
                error = UiText.Res(
                    R.string.error_untis_generic,
                    listOf(UiText.Raw(e.code.toString()), UiText.Raw(e.message)),
                )
            } catch (e: Exception) {
                error = UiText.Res(
                    R.string.error_unexpected,
                    listOf(UiText.Raw(e.message ?: "")),
                )
            } finally {
                signingIn = false
            }
        }
    }

    val sel = selectedSchool

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = stringResource(R.string.untisx_logo),
            modifier = Modifier.size(96.dp),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.login_connect_account),
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
                text = stringResource(R.string.login_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.login_teacher_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

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
                label = { Text(stringResource(R.string.login_school_label)) },
                singleLine = true,
                enabled = !signedIn,
                supportingText = when {
                    sel != null -> {
                        {
                            Text(stringResource(R.string.login_school_selected, sel.server, sel.loginName))
                        }
                    }

                    searching -> {
                        { Text(stringResource(R.string.login_searching)) }
                    }

                    tooManyResults -> {
                        { Text(stringResource(R.string.login_too_many_results)) }
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
                        onClick = Haptics.haptic {
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
            label = { Text(stringResource(R.string.login_username)) },
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
            label = { Text(stringResource(R.string.login_password)) },
            singleLine = true,
            enabled = !signedIn,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }, enabled = !signedIn) {
                    Text(stringResource(if (showPassword) R.string.login_password_hide else R.string.login_password_show))
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        var lastError by remember { mutableStateOf<String?>(null) }

        error?.let { lastError = uiTextString(it) }

        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(animationSpec = effectsSpec) +
                expandVertically(animationSpec = if (animations) AppMotion.spatial() else snap()),
            exit = fadeOut(animationSpec = effectsSpec) +
                shrinkVertically(animationSpec = if (animations) AppMotion.spatial() else snap()),
        ) {
            Text(
                text = lastError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.offset { IntOffset(errorShake.value.roundToInt(), 0) },
            )
        }

        Spacer(Modifier.height(16.dp))

        AnimatedContent(
            targetState = signedIn,
            transitionSpec = {
                (fadeIn(animationSpec = effectsSpec) +
                    scaleIn(initialScale = 0.92f, animationSpec = scaleSpec)) togetherWith
                    fadeOut(animationSpec = effectsSpec)
            },
            label = "loginDone",
        ) { done ->
            if (done) {
                Text(
                    text = stringResource(R.string.login_signed_in),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(
                    onClick = Haptics.haptic(::signIn),
                    enabled = !signingIn && sel != null && user.isNotBlank() && password.isNotBlank(),
                ) {
                    AnimatedContent(
                        targetState = signingIn,
                        transitionSpec = {
                            fadeIn(animationSpec = effectsSpec) togetherWith
                                fadeOut(animationSpec = effectsSpec)
                        },
                        label = "loginButton",
                    ) { signing ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (signing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )

                                Spacer(Modifier.width(8.dp))
                            }

                            Text(
                                if (signing) stringResource(R.string.login_signing_in)
                                else stringResource(R.string.login_sign_in),
                            )
                        }
                    }
                }
            }
        }
    }
}
