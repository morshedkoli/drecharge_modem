package com.dRecharge.modem

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dRecharge.modem.helper.Session
import com.dRecharge.modem.ussd.USSDController
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity(), EventChannel.StreamHandler {
    private var ussdEventSink: EventChannel.EventSink? = null
    private var permissionResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            METHOD_CHANNEL
        ).setMethodCallHandler(::handleMethodCall)

        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            EVENT_CHANNEL
        ).setStreamHandler(this)
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        ussdEventSink = events
    }

    override fun onCancel(arguments: Any?) {
        ussdEventSink = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS_CODE) {
            return
        }

        val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        permissionResult?.success(
            mapOf(
                "granted" to allGranted,
                "permissions" to requiredPermissions()
            )
        )
        permissionResult = null
    }

    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getInitialState" -> result.success(buildInitialState())
            "requestPermissions" -> requestPermissions(result)
            "isAccessibilityEnabled" -> result.success(
                USSDController.isAccessiblityServicesEnable(applicationContext)
            )
            "openAccessibilitySettings" -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                result.success(true)
            }
            "saveDomain" -> {
                val domain = call.argument<String>("domain").orEmpty()
                val session = Session(this)
                session.setData(Session.API_DOMAIN_LINK, domain)
                session.setBooleanData(Session.IS_DOMAIN_VALIED, domain.isNotBlank())
                result.success(buildInitialState())
            }
            "saveInterval" -> {
                val interval = call.argument<String>("interval").orEmpty()
                Session(this).setData(Session.TIME_INTERVAL, interval)
                result.success(buildInitialState())
            }
            "saveSimSettings" -> {
                saveSimSettings(call)
                result.success(buildInitialState())
            }
            "saveSimService" -> {
                saveSimService(call)
                result.success(buildInitialState())
            }
            "invokeUssd" -> {
                invokeUssd(call)
                result.success(true)
            }
            "sendUssd" -> {
                val text = call.argument<String>("text").orEmpty()
                USSDController.getInstance(applicationContext)
                    .send(text, object : USSDController.CallbackMessage {
                        override fun responseMessage(message: String) {
                            emitEvent("ussd_response", message)
                        }
                    })
                result.success(true)
            }
            "sendUssdPin" -> {
                val text = call.argument<String>("text").orEmpty()
                USSDController.getInstance(applicationContext)
                    .sendPin(text, object : USSDController.CallbackMessage {
                        override fun responseMessage(message: String) {
                            emitEvent("ussd_response", message)
                        }
                    })
                result.success(true)
            }
            "cancelUssd" -> {
                USSDController.getInstance(applicationContext).cancel()
                result.success(true)
            }
            "closeApp" -> {
                finishAffinity()
                result.success(true)
            }
            else -> result.notImplemented()
        }
    }

    private fun requestPermissions(result: MethodChannel.Result) {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            result.success(
                mapOf(
                    "granted" to true,
                    "permissions" to requiredPermissions()
                )
            )
            return
        }

        permissionResult = result
        ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS_CODE)
    }

    private fun buildInitialState(): Map<String, Any?> {
        val session = Session(this)
        val simCards = readSimCards(session)

        return mapOf(
            "appName" to getString(R.string.app_name),
            "permissionsGranted" to requiredPermissions().all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            },
            "accessibilityEnabled" to USSDController.isAccessiblityServicesEnable(applicationContext),
            "serviceCatalog" to serviceCatalog(),
            "simCards" to simCards,
            "settings" to mapOf(
                "domain" to session.getData(Session.API_DOMAIN_LINK),
                "isDomainValid" to session.isDomainValid(),
                "interval" to session.getData(Session.TIME_INTERVAL),
                "sim1" to buildSimState(
                    session = session,
                    slotKey = "sim1",
                    savedId = session.getData(Session.SIM1_ID),
                    fallbackNumber = simCards.getOrNull(0)?.get("number") as? String ?: ""
                ),
                "sim2" to buildSimState(
                    session = session,
                    slotKey = "sim2",
                    savedId = session.getData(Session.SIM2_ID),
                    fallbackNumber = simCards.getOrNull(1)?.get("number") as? String ?: ""
                )
            )
        )
    }

    private fun buildSimState(
        session: Session,
        slotKey: String,
        savedId: String,
        fallbackNumber: String
    ): Map<String, Any?> {
        val isSim1 = slotKey == "sim1"
        return mapOf(
            "valid" to if (isSim1) session.isSim1Valid() else session.isSim2Valid(),
            "id" to savedId,
            "number" to if (isSim1) {
                session.getData(Session.SIM1_NUMBER).ifBlank { fallbackNumber }
            } else {
                session.getData(Session.SIM2_NUMBER).ifBlank { fallbackNumber }
            },
            "pin" to if (isSim1) session.getData(Session.SIM1_PIN) else session.getData(Session.SIM2_PIN),
            "minBalance" to if (isSim1) session.getData(Session.SIM1_MIN_BAL) else session.getData(Session.SIM2_MIN_BAL),
            "time" to if (isSim1) session.getData(Session.SIM1_TIME) else session.getData(Session.SIM2_TIME),
            "serviceIndex" to if (isSim1) session.getData(Session.SIM1_SERVICE) else session.getData(Session.SIM2_SERVICE),
            "serviceName" to if (isSim1) session.getData(Session.SIM1_SERVICE_NAME) else session.getData(Session.SIM2_SERVICE_NAME),
            "serviceCode" to if (isSim1) session.getData(Session.SIM1_SERVICE_CODE) else session.getData(Session.SIM2_SERVICE_CODE)
        )
    }

    private fun readSimCards(session: Session): List<Map<String, Any>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return emptyList()
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val subscriptionManager = SubscriptionManager.from(this)
        val subscriptions = subscriptionManager.activeSubscriptionInfoList ?: return emptyList()

        return subscriptions.sortedBy { it.simSlotIndex }.mapIndexed { index, info ->
            val number = info.number?.replace("+88", "").orEmpty()
                .ifBlank { if (index == 0) session.getData(Session.SIM1_NUMBER) else session.getData(Session.SIM2_NUMBER) }

            if (index == 0) {
                session.setData(Session.SIM1_ID, info.simSlotIndex.toString())
            } else if (index == 1) {
                session.setData(Session.SIM2_ID, info.simSlotIndex.toString())
            }

            mapOf(
                "slotIndex" to info.simSlotIndex,
                "carrierName" to info.carrierName.toString(),
                "number" to number
            )
        }
    }

    private fun serviceCatalog(): List<Map<String, String>> {
        return listOf(
            serviceEntry("Select One", ""),
            serviceEntry("Grameen", "GP"),
            serviceEntry("Robi", "RB"),
            serviceEntry("Airtel", "AT"),
            serviceEntry("bLink", "BL"),
            serviceEntry("Taletalk", "TT"),
            serviceEntry("bKash-Load", "GP,RB,AT,BL,TT"),
            serviceEntry("Nagad-Load", "GP,RB,AT,BL,TT"),
            serviceEntry("bKash-Agent-SIM", "BK"),
            serviceEntry("bKash-Personal-SIM", "BKA,BKS"),
            serviceEntry("Roket-Agent-SIM", "RK"),
            serviceEntry("Roket-Personal-SIM", "RKA,RKS"),
            serviceEntry("Nagad-Agent-SIM", "NG"),
            serviceEntry("Nagad-Personal-SIM", "NGA,NGS")
        )
    }

    private fun serviceEntry(name: String, code: String): Map<String, String> {
        return mapOf("name" to name, "code" to code)
    }

    private fun saveSimSettings(call: MethodCall) {
        val slotKey = call.argument<String>("slotKey").orEmpty()
        val number = call.argument<String>("number").orEmpty()
        val pin = call.argument<String>("pin").orEmpty()
        val minBalance = call.argument<String>("minBalance").orEmpty()
        val time = call.argument<String>("time").orEmpty()
        val session = Session(this)

        if (slotKey == "sim1") {
            session.SetSim1Info(true, number, pin, minBalance, time)
        } else {
            session.SetSim2Info(true, number, pin, minBalance, time)
        }
    }

    private fun saveSimService(call: MethodCall) {
        val slotKey = call.argument<String>("slotKey").orEmpty()
        val serviceIndex = call.argument<String>("serviceIndex").orEmpty()
        val serviceName = call.argument<String>("serviceName").orEmpty()
        val serviceCode = call.argument<String>("serviceCode").orEmpty()
        val session = Session(this)

        if (slotKey == "sim1") {
            session.setData(Session.SIM1_SERVICE, serviceIndex)
            session.setData(Session.SIM1_SERVICE_NAME, serviceName)
            session.setData(Session.SIM1_SERVICE_CODE, serviceCode)
        } else {
            session.setData(Session.SIM2_SERVICE, serviceIndex)
            session.setData(Session.SIM2_SERVICE_NAME, serviceName)
            session.setData(Session.SIM2_SERVICE_CODE, serviceCode)
        }
    }

    private fun invokeUssd(call: MethodCall) {
        val code = call.argument<String>("code").orEmpty()
        val simSlot = call.argument<Int>("simSlot") ?: 0
        val map = hashMapOf(
            "KEY_LOGIN" to hashSetOf("running...", "waiting", "loading", "esperando"),
            "KEY_ERROR" to hashSetOf("problema", "problem", "error", "null")
        )

        USSDController.getInstance(applicationContext)
            .callUSSDInvoke(code, simSlot, map, object : USSDController.CallbackInvoke {
                override fun responseInvoke(message: String) {
                    emitEvent("ussd_invoke", message)
                }

                override fun over(message: String) {
                    emitEvent("ussd_over", message)
                }
            })
    }

    private fun emitEvent(type: String, message: String) {
        ussdEventSink?.success(
            mapOf(
                "type" to type,
                "message" to message
            )
        )
    }

    private fun requiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions
    }

    companion object {
        private const val METHOD_CHANNEL = "modem/native"
        private const val EVENT_CHANNEL = "modem/events"
        private const val REQUEST_PERMISSIONS_CODE = 5012
    }
}
