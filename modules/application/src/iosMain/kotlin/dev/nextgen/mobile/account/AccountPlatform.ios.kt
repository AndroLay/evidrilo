package dev.nextgen.mobile.account

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.NSBundle
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.UIKit.UIApplication
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
actual fun createAccountClientConfiguration(): AccountClientConfiguration = AccountClientConfiguration(
    supabaseUrl = infoValue("SUPABASE_URL"),
    publishableKey = infoValue("SUPABASE_PUBLISHABLE_KEY"),
    redirectUrl = infoValue("SUPABASE_AUTH_REDIRECT_URL").ifBlank { DEFAULT_ACCOUNT_AUTH_REDIRECT_URL },
    apiBaseUrl = infoValue("EVIDRILO_API_BASE_URL"),
)

@OptIn(ExperimentalForeignApi::class)
private fun infoValue(key: String): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String)?.trim().orEmpty()

actual fun createAccountAuthPlatform(): AccountAuthPlatform = IosAccountAuthPlatform()

@OptIn(ExperimentalForeignApi::class)
private class IosAccountAuthPlatform : AccountAuthPlatform {
    override fun openExternalUrl(url: String): Boolean =
        UIApplication.sharedApplication.openURL(NSURL(string = url))
}

@OptIn(ExperimentalForeignApi::class)
actual fun createAccountHttpTransport(): AccountHttpTransport = IosAccountHttpTransport()

@OptIn(ExperimentalForeignApi::class)
private class IosAccountHttpTransport : AccountHttpTransport {
    private val sessionDelegate = IosNoRedirectSessionDelegate()
    private val session = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.ephemeralSessionConfiguration(),
        delegate = sessionDelegate,
        delegateQueue = null,
    )

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse = awaitCancellableRequest { complete ->
        validateAccountHttpRequest(url, body)
        val request = NSMutableURLRequest.requestWithURL(NSURL(string = url)).apply {
            setHTTPMethod(method)
            headers.forEach { (name, value) -> setValue(value, forHTTPHeaderField = name) }
            body.toNSData()?.let(::setHTTPBody)
        }
        val task: NSURLSessionDataTask = session.dataTaskWithRequest(
            request as NSURLRequest,
        ) { data: NSData?, response: NSURLResponse?, error: NSError? ->
            if (error != null) {
                complete(Result.failure(Exception("Auth transport request failed.")))
                return@dataTaskWithRequest
            }
            val httpResponse = response as? NSHTTPURLResponse
            if (httpResponse == null) {
                complete(Result.failure(Exception("Auth transport response was invalid.")))
                return@dataTaskWithRequest
            }
            val responseBody = try {
                data?.boundedUtf8String() ?: ""
            } catch (exception: Exception) {
                complete(Result.failure(exception))
                return@dataTaskWithRequest
            }
            complete(Result.success(AccountHttpResponse(httpResponse.statusCode.toInt(), responseBody)))
        }
        task.resume()
        val cancel: () -> Unit = { task.cancel() }
        cancel
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosNoRedirectSessionDelegate : NSObject(), NSURLSessionTaskDelegateProtocol {
    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit,
    ) {
        completionHandler(null)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun String.toNSData(): NSData? {
    if (isEmpty()) return null
    val bytes = encodeToByteArray()
    return bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.boundedUtf8String(): String {
    if (length > MAX_ACCOUNT_HTTP_BODY_BYTES.toULong()) {
        throw IllegalStateException("Auth response exceeded the bounded body limit.")
    }
    val rawBytes = bytes?.reinterpret<kotlinx.cinterop.ByteVar>() ?: return ""
    return ByteArray(length.toInt()) { index -> rawBytes[index] }.decodeToString()
}
