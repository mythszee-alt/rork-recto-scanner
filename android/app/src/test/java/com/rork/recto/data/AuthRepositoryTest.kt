./android/gradlew -p android testDebugUnitTest
pa./android/gradlew -p android testDebugUnitTestckage com.rork.recto.data

import android.content.Context
import android.content.SharedPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
    }

    @Test
    fun `signIn returns success when response is successful`() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"access_token": "abc", "refresh_token": "def", "expires_in": 3600, "user": {"id": "123", "email": "test@example.com"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val repository = AuthRepository(context, client)

        val result = repository.signIn("test@example.com", "password")

        assertTrue(result.isSuccess)
        assertEquals("abc", result.getOrNull()?.accessToken)
        assertEquals("123", result.getOrNull()?.user?.id)
    }

    @Test
    fun `signIn returns failure when response is 401`() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"message": "Invalid login credentials"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val repository = AuthRepository(context, client)

        val result = repository.signIn("test@example.com", "wrong")

        assertTrue(result.isFailure)
        assertEquals("The email or password is incorrect", result.exceptionOrNull()?.message)
    }
}
