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

data class SimpleHttpResponse(
    val statusCode: Int,
    val responseBody: String,
    val durationMs: Long,
    val isSuccess: Boolean
)

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
     * Sends a simple HTTP request with strict timeouts.
     */
    suspend fun executeHttpRequest(
        urlString: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = 3000
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
                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }

            val statusCode = connection.responseCode
            val isSuccess = statusCode in 200..299
            val stream = if (isSuccess) connection.inputStream else connection.errorStream
            val responseText = stream?.let {
                BufferedReader(InputStreamReader(it, "UTF-8")).use { reader ->
                    reader.readText().take(4096) // bounded read
                }
            } ?: ""
            val duration = System.currentTimeMillis() - startTime
            SimpleHttpResponse(statusCode, responseText, duration, isSuccess)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            SimpleHttpResponse(-1, e.message ?: e.javaClass.simpleName, duration, false)
        } finally {
            connection?.disconnect()
        }
    }
}
