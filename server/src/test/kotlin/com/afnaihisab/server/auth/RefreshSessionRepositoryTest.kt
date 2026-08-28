package com.afnaihisab.server.auth

import com.afnaihisab.core.domain.User
import com.afnaihisab.server.db.DatabaseFactory
import com.afnaihisab.server.repository.ExposedUserRepository
import com.afnaihisab.server.testAppConfig
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ADR-0008's session-family tracking: rotation-on-use, single-use enforcement, and family-wide
 * revocation on reuse of an already-burned token. Real H2 (`docs/guidelines/exposed-koin.md`), not
 * mocked — an ORM-mapping bug against `V3__add_refresh_sessions.sql` is exactly what mocked SQL
 * would hide.
 *
 * No HTTP route exercises this yet (`docs/specs/registration-login.md` has no `/refresh` endpoint)
 * — this is the repository-level coverage the mechanism needs to exist before that route is built.
 */
@OptIn(ExperimentalUuidApi::class)
class RefreshSessionRepositoryTest {
    private lateinit var databaseFactory: DatabaseFactory
    private lateinit var database: Database
    private lateinit var repository: ExposedRefreshSessionRepository
    private lateinit var userRepository: ExposedUserRepository

    private val userId = Uuid.parse("00000000-0000-7000-8000-0000000000a1")
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")

    @BeforeTest
    fun setUp() {
        databaseFactory = DatabaseFactory(testAppConfig().database)
        database = databaseFactory.connectAndMigrate()
        repository = ExposedRefreshSessionRepository(database)
        userRepository = ExposedUserRepository(database)
    }

    @AfterTest
    fun tearDown() {
        databaseFactory.close()
    }

    private suspend fun seedUser() {
        userRepository.insert(
            User(
                id = userId,
                email = "session-owner@example.com",
                passwordHash = "irrelevant-for-this-test",
                displayName = "Session Owner",
                isGhost = false,
                createdAt = t0,
            ),
        )
    }

    @Test
    fun `issuing a session persists it as the head of a new family`() =
        runTest {
            seedUser()
            val sessionId = Uuid.parse("00000000-0000-7000-8000-0000000000b1")
            val familyId = Uuid.parse("00000000-0000-7000-8000-0000000000c1")

            val session = repository.issue(userId, sessionId, familyId, t0, t0 + 24.hours)

            assertEquals(sessionId, session.id)
            assertEquals(familyId, session.familyId)
            assertNull(session.revokedAt)
            assertNull(session.replacedById)
        }

    @Test
    fun `rotating a valid unused session issues a new session in the same family and burns the old one`() =
        runTest {
            seedUser()
            val sessionId = Uuid.parse("00000000-0000-7000-8000-0000000000b2")
            val familyId = Uuid.parse("00000000-0000-7000-8000-0000000000c2")
            repository.issue(userId, sessionId, familyId, t0, t0 + 24.hours)

            val newSessionId = Uuid.parse("00000000-0000-7000-8000-0000000000b3")
            val outcome = repository.rotate(sessionId, newSessionId, t0 + 1.hours, t0 + 25.hours)

            val rotated = assertIs<RotateOutcome.Rotated>(outcome)
            assertEquals(newSessionId, rotated.newSession.id)
            assertEquals(familyId, rotated.newSession.familyId, "rotation stays within the same session family")

            // The old session is now single-used: rotating it again must be reuse detection, not
            // a second successful rotation.
            val reuse = repository.rotate(sessionId, Uuid.parse("00000000-0000-7000-8000-0000000000b4"), t0 + 2.hours, t0 + 26.hours)
            assertIs<RotateOutcome.ReuseDetected>(reuse)
        }

    @Test
    fun `reusing an already-rotated token revokes every other session in its family`() =
        runTest {
            seedUser()
            val sessionId = Uuid.parse("00000000-0000-7000-8000-0000000000b5")
            val familyId = Uuid.parse("00000000-0000-7000-8000-0000000000c5")
            repository.issue(userId, sessionId, familyId, t0, t0 + 24.hours)

            val rotatedSessionId = Uuid.parse("00000000-0000-7000-8000-0000000000b6")
            val rotated = repository.rotate(sessionId, rotatedSessionId, t0 + 1.hours, t0 + 25.hours)
            assertIs<RotateOutcome.Rotated>(rotated)

            // Reusing the burned original token is the theft-detection trigger.
            val reuse = repository.rotate(sessionId, Uuid.parse("00000000-0000-7000-8000-0000000000b7"), t0 + 2.hours, t0 + 26.hours)
            assertIs<RotateOutcome.ReuseDetected>(reuse)

            // The token that came from the legitimate rotation must now be revoked too, even
            // though it was never itself reused — that is the entire point of family tracking.
            val afterFamilyRevocation =
                repository.rotate(rotatedSessionId, Uuid.parse("00000000-0000-7000-8000-0000000000b8"), t0 + 3.hours, t0 + 27.hours)
            assertIs<RotateOutcome.ReuseDetected>(afterFamilyRevocation)
        }

    @Test
    fun `rotating a session past its own expiry is rejected as expired`() =
        runTest {
            seedUser()
            val sessionId = Uuid.parse("00000000-0000-7000-8000-0000000000b9")
            val familyId = Uuid.parse("00000000-0000-7000-8000-0000000000c9")
            repository.issue(userId, sessionId, familyId, t0, t0 + 1.hours)

            val outcome =
                repository.rotate(sessionId, Uuid.parse("00000000-0000-7000-8000-0000000000ba"), t0 + 2.hours, t0 + 3.hours)

            assertIs<RotateOutcome.Expired>(outcome)
        }

    @Test
    fun `rotating an unknown session id reports not found`() =
        runTest {
            val outcome =
                repository.rotate(
                    Uuid.parse("00000000-0000-7000-8000-0000000000ff"),
                    Uuid.parse("00000000-0000-7000-8000-0000000000fe"),
                    t0,
                    t0 + 24.hours,
                )

            assertIs<RotateOutcome.NotFound>(outcome)
        }
}
