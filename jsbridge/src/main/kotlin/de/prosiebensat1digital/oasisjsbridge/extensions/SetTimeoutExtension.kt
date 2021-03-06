/*
 * Copyright (C) 2019 ProSiebenSat1.Digital GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.prosiebensat1digital.oasisjsbridge.extensions

import de.prosiebensat1digital.oasisjsbridge.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import timber.log.Timber

// Support for setTimeout() and setInterval()
class SetTimeoutExtension(private val jsBridge: JsBridge) {
    private val activeTimers = mutableSetOf<String>()
    private var timerCounter = 0

    init {
        JsValue.fromNativeFunction2(jsBridge) { cb: () -> Unit, msecs: Long -> setTimeoutHelper(cb, msecs, false) }
            .assignToGlobal("setTimeout")

        JsValue.fromNativeFunction1(jsBridge) { id: String? -> clearTimeoutHelper(id) }
            .assignToGlobal("clearTimeout")

        JsValue.fromNativeFunction2(jsBridge) { cb: () -> Unit, msecs: Long -> setTimeoutHelper(cb, msecs, true) }
            .assignToGlobal("setInterval")

        JsValue.fromNativeFunction1(jsBridge) { id: String? -> clearTimeoutHelper(id) }
            .assignToGlobal("clearInterval")
    }

    fun release() {
    }

    private fun setTimeoutHelper(cb: () -> Unit, msecs: Long, repeat: Boolean): String {
        val id = "timerId${timerCounter++}"
        activeTimers.add(id)

        jsBridge.launch {
            do {
                delay(msecs)

                if (!activeTimers.contains(id)) {
                    Timber.d("setTimeoutHelper($msecs, $repeat) - id = $id - callback not executed because the timeout was cancelled!")
                    return@launch
                }
                if (!repeat) {
                    activeTimers.remove(id)
                }
                try {
                    cb()
                    jsBridge.processPromiseQueue()
                } catch (t: Throwable) {
                    Timber.e("Error while calling setTimeout JS callback: $t")
                    jsBridge.notifyErrorListeners(JsBridgeError.JsCallbackError(t))
                }
            } while (repeat)
        }

        return id
    }

    private fun clearTimeoutHelper(id: String?) {
        id?.let { activeTimers.remove(id) }
    }
}

