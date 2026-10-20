package com.example.royalremotelab.network

import com.example.royalremotelab.model.PortCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.Charset

enum class HttpErrorType(val label: String) {
    CONNECTION_REFUSED("Connection Refused (Server closed port or rejected socket)"),
    TIMED_OUT("Connection / Read Timed Out"),
    NO_ROUTE_TO_HOST("No Route to Host (Host unreachable or Wi-Fi disconnected)"),
    UNEXPECTED_HTTP_STATUS("Unexpected HTTP Status Code"),
    EMPTY_RESPONSE("Empty Response Body"),
    SOCKET_ERROR("Socket Communication Error"),
    UNKNOWN_ERROR("Unexpected Error")
}

data class SimpleHttpResponse(
    val statusCode: Int,
    val responseBody: String,
    val durationMs: Long,
    val isSuccess: Boolean,
    val headers: Map<String, List<String>> = emptyMap(),
    val errorType: HttpErrorType? = null,
    val rawException: String? = null
) {
    fun getHeader(name: String): String? {
        val entry = headers.entries.find { it.key.equals(name, ignoreCase = true) }
        return entry?.value?.firstOrNull()
    }
}

object NetworkUtils {

    /**
     * Performs a safe, bounded TCP connection probe against a single target host and port.
     * Does not send arbitrary payloads. Closes socket immediately after connection test.
     */
    suspend fun probeTcpPort(host: String, port: Int, serviceName: String, timeoutMs: Int): PortCheck =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var socket: Socket? = null
            try {
                socket = Socket()
                val address = InetSocketAddress(host, port)
                socket.connect(address, timeoutMs)
                val duration = System.currentTimeMillis() - startTime
                PortCheck(
                    port = port,
                    serviceName = serviceName,
                    isOpen = true,
                    latencyMs = duration,
                    details = "Connection accepted in ${duration}ms"
                )
            } catch (e: ConnectException) {
                val duration = System.currentTimeMillis() - startTime
                PortCheck(
                    port = port,
                    serviceName = serviceName,
                    isOpen = false,
                    latencyMs = duration,
                    details = "Connection refused (port closed or filtered)"
                )
            } catch (e: SocketTimeoutException) {
                val duration = System.currentTimeMillis() - startTime
                PortCheck(
                    port = port,
                    serviceName = serviceName,
                    isOpen = false,
                    latencyMs = duration,
                    details = "Connection timed out after ${timeoutMs}ms"
                )
            } catch (e: NoRouteToHostException) {
                val duration = System.currentTimeMillis() - startTime
                PortCheck(
                    port = port,
                    serviceName = serviceName,
                    isOpen = false,
                    latencyMs = duration,
                    details = "No route to host (check Wi-Fi or TV IP)"
                )
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                PortCheck(
                    port = port,
                    serviceName = serviceName,
                    isOpen = false,
                    latencyMs = duration,
                    details = "Failed: ${e.message ?: e.javaClass.simpleName}"
                )
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }
        }

    /**
     * Sends an HTTP request with strict timeouts, full header capture, and charset specification.
     * Guaranteed to execute off the main thread.
     */
    suspend fun executeHttpRequest(
        urlString: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = 3000,
        charset: Charset = Charsets.UTF_8
    ): SimpleHttpResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.useCaches = false
            connection.instanceFollowRedirects = false

            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }

            if (body != null) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, charset).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }

            val statusCode = connection.responseCode
            val isSuccess = statusCode in 200..299
            val stream = if (isSuccess) connection.inputStream else connection.errorStream
            val responseText = stream?.let {
                BufferedReader(InputStreamReader(it, charset)).use { reader ->
                    reader.readText().take(16384) // bounded read
                }
            } ?: ""

            val respHeaders = connection.headerFields.filterKeys { it != null }
            val duration = System.currentTimeMillis() - startTime

            val errorType = when {
                !isSuccess -> HttpErrorType.UNEXPECTED_HTTP_STATUS
                isSuccess && responseText.isBlank() -> HttpErrorType.EMPTY_RESPONSE
                else -> null
            }

            SimpleHttpResponse(
                statusCode = statusCode,
                responseBody = responseText,
                durationMs = duration,
                isSuccess = isSuccess,
                headers = respHeaders,
                errorType = errorType
            )
        } catch (e: ConnectException) {
            val duration = System.currentTimeMillis() - startTime
            SimpleHttpResponse(
                statusCode = -1,
                responseBody = "",
                durationMs = duration,
                isSuccess = false,
                errorType = HttpErrorType.CONNECTION_REFUSED,
                rawException = e.message ?: "Connection refused"
            )
        } catch (e: SocketTimeoutException) {
            val duration = System.currentTimeMillis() - startTime
            SimpleHttpResponse(
                statusCode = -1,
                responseBody = "",
                durationMs = duration,
                isSuccess = false,
                errorType = HttpErrorType.TIMED_OUT,
                rawException = e.message ?: "Socket timed out"
            )
        } catch (e: NoRouteToHostException) {
            val duration = System.currentTimeMillis() - startTime
            SimpleHttpResponse(
                statusCode = -1,
                responseBody = "",
                durationMs = duration,
                isSuccess = false,
                errorType = HttpErrorType.NO_ROUTE_TO_HOST,
                rawException = e.message ?: "No route to host"
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            SimpleHttpResponse(
                statusCode = -1,
                responseBody = "",
                durationMs = duration,
                isSuccess = false,
                errorType = HttpErrorType.UNKNOWN_ERROR,
                rawException = e.message ?: e.javaClass.simpleName
            )
        } finally {
            connection?.disconnect()
        }
    }
}
