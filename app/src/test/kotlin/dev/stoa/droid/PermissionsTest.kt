package dev.stoa.droid

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PermissionsTest {
    private val wakeLock = "android.permission.WAKE_LOCK"
    private val bootCompleted = "android.permission.RECEIVE_BOOT_COMPLETED"

    private fun requestedByManifest(): List<String> {
        val context = RuntimeEnvironment.getApplication()
        return context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toList()
    }

    @Test
    fun `S001-6 the requested permissions are exactly the approved list in app permissions yaml`() {
        // Unit tests run with the module directory as the working directory.
        val approved = PermissionAllowlist.parse(File("permissions.yaml").readText())

        assertEquals(
            emptyList<String>(),
            PermissionAllowlist.violations(requestedByManifest(), approved.map { it.name }),
        )
    }

    @Test
    fun `S001-6 every approved entry names its library and the reason it is accepted`() {
        val approved = PermissionAllowlist.parse(File("permissions.yaml").readText())

        assertTrue(approved.isNotEmpty())
        approved.forEach {
            assertTrue("${it.name} has no library", it.library.isNotBlank())
            assertTrue("${it.name} has no reason", it.reason.isNotBlank())
        }
    }

    @Test
    fun `S001-6 a requested permission that is not on the list fails`() {
        val violations = PermissionAllowlist.violations(listOf(wakeLock, bootCompleted), listOf(wakeLock))

        assertEquals(listOf("requested but not approved in app/permissions.yaml: $bootCompleted"), violations)
    }

    @Test
    fun `S001-6 a listed permission that the manifest does not request fails`() {
        val violations = PermissionAllowlist.violations(listOf(wakeLock), listOf(wakeLock, bootCompleted))

        assertEquals(listOf("approved in app/permissions.yaml but not requested: $bootCompleted"), violations)
    }

    @Test
    fun `S001-6 INTERNET in the manifest fails even when the list approves it`() {
        val violations =
            PermissionAllowlist.violations(
                listOf(wakeLock, PermissionAllowlist.INTERNET),
                listOf(wakeLock, PermissionAllowlist.INTERNET),
            )

        assertEquals(listOf("never allowed: ${PermissionAllowlist.INTERNET}"), violations)
    }

    @Test
    fun `S001-6 INTERNET on the list fails even when the manifest does not request it`() {
        val violations =
            PermissionAllowlist.violations(
                listOf(wakeLock),
                listOf(wakeLock, PermissionAllowlist.INTERNET),
            )

        assertTrue(violations.toString(), "never allowed: ${PermissionAllowlist.INTERNET}" in violations)
    }

    @Test
    fun `S001-6 the list parser reads name, library and reason`() {
        val yaml =
            """
            # comment
            permissions:
              - name: $wakeLock
                library: androidx.work (via LiteRT-LM)
                reason: It comes with WorkManager: the app schedules no work.
            """.trimIndent()

        assertEquals(
            listOf(
                PermissionAllowlist.Entry(
                    wakeLock,
                    "androidx.work (via LiteRT-LM)",
                    "It comes with WorkManager: the app schedules no work.",
                ),
            ),
            PermissionAllowlist.parse(yaml),
        )
    }
}
