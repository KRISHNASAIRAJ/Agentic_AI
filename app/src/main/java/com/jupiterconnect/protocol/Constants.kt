package com.jupiterconnect.protocol

/** SmartXonnect GATT layer - verified against TVS Connect and TVSRide. */
object Gatt {
    const val SERVICE = "5456534d-5647-5341-5342-454e544f5251"
    const val WRITE = "00005352-0000-1000-8000-00805f9b34fb"
    const val READ = "00005354-0000-1000-8000-00805f9b34fb"
    const val CCCD = "00002902-0000-1000-8000-00805f9b34fb"
    const val REQUESTED_MTU = 512
}

object OutboundId {
    const val MOBILE_DATA = 0x4A
    const val NAVIGATION_CONTROL = 0x4E
    const val NAVIGATION_TEXT = 0x4F
    const val RIDER_NAME = 0x52
}

object InboundId {
    const val SPEEDOMETER_1 = 0x10
    const val SERVICE = 0x11
    const val ENGINE = 0x18
    const val ECONOMY = 0x19
}

/** A device seen during a BLE scan. Clusters are flagged but never hidden. */
data class ScannedDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val isCluster: Boolean,
)

/** One decoded snapshot of the cluster. All values are plain types for the UI. */
data class Telemetry(
    val connected: Boolean = false,
    val speedKmh: Int = 0,
    val odometerKm: Double = 0.0,
    val fuelBars: Int = 0,
    val rpm: Int = 0,
    val batteryVoltage: Double = 0.0,
    val engineTempC: Int = 0,
    val intakeTempC: Int = 0,
    val distanceToEmptyKm: Int = 0,
    val averageFuelEconomy: Int = 0,

    // 0x10 primary telemetry
    val averageSpeedKmh: Int = 0,
    val clusterTopSpeedKmh: Int = 0,
    val powerMode: Boolean = false,
    val throttlePosition: Int = 0,
    val backlightMode: Int = 0,
    val ignitionOn: Boolean = false,
    val zeroToSixtyRaw: Int = 0,
    val tripMeterKm: Double = 0.0,

    // 0x11 service / diagnostics
    val fuelSensorFault: Boolean = false,
    val vehicleState: Int = 0,
    val serviceDue: Boolean = false,
    val issMode: Int = 0,
    val softwareVersion: Int = 0,
    val milBlinkCode: Int = 0,
    val vehicleModel: Int = 0,
    val milOn: Boolean = false,
    val isgBlinkCode: Int = 0,
    val turnIndicator: Int = 0,
    val connectorStatus: Int = 0,
    val clusterTheme: Int = 0,

    // 0x18 engine telemetry
    val engineLoad: Int = 0,
    val fuelInjectionTime: Int = 0,
    val manifoldPressure: Int = 0,
    val barometricPressureRaw: Int = 0,
    val barometricPressure: Int = 0,
    val engineRunTime: Int = 0,
    val distanceTravelledKm: Int = 0,
    val fuelInjectionVolume: Int = 0,

    // 0x19 economy
    val issDuration: Int = 0,
    val otaStatus: Int = 0,
    val instantEconomy: Int = 0,
    val issCount: Int = 0,

    val frameCount: Long = 0,
    val lastFrame: String = "-",
    val address: String = "",
    val error: String? = null,
)
