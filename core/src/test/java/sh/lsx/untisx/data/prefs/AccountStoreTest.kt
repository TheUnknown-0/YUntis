package sh.lsx.untisx.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.CorePrefs
import sh.lsx.untisx.data.model.AuthResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountStoreTest {

    private lateinit var context: Context
    private lateinit var cryptor: FakeCryptor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        AccountStore.resetForTests()
        cryptor = FakeCryptor()
        AccountStore.cryptor = cryptor
    }

    @After
    fun tearDown() {
        AccountStore.resetForTests()
    }

    private fun prefs() =
        context.getSharedPreferences(CorePrefs.PREFS_NAME, Context.MODE_PRIVATE)

    @Test fun `late timetable result cannot recreate a removed accounts cache`() = runTest {
        val auth = account("late-watch")
        AccountStore.addAccount(context, auth, "secret")

        val id = AccountPrefs.accountId(auth)
        assertNotNull(AccountStore.mergeTimetableCache(context, id, sh.lsx.untisx.data.TimetableCache.Data()))
        AccountStore.removeAccount(context, id)
        assertNull(AccountStore.mergeTimetableCache(context, id, sh.lsx.untisx.data.TimetableCache.Data()))
        assertNull(sh.lsx.untisx.data.TimetableCache.load(context, id))
    }

    @Test fun `concurrent timetable snapshots keep the newest week regardless of save order`() = runTest {
        val auth = account("concurrent-watch")
        AccountStore.addAccount(context, auth, "secret")
        val id = AccountPrefs.accountId(auth)

        val jobs = (1..10).map { index ->
            async(kotlinx.coroutines.Dispatchers.IO) {
                AccountStore.mergeTimetableCache(context, id, sh.lsx.untisx.data.TimetableCache.Data(
                    weeks = mapOf("20261005" to listOf(sh.lsx.untisx.data.model.TimetableEntry(id = index))),
                    weekFetchedAt = mapOf("20261005" to index.toLong()),
                ))
            }
        }

        jobs.forEach { it.await() }
        assertEquals(10, sh.lsx.untisx.data.TimetableCache.load(context, id)?.weeks?.get("20261005")?.single()?.id)
    }

    private fun account(user: String, sessionId: String = "session-1") = AuthData(
        server = "https://school.example",
        loginName = "school",
        displayName = "School",
        user = user,
        session = AuthResult(sessionId = sessionId),
    )

    @Test
    fun `add account activates it and stores the password`() = runTest {
        val data = account("max")

        AccountStore.addAccount(context, data, "secret")

        assertEquals(listOf(data), AccountStore.accounts.value)
        assertEquals(AccountPrefs.accountId(data), AccountStore.activeId.value)
        assertEquals("secret", PasswordVault.retrieve(context, AccountPrefs.accountId(data)))
        assertEquals(data, AccountStore.active)
    }

    @Test
    fun `accounts survive a reload from preferences`() = runTest {
        val data = account("max")

        AccountStore.addAccount(context, data, "secret")

        AccountStore.resetForTests()
        AccountStore.cryptor = cryptor
        AccountStore.refresh(context)

        assertEquals(listOf(data), AccountStore.accounts.value)
        assertTrue(AccountStore.loaded.value)
        assertNull(AccountStore.error.value)
    }

    @Test
    fun `parallel session updates and adds do not lose accounts`() = runTest {
        val max = account("max")
        val ben = account("ben")

        AccountStore.addAccount(context, max, "pw-a")
        AccountStore.addAccount(context, ben, "pw-b")

        val updates = (0 until 20).map { index ->
            Thread {
                kotlinx.coroutines.runBlocking {
                    AccountStore.updateSession(context, max.copy(session = AuthResult("session-$index")))
                }
            }.apply { start() }
        }

        updates.forEach { it.join() }

        assertEquals(2, AccountStore.accounts.value.size)
        assertTrue(AccountStore.accounts.value.any { it.user == "max" })
        assertTrue(AccountStore.accounts.value.any { it.user == "ben" })

        val maxSession = AccountStore.accounts.value.first { it.user == "max" }.session.sessionId

        assertTrue(
            "session update was lost, kept: $maxSession",
            maxSession in (0 until 20).map { "session-$it" },
        )
    }

    @Test
    fun `transient decryption error blocks writes and keeps the blob`() = runTest {
        prefs().edit()
            .putString(AccountPrefs.KEY_ACCOUNTS, FakeCryptor.TRANSIENT)
            .commit()

        AccountStore.refresh(context)

        assertTrue(AccountStore.error.value is AccountLoadError.Transient)

        val before = prefs().getString(AccountPrefs.KEY_ACCOUNTS, null)

        var blocked = false
        try {
            AccountStore.addAccount(context, account("max"), "secret")
        } catch (e: AccountUnavailableException) {
            blocked = true
        }

        assertTrue(blocked)
        assertEquals(before, prefs().getString(AccountPrefs.KEY_ACCOUNTS, null))
    }

    @Test
    fun `key lost is surfaced and can be discarded explicitly`() = runTest {
        prefs().edit()
            .putString(AccountPrefs.KEY_ACCOUNTS, FakeCryptor.KEY_LOST)
            .commit()

        AccountStore.refresh(context)

        assertTrue(AccountStore.error.value is AccountLoadError.KeyLost)

        AccountStore.discardUnreadable(context)

        assertNull(AccountStore.error.value)
        assertTrue(AccountStore.loaded.value)
        assertTrue(AccountStore.accounts.value.isEmpty())
        assertNull(prefs().getString(AccountPrefs.KEY_ACCOUNTS, null))
    }

    @Test
    fun `decryption does not run on the caller thread`() = runTest {
        prefs().edit()
            .putString(AccountPrefs.KEY_ACCOUNTS, cryptor.encrypt("[]"))
            .commit()

        AccountStore.refresh(context)

        assertNotNull("decrypt was never invoked", cryptor.decryptThread)
        assertNotEquals(Thread.currentThread().name, cryptor.decryptThread)
    }

    @Test
    fun `removing the active account activates the remaining one`() = runTest {
        val max = account("max")
        val ben = account("ben")

        AccountStore.addAccount(context, max, "pw-a")
        AccountStore.addAccount(context, ben, "pw-b")

        AccountStore.removeAccount(context, AccountPrefs.accountId(ben))

        assertEquals(listOf(max), AccountStore.accounts.value)
        assertEquals(AccountPrefs.accountId(max), AccountStore.activeId.value)
        assertNull(PasswordVault.retrieve(context, AccountPrefs.accountId(ben)))
    }

    @Test
    fun `setActive ignores unknown ids`() = runTest {
        val max = account("max")
        AccountStore.addAccount(context, max, "pw")

        AccountStore.setActive(context, "missing-account")

        assertEquals(AccountPrefs.accountId(max), AccountStore.activeId.value)
    }

    @Test
    fun `adding the same account twice replaces it`() = runTest {
        val max = account("max")
        AccountStore.addAccount(context, max, "pw-a")

        val renamed = max.copy(displayName = "Renamed")
        AccountStore.addAccount(context, renamed, "pw-b")

        assertEquals(listOf(renamed), AccountStore.accounts.value)
        assertEquals("pw-b", PasswordVault.retrieve(context, AccountPrefs.accountId(max)))
    }

    @Test
    fun `blank passwords are not stored`() = runTest {
        val data = account("max")

        AccountStore.addAccount(context, data, "   ")

        assertNull(PasswordVault.retrieve(context, AccountPrefs.accountId(data)))
    }

    @Test
    fun `legacy single account blob is migrated`() = runTest {
        val data = account("max")
        val encoded = cryptor.encrypt(AuthPrefs.json.encodeToString(AuthData.serializer(), data))

        prefs().edit().putString(AuthPrefs.KEY_BLOB, encoded).commit()

        AccountStore.refresh(context)

        assertEquals(listOf(data), AccountStore.accounts.value)
        assertEquals(AccountPrefs.accountId(data), AccountStore.activeId.value)
        assertNull(prefs().getString(AuthPrefs.KEY_BLOB, null))
        assertNotNull(prefs().getString(AccountPrefs.KEY_ACCOUNTS, null))
    }
}
