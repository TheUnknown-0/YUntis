package sh.lsx.untisx.data.net

import okhttp3.OkHttpClient

object Http {

    // set by the app at startup, e.g. "YUntis/0.9.0"
    @Volatile
    var userAgent: String = "YUntis"

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
