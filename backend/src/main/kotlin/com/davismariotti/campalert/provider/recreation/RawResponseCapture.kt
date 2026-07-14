package com.davismariotti.campalert.provider.recreation

import com.davismariotti.campalert.provider.recreation.RawResponseCapture.takeAndClear
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Holds the literal HTTP response body text for the Recreation.gov call most recently executed on
 * the calling thread. [RawBodyCapturingInterceptor] runs synchronously inside `Call.execute()`, on
 * the same thread that then reads [takeAndClear] immediately afterward — safe without extra locking
 * since there's no concurrent access to a single thread's own ThreadLocal.
 */
object RawResponseCapture {
    private val threadLocal = ThreadLocal<String?>()

    fun set(body: String?) = threadLocal.set(body)

    fun takeAndClear(): String? {
        val value = threadLocal.get()
        threadLocal.remove()
        return value
    }
}

/** Peeks (doesn't consume) the response body so Retrofit's Jackson converter can still read it normally. */
class RawBodyCapturingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        RawResponseCapture.set(response.peekBody(Long.MAX_VALUE).string())
        return response
    }
}
