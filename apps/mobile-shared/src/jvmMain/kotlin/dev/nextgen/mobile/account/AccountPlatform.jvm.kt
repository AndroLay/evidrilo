package dev.nextgen.mobile.account

import java.awt.Desktop
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal actual fun createAccountClientConfiguration(): AccountClientConfiguration = AccountClientConfiguration(
    supabaseUrl = readJvmValue("evidrilo.supabaseUrl", "SUPABASE_URL"),
    publishableKey = readJvmValue("evidrilo.supabasePublishableKey", "SUPABASE_PUBLISHABLE_KEY"),
    redirectUrl = readJvmValue("evidrilo.supabaseAuthRedirectUrl", "SUPABASE_AUTH_REDIRECT_URL")
        .ifBlank { DEFAULT_ACCOUNT_AUTH_REDIRECT_URL },
    apiBaseUrl = readJvmValue("evidrilo.apiBaseUrl", "EVIDRILO_API_BASE_URL"),
)

private fun readJvmValue(property: String, environment: String): String =
    System.getProperty(property)?.trim().orEmpty().ifBlank { System.getenv(environment)?.trim().orEmpty() }

internal actual fun createAccountAuthPlatform(): AccountAuthPlatform = JvmAccountAuthPlatform()

private class JvmAccountAuthPlatform : AccountAuthPlatform {
    override fun openExternalUrl(url: String): Boolean {
        if (!Desktop.isDesktopSupported()) return false
        return runCatching { Desktop.getDesktop().browse(URI(url)); true }.getOrDefault(false)
    }
}

internal actual fun createAccountHttpTransport(): AccountHttpTransport = JvmAccountHttpTransport()

private class JvmAccountHttpTransport : AccountHttpTransport {
    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse = awaitCancellableRequest { complete ->
        validateAccountHttpRequest(url, body)
        val cancelled = AtomicBoolean(false)
        val connectionReference = AtomicReference<HttpURLConnection?>()
        val worker = thread(start = true, isDaemon = true, name = "evidrilo-auth-http") {
            var connection: HttpURLConnection? = null
            try {
                if (cancelled.get()) return@thread
                connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    instanceFollowRedirects = ACCOUNT_HTTP_REDIRECTS_ALLOWED
                    useCaches = false
                    doInput = true
                    headers.forEach { (name, value) -> setRequestProperty(name, value) }
                    if (body.isNotEmpty()) {
                        doOutput = true
                        outputStream.use { stream -> stream.write(body.encodeToByteArray()) }
                    }
                }
                connectionReference.set(connection)
                if (cancelled.get()) return@thread
                val statusCode = connection.responseCode
                val responseStream = if (statusCode in 200..399) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val responseBody = responseStream?.use(::readBounded).orEmpty()
                complete(Result.success(AccountHttpResponse(statusCode, responseBody)))
            } catch (exception: Exception) {
                complete(Result.failure(exception))
            } finally {
                connectionReference.compareAndSet(connection, null)
                connection?.disconnect()
            }
        }
        val cancel: () -> Unit = {
            cancelled.set(true)
            connectionReference.get()?.disconnect()
            worker.interrupt()
        }
        cancel
    }
}

internal actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0)
    return ByteArray(size).also(SecureRandom()::nextBytes)
}

private fun readBounded(stream: InputStream): String {
    val output = ByteArrayOutputStreamWithLimit(MAX_ACCOUNT_HTTP_BODY_BYTES)
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray().decodeToString()
}

private class ByteArrayOutputStreamWithLimit(
    private val limit: Int,
) {
    private val bytes = ArrayList<Byte>()

    fun write(buffer: ByteArray, offset: Int, count: Int) {
        if (bytes.size + count > limit) {
            throw IllegalStateException("Auth response exceeded the bounded body limit.")
        }
        repeat(count) { index -> bytes += buffer[offset + index] }
    }

    fun toByteArray(): ByteArray = ByteArray(bytes.size) { index -> bytes[index] }
}
