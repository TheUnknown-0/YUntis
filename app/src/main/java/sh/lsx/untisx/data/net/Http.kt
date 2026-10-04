package sh.lsx.untisx.data.net

import okhttp3.OkHttpClient

internal object Http {
    val base: OkHttpClient = OkHttpClient()
}
