package sh.lsx.untisx.data.net

import okhttp3.OkHttpClient

object Http {

    // set by the app at startup, e.g. "UntisX/0.7.0 (+https://codeberg.org/Losterixx/UntisX)"
    @Volatile
    var userAgent: String = "UntisX"

    val base: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .build(),
            )
        }
        .build()
}
