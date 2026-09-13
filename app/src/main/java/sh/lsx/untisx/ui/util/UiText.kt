package sh.lsx.untisx.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class Res(
        @StringRes val res: Int,
        val args: List<UiText> = emptyList(),
    ) : UiText

    data class Raw(val value: String) : UiText
}

@Composable
@ReadOnlyComposable
fun uiTextString(text: UiText): String = when (text) {
    is UiText.Raw -> text.value
    is UiText.Res -> stringResource(
        text.res,
        *text.args.map { uiTextString(it) }.toTypedArray(),
    )
}
