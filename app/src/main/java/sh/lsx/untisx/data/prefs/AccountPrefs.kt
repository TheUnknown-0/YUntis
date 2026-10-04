package sh.lsx.untisx.data.prefs

import android.content.Context

object AccountPrefs {

    const val KEY_ACCOUNTS = "accounts_blob"
    const val KEY_ACTIVE = "active_account"

    fun accountId(data: AuthData): String = "${data.server}/${data.loginName}/${data.user}"

    fun getActive(): AuthData? = AccountStore.active

    suspend fun addAccount(context: Context, data: AuthData, password: String) =
        AccountStore.addAccount(context, data, password)

    suspend fun updateSession(context: Context, updated: AuthData) =
        AccountStore.updateSession(context, updated)

    suspend fun removeAccount(context: Context, id: String) =
        AccountStore.removeAccount(context, id)
}
