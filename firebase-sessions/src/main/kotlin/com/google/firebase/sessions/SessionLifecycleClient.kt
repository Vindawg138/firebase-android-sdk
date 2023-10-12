/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.sessions

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import java.util.concurrent.LinkedBlockingDeque

/**
 * Client for binding to the [SessionLifecycleService]. This client will receive updated sessions
 * through a callback whenever a new session is generated by the service, or after the initial
 * binding.
 *
 * Note: this client will be connected in every application process that uses Firebase, and is
 * intended to maintain that connection for the lifetime of the process.
 */
internal object SessionLifecycleClient {
  const val TAG = "SessionLifecycleClient"

  /**
   * The maximum number of messages that we should queue up for delivery to the
   * [SessionLifecycleService] in the event that we have lost the connection.
   */
  const val MAX_QUEUED_MESSAGES = 20

  private var service: Messenger? = null
  private var serviceBound: Boolean = false
  private val queuedMessages = LinkedBlockingDeque<Message>(MAX_QUEUED_MESSAGES)
  private var curSessionId: String = ""

  /**
   * The callback class that will be used to receive updated session events from the
   * [SessionLifecycleService].
   */
  internal class ClientUpdateHandler() : Handler(Looper.getMainLooper()) {
    override fun handleMessage(msg: Message) {
      when (msg.what) {
        SessionLifecycleService.SESSION_UPDATED ->
          handleSessionUpdate(
            msg.data?.getString(SessionLifecycleService.SESSION_UPDATE_EXTRA) ?: ""
          )
        else -> {
          Log.w(TAG, "Received unexpected event from the SessionLifecycleService: $msg")
          super.handleMessage(msg)
        }
      }
    }

    fun handleSessionUpdate(sessionId: String) {
      Log.i(TAG, "Session update received: $sessionId")
      curSessionId = sessionId
    }
  }

  /** The connection object to the [SessionLifecycleService]. */
  private val serviceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(className: ComponentName, serviceBinder: IBinder) {
        Log.i(TAG, "Connected to SessionLifecycleService. Queue size ${queuedMessages.size}")
        service = Messenger(serviceBinder)
        serviceBound = true
        sendLifecycleEvents(drainQueue())
      }

      override fun onServiceDisconnected(className: ComponentName) {
        Log.i(TAG, "Disconnected from SessionLifecycleService")
        service = null
        serviceBound = false
      }
    }

  /**
   * Binds to the [SessionLifecycleService] and passes a callback [Messenger] that will be used to
   * relay session updates to this client.
   */
  fun bindToService(appContext: Context): Unit {
    Intent(appContext, SessionLifecycleService::class.java).also { intent ->
      Log.i(TAG, "Binding service to application.")
      // This is necessary for the onBind() to be called by each process
      intent.setAction(android.os.Process.myPid().toString())
      intent.putExtra(
        SessionLifecycleService.CLIENT_CALLBACK_MESSENGER,
        Messenger(ClientUpdateHandler())
      )
      appContext.bindService(
        intent,
        serviceConnection,
        Context.BIND_IMPORTANT or Context.BIND_AUTO_CREATE
      )
    }
  }

  /**
   * Should be called when any activity in this application process goes to the foreground. This
   * will relay the event to the [SessionLifecycleService] where it can make the determination of
   * whether or not this foregrounding event should result in a new session being generated.
   */
  fun foregrounded(activity: Activity): Unit {
    sendLifecycleEvent(SessionLifecycleService.FOREGROUNDED)
  }

  /**
   * Should be called when any activity in this application process goes from the foreground to the
   * background. This will relay the event to the [SessionLifecycleService] where it will be used to
   * determine when a new session should be generated.
   */
  fun backgrounded(activity: Activity): Unit {
    sendLifecycleEvent(SessionLifecycleService.BACKGROUNDED)
  }

  /**
   * Sends a message to the [SessionLifecycleService] with the given event code. This will
   * potentially also send any messages that have been queued up but not successfully delivered to
   * thes service since the previous send.
   */
  private fun sendLifecycleEvent(messageCode: Int) {
    val allMessages = drainQueue()
    allMessages.add(Message.obtain(null, messageCode, 0, 0))
    sendLifecycleEvents(allMessages)
  }

  /**
   * Sends lifecycle events to the [SessionLifecycleService]. This will only send the latest
   * FOREGROUND and BACKGROUND events to the service that are included in the given list. Running
   * through the full backlog of messages is not useful since the service only cares about the
   * current state and transitions from background -> foreground.
   */
  private fun sendLifecycleEvents(messages: List<Message>) {
    val latest = ArrayList<Message>()
    getLatestByCode(messages, SessionLifecycleService.BACKGROUNDED)?.let { latest.add(it) }
    getLatestByCode(messages, SessionLifecycleService.FOREGROUNDED)?.let { latest.add(it) }
    latest.sortBy { it.getWhen() }

    latest.forEach { sendMessageToServer(it) }
  }

  /** Sends the given [Message] to the [SessionLifecycleService]. */
  private fun sendMessageToServer(msg: Message) {
    if (service != null) {
      try {
        Log.i(TAG, "Sending lifecycle ${msg.what} at time ${msg.getWhen()} to service")
        service?.send(msg)
      } catch (e: RemoteException) {
        Log.e(TAG, "Unable to deliver message: ${msg.what}")
        queueMessage(msg)
      }
    } else {
      queueMessage(msg)
    }
  }

  /**
   * Queues the given [Message] up for delivery to the [SessionLifecycleService] once the connection
   * is established.
   */
  private fun queueMessage(msg: Message) {
    if (queuedMessages.offer(msg)) {
      Log.i(TAG, "Queued message ${msg.what} at ${msg.getWhen()}")
    } else {
      Log.i(TAG, "Failed to enqueue message ${msg.what} at ${msg.getWhen()}. Dropping.")
    }
  }

  /** Drains the queue of messages into a new list in a thread-safe manner. */
  private fun drainQueue(): MutableList<Message> {
    val messages = ArrayList<Message>()
    queuedMessages.drainTo(messages)
    return messages
  }

  /** Gets the message in the given list with the given code that has the latest timestamp. */
  private fun getLatestByCode(messages: List<Message>, msgCode: Int): Message? =
    messages.filter { it.what == msgCode }.maxByOrNull { it.getWhen() }
}
