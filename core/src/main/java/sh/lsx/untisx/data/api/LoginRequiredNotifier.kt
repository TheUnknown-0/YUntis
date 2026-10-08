package sh.lsx.untisx.data.api

import android.content.Context

fun interface LoginRequiredNotifier {
    fun onLoginRequired(context: Context)
}
