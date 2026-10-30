package sh.lsx.untisx.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme
import sh.lsx.untisx.wear.ui.WearRoot
import sh.lsx.untisx.wear.ui.WearViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WearTheme {
                WearRoot(viewModel())
            }
        }
    }
}

@Composable
private fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = dynamicColorScheme(LocalContext.current) ?: MaterialTheme.colorScheme,
        content = content,
    )
}
