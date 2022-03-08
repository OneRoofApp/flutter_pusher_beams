package com.pusher.pusher_beams

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.NonNull
import com.google.firebase.messaging.RemoteMessage
import com.pusher.pushnotifications.*
import com.pusher.pushnotifications.auth.AuthData
import com.pusher.pushnotifications.auth.AuthDataGetter
import com.pusher.pushnotifications.auth.BeamsTokenProvider
import io.flutter.Log

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

import io.flutter.plugin.common.PluginRegistry.NewIntentListener
import org.json.JSONObject
import org.json.JSONTokener
import java.lang.IllegalStateException
import java.util.*
import kotlin.collections.HashMap

/** PusherBeamsPlugin */
class PusherBeamsPlugin : FlutterPlugin, Messages.PusherBeamsApi, ActivityAware, NewIntentListener {
    private lateinit var context: Context
    private var alreadyInterestsListener: Boolean = false
    private var currentActivity: Activity? = null

    private var onMessageOpenedAppCallbackId: String? = null
    private var data: kotlin.collections.Map<String, kotlin.Any?>? = null

    private lateinit var callbackHandlerApi: Messages.CallbackHandlerApi

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        Messages.PusherBeamsApi.setup(flutterPluginBinding.binaryMessenger, this)

        context = flutterPluginBinding.applicationContext
        callbackHandlerApi = Messages.CallbackHandlerApi(flutterPluginBinding.binaryMessenger)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        Messages.PusherBeamsApi.setup(binding.binaryMessenger, null)
        callbackHandlerApi = Messages.CallbackHandlerApi(binding.binaryMessenger)
    }

    override fun onNewIntent(intent: Intent?): Boolean {
        handleIntent(context, intent!!)

        if(onMessageOpenedAppCallbackId != null) {
            callbackHandlerApi.handleCallback(
                onMessageOpenedAppCallbackId,
                "onMessageOpenedApp",
                listOf(data)
            ) {
                Log.d(this.toString(), "App opened from background with data: $data")
            }
        }

        return false
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.currentActivity = binding.activity;
        binding.addOnNewIntentListener(this)
        handleIntent(context, binding.activity.intent)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        binding.addOnNewIntentListener(this)
        handleIntent(context, binding.activity.intent)
    }

    override fun onDetachedFromActivityForConfigChanges() {}

    override fun onDetachedFromActivity() {
        this.currentActivity = null;
    }

    private fun handleIntent(context: Context, intent: Intent) {
        val extras = intent.extras
        if (extras != null) {
            Log.d(this.toString(), "Got extras: $extras")
            data = bundleToMap(extras.getString("info"))
            Log.d(this.toString(), "Got initial data: $data")
        }
    }

    override fun start(instanceId: kotlin.String) {
        PushNotifications.start(this.context, instanceId)
        Log.d(this.toString(), "PusherBeams started with $instanceId instanceId")
    }

    override fun getInitialMessage(result: Messages.Result<kotlin.collections.Map<String, kotlin.Any?>>) {
        Log.d(this.toString(), "Returning initial data: $data")
        result.success(data)
    }

    override fun addDeviceInterest(interest: kotlin.String) {
        try {
            PushNotifications.addDeviceInterest(interest)
            Log.d(this.toString(), "Added device to interest: $interest")
        } catch(e: IllegalStateException) {
            Log.w(this.toString(), "Call to addDeviceInterest has no effect because it must have been called after PushNotifications.start")
        }
    }

    override fun removeDeviceInterest(interest: String) {
        try {
            PushNotifications.removeDeviceInterest(interest)
            Log.d(this.toString(), "Removed device to interest: $interest")
        } catch(e: IllegalStateException) {
            Log.w(this.toString(), "Call to removeDeviceInterest has no effect because it must have been called after PushNotifications.start")
        }
    }

    override fun getDeviceInterests(): kotlin.collections.List<String> {
        return try {
            PushNotifications.getDeviceInterests().toList()
        } catch(e: IllegalStateException) {
            Log.w(this.toString(), "Call to getDeviceInterests has no effect because it must have been called after PushNotifications.start")
            Collections.emptyList()
        }
    }

    override fun setDeviceInterests(interests: kotlin.collections.List<String>) {
        try {
            PushNotifications.setDeviceInterests(interests.toSet())
            Log.d(this.toString(), "$interests added to device")
        } catch(e: IllegalStateException) {
            Log.w(this.toString(), "Call to setDeviceInterests has no effect because it must have been called after PushNotifications.start")
        }
    }

    override fun clearDeviceInterests() {
        try {
            PushNotifications.clearDeviceInterests()
            Log.d(this.toString(), "Cleared device interests")
        } catch(e: IllegalStateException) {
            Log.w(this.toString(), "Call to clearDeviceInterests has no effect because it must have been called after PushNotifications.start")
        }
    }

    override fun onInterestChanges(callbackId: String) {
        if (!alreadyInterestsListener) {
            try {
                PushNotifications.setOnDeviceInterestsChangedListener(object :
                    SubscriptionsChangedListener {
                    override fun onSubscriptionsChanged(interests: Set<String>) {
                        callbackHandlerApi.handleCallback(
                            callbackId,
                            "onInterestChanges",
                            listOf(interests.toList()),
                            Messages.CallbackHandlerApi.Reply {
                                Log.d(this.toString(), "interests changed $interests")
                            })
                    }
                })
            } catch(e: IllegalStateException) {
                Log.w(this.toString(), "Call to onInterestChanges has no effect because it must have been called after PushNotifications.start")
            }
        }
    }

    override fun setUserId(
        userId: String,
        provider: Messages.BeamsAuthProvider,
        callbackId: String
    ) {
        val tokenProvider = BeamsTokenProvider(
            provider.authUrl,
            object : AuthDataGetter {
                override fun getAuthData(): AuthData {
                    return AuthData(
                        headers = provider.headers,
                        queryParams = provider.queryParams
                    )
                }
            }
        )
        try {
            PushNotifications.setUserId(
                userId,
                tokenProvider,
                object : BeamsCallback<Void, PusherCallbackError> {
                    override fun onFailure(error: PusherCallbackError) {
                        callbackHandlerApi.handleCallback(
                            callbackId,
                            "setUserId",
                            listOf(error.message),
                            Messages.CallbackHandlerApi.Reply {
                                Log.d(this.toString(), "Failed to set Authentication to device")
                            })
                    }

                    override fun onSuccess(vararg values: Void) {
                        callbackHandlerApi.handleCallback(
                            callbackId,
                            "setUserId",
                            listOf(null),
                            Messages.CallbackHandlerApi.Reply {
                                Log.d(this.toString(), "Device authenticated with $userId")
                            })
                    }
                }
            )
        } catch(e: IllegalStateException) {
            Log.w(this.toString(), "Call to setUserId has no effect because it must have been called after PushNotifications.start")
        }
    }

    override fun clearAllState() {
        try {
            PushNotifications.clearAllState()
        } catch(e: IllegalStateException) {
            Log.w(this.toString(), "Call to clearAllState has no effect because it must have been called after PushNotifications.start")
        }
    }

    override fun onMessageReceivedInTheForeground(callbackId: String) {
        try {
            currentActivity?.let { activity ->
                PushNotifications.setOnMessageReceivedListenerForVisibleActivity(
                    activity,
                    object : PushNotificationReceivedListener {
                        override fun onMessageReceived(remoteMessage: RemoteMessage) {
                            activity.runOnUiThread {
                                val pusherMessage = remoteMessage.toPusherMessage()
                                callbackHandlerApi.handleCallback(
                                    callbackId,
                                    "onMessageReceivedInTheForeground",
                                    listOf(pusherMessage)
                                ) {
                                    Log.d(this.toString(), "Message received: $pusherMessage")
                                }
                            }
                        }
                    })
            }
        } catch(e: IllegalStateException) {
            Log.w(this.toString(), "Call to onMessageReceivedInTheForeground has no effect because it must have been called after PushNotifications.start")
        }
    }

    override fun onMessageOpenedApp(callbackId: String) {
        onMessageOpenedAppCallbackId = callbackId
    }

    override fun stop() {
        try {
            PushNotifications.stop()
        } catch(e: IllegalStateException) {
            Log.w(this.toString(), "Call to stop has no effect because it must have been called after PushNotifications.start")
        }
    }

    private fun bundleToMap(info: kotlin.String?): kotlin.collections.Map<kotlin.String, kotlin.Any?>? {
        if (info == null)
            return null

        val map: MutableMap<String, kotlin.Any?> = HashMap<String, kotlin.Any?>()
        val infoJson = JSONTokener(info).nextValue() as JSONObject

        val iterator = infoJson.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            map[key] = infoJson.get(key)
        }
        return map
    }
}

fun RemoteMessage.toPusherMessage() = mapOf(
    "title" to notification?.title,
    "body" to notification?.body,
    "data" to data
)