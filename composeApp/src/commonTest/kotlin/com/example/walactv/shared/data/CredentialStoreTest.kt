package com.example.walactv.shared.data

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CredentialStoreTest {

    private fun createCredentialStore(): CredentialStore {
        return CredentialStore(MapSettings())
    }

    @Test
    fun hasCredentials_returnsFalseByDefault() {
        val store = createCredentialStore()
        assertFalse(store.hasCredentials())
    }

    @Test
    fun hasCredentials_returnsTrueAfterSave() {
        val store = createCredentialStore()
        store.save("user@test.com", "password123")
        assertTrue(store.hasCredentials())
    }

    @Test
    fun hasCredentials_returnsFalseWhenOnlyUsernameSet() {
        val settings = MapSettings()
        settings.putString("username", "user@test.com")
        val store = CredentialStore(settings)
        assertFalse(store.hasCredentials())
    }

    @Test
    fun hasCredentials_returnsFalseWhenOnlyPasswordSet() {
        val settings = MapSettings()
        settings.putString("password", "password123")
        val store = CredentialStore(settings)
        assertFalse(store.hasCredentials())
    }

    @Test
    fun save_storesUsernameAndPassword() {
        val store = createCredentialStore()
        store.save("user@test.com", "password123")

        assertEquals("user@test.com", store.username())
        assertEquals("password123", store.password())
    }

    @Test
    fun save_overwritesExistingCredentials() {
        val store = createCredentialStore()
        store.save("old@test.com", "oldpass")
        store.save("new@test.com", "newpass")

        assertEquals("new@test.com", store.username())
        assertEquals("newpass", store.password())
    }

    @Test
    fun clear_removesAllCredentials() {
        val store = createCredentialStore()
        store.save("user@test.com", "password123")
        store.clear()

        assertFalse(store.hasCredentials())
        assertEquals("", store.username())
        assertEquals("", store.password())
    }

    @Test
    fun clear_isIdempotent() {
        val store = createCredentialStore()
        store.clear()
        store.clear()

        assertFalse(store.hasCredentials())
        assertEquals("", store.username())
        assertEquals("", store.password())
    }

    @Test
    fun username_returnsEmptyStringWhenNotSet() {
        val store = createCredentialStore()
        assertEquals("", store.username())
    }

    @Test
    fun password_returnsEmptyStringWhenNotSet() {
        val store = createCredentialStore()
        assertEquals("", store.password())
    }

    @Test
    fun save_handlesEmptyStrings() {
        val store = createCredentialStore()
        store.save("", "")

        assertFalse(store.hasCredentials())
        assertEquals("", store.username())
        assertEquals("", store.password())
    }

    @Test
    fun save_handlesSpecialCharacters() {
        val store = createCredentialStore()
        store.save("user@test.com", "p@ssw0rd!#%")

        assertEquals("user@test.com", store.username())
        assertEquals("p@ssw0rd!#%", store.password())
    }
}
