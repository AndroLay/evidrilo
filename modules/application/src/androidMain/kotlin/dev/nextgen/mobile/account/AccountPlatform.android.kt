package dev.nextgen.mobile.account

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.nextgen.mobile.application.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

object AndroidAccountAuthStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun context(): Context? = applicationContext
}

actual fun createAccountClientConfiguration(): AccountClientConfiguration = AccountClientConfiguration(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    redirectUrl = BuildConfig.SUPABASE_AUTH_REDIRECT_URL.ifBlank { DEFAULT_ACCOUNT_AUTH_REDIRECT_URL },
    apiBaseUrl = BuildConfig.EVIDRILO_API_BASE_URL,
)

actual fun createAccountAuthPlatform(): AccountAuthPlatform = AndroidAccountAuthPlatform()

private class AndroidAccountAuthPlatform : AccountAuthPlatform {
    override fun openExternalUrl(url: String): Boolean {
        val context = AndroidAccountAuthStorage.context() ?: return false
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

actual fun createAccountHttpTransport(): AccountHttpTransport = AndroidAccountHttpTransport()

private class AndroidAccountHttpTransport : AccountHttpTransport {
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

private fun readBounded(stream: InputStream): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        total += count
        if (total > MAX_ACCOUNT_HTTP_BODY_BYTES) {
            throw IllegalStateException("Auth response exceeded the bounded body limit.")
        }
        output.write(buffer, 0, count)
    }
    return output.toByteArray().decodeToString()
}
