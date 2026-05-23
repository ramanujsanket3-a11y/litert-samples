package com.google.ai.edge.aiedge.examples.textclassification

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.ai.edge.litertlm.LMEngine
import com.google.ai.edge.litertlm.Message
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.json.JSONObject

class AutomationServerService : Service() {
    private var serverSocket: ServerSocket? = null
    private var lmEngine: LMEngine? = null
    private var isRunning = true

    override fun onCreate() {
        super.onCreate()
        try {
            val options = LMEngine.Options.builder()
                .setModelPath("/sdcard/Download/gemma-4-E2B-it.litertlm")
                .setAccelerator(LMEngine.Accelerator.GPU)
                .build()
            lmEngine = LMEngine.create(options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        startNetworkListener()
    }

    private fun startNetworkListener() {
        thread {
            serverSocket = ServerSocket(5000)
            while (isRunning) {
                try {
                    val client = serverSocket?.accept() ?: break
                    thread {
                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val writer = PrintWriter(client.getOutputStream(), true)
                        
                        var line: String? = reader.readLine()
                        var contentLength = 0
                        while (!line.isNullOrBlank()) {
                            if (line.startsWith("Content-Length:")) {
                                contentLength = line.substringAfter(": ").trim().toInt()
                            }
                            line = reader.readLine()
                        }

                        val body = CharArray(contentLength)
                        reader.read(body, 0, contentLength)
                        val requestJson = JSONObject(String(body))
                        val userCommand = requestJson.optString("command", "")

                        val systemPrompt = "You are a laptop system automation router. Your response must be strictly valid JSON formatting containing 'action', 'cell', and 'value'."
                        val messages = listOf(Message.createSystemMessage(systemPrompt), Message.createUserMessage(userCommand))
                        
                        val aiResult = lmEngine?.generate(messages)
                        val textResponse = aiResult?.outputs?.firstOrNull()?.text ?: "{\"action\": \"chat\"}"

                        writer.println("HTTP/1.1 200 OK")
                        writer.println("Content-Type: application/json")
                        writer.println("Connection: close")
                        writer.println()
                        writer.println(textResponse)
                        
                        client.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        isRunning = false
        serverSocket?.close()
        lmEngine?.close()
        super.onDestroy()
    }
}
