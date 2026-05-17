package com.example.appfightsmart

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

private data class CalPlan(val label: String, val fileLabel: String, val move: String, val height: Int?)

private val calPlans = listOf(
    CalPlan("Jab 80 cm", "jab_80cm", "Jab", 80),
    CalPlan("Jab 100 cm", "jab_100cm", "Jab", 100),
    CalPlan("Jab 120 cm", "jab_120cm", "Jab", 120),
    CalPlan("Jab 140 cm", "jab_140cm", "Jab", 140),
    CalPlan("Cross 80 cm", "cross_80cm", "Cross", 80),
    CalPlan("Cross 100 cm", "cross_100cm", "Cross", 100),
    CalPlan("Cross 120 cm", "cross_120cm", "Cross", 120),
    CalPlan("Cross 140 cm", "cross_140cm", "Cross", 140),
    CalPlan("Hook 80 cm", "hook_80cm", "Hook", 80),
    CalPlan("Hook 100 cm", "hook_100cm", "Hook", 100),
    CalPlan("Hook 120 cm", "hook_120cm", "Hook", 120),
    CalPlan("Hook 140 cm", "hook_140cm", "Hook", 140),
    CalPlan("Jab all heights", "jab_all_heights", "Jab", null),
    CalPlan("Cross all heights", "cross_all_heights", "Cross", null),
    CalPlan("Hook all heights", "hook_all_heights", "Hook", null)
)

private data class CalStep(
    val type: String,
    val move: String,
    val force: String,
    val height: Int,
    val rep: Int,
    val totalReps: Int,
    val seconds: Int,
    val instruction: String
)

private data class ParsedFrame(
    val frameType: String,
    val accX: Double? = null, val accY: Double? = null, val accZ: Double? = null, val accMag: Double? = null,
    val temp: Double? = null,
    val gyroX: Double? = null, val gyroY: Double? = null, val gyroZ: Double? = null, val gyroMag: Double? = null,
    val angleX: Double? = null, val angleY: Double? = null, val angleZ: Double? = null, val tiltMag: Double? = null,
    val yawCompass: Double? = null,
    val magX: Int? = null, val magY: Int? = null, val magZ: Int? = null, val magMag: Double? = null,
    val magneticCompass: Double? = null,
    val q0: Double? = null, val q1: Double? = null, val q2: Double? = null, val q3: Double? = null,
    val motionScore: Double? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(navController: NavHostController, bluetoothManager: BluetoothManager) {
    val context = LocalContext.current
    val lock = remember { Any() }
    var plan by remember { mutableStateOf<CalPlan?>(calPlans.first()) }
    val steps = remember(plan) { plan?.let { buildSteps(it) }.orEmpty() }
    var writer by remember { mutableStateOf<BufferedWriter?>(null) }
    var outputPath by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var stepStart by remember { mutableLongStateOf(0L) }
    var stepRows by remember { mutableIntStateOf(0) }
    var totalRows by remember { mutableIntStateOf(0) }
    var liveFramesSeen by remember { mutableIntStateOf(0) }
    var idleFramesSeen by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf("Waiting for sensor data...") }
    var warning by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val step = steps.getOrNull(index)
    val currentRecording by rememberUpdatedState(recording)
    val currentStep by rememberUpdatedState(step)

    LaunchedEffect(Unit) {
        delay(250)
        try {
            bluetoothManager.enableAnglesAndMagAt100Hz()
            last = "Sensor stream command sent. Waiting for live frames..."
        } catch (e: Exception) {
            warning = "Could not send sensor stream command: ${e.message}"
        }
    }

    fun closeFile() = synchronized(lock) { writer?.flush(); writer?.close(); writer = null }

    fun newFile(selected: CalPlan): BufferedWriter {
        val dir = File(context.getExternalFilesDir(null), "calibration")
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "fight_smart_calibration_${selected.fileLabel}_$stamp.csv")
        outputPath = file.absolutePath
        return BufferedWriter(FileWriter(file, false)).also {
            it.write("# FightSmart maximum raw punch calibration\n")
            it.write("# Calibration plan: ${selected.label}\n")
            it.write("# TEST MODE: only Jab 80 cm is enabled in the UI.\n")
            it.write("# Bag height 160 cm; bag elevation corrected to about 40+ cm; sensor about 80 cm from bag bottom.\n")
            it.write("# Stop before each punch; after punching let the bag swing until recording ends.\n")
            it.write(header()); it.newLine(); it.flush()
        }
    }

    fun start(selected: CalPlan) {
        closeFile()
        try { bluetoothManager.enableAnglesAndMagAt100Hz() } catch (_: Exception) {}
        writer = newFile(selected)
        started = true
        finished = false
        index = 0
        stepRows = 0
        totalRows = 0
        idleFramesSeen = 0
        warning = null
        error = null
        last = "File created. Live frames seen: $liveFramesSeen. Start the first step."
    }

    fun writeMarker(marker: String, active: CalStep?) {
        active ?: return
        val emptyParsed = List(31) { "" }
        val raw = List(RAW_BYTES) { "" }
        val elapsed = if (stepStart > 0L) System.currentTimeMillis() - stepStart else 0L
        val row = listOf(now(), (index + 1).toString(), steps.size.toString(), csv(active.type), csv(active.move), csv(active.force), active.height.toString(), active.rep.toString(), active.totalReps.toString(), elapsed.toString(), "0", csv(marker), csv("")) + emptyParsed + raw
        synchronized(lock) {
            try {
                writer?.write(row.joinToString(","))
                writer?.newLine()
                writer?.flush()
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    fun writeFrame(bytes: ByteArray, active: CalStep, parsed: ParsedFrame) {
        val elapsed = if (stepStart > 0L) System.currentTimeMillis() - stepStart else 0L
        val rawBytes = (0 until RAW_BYTES).map { bytes.getOrNull(it)?.let { b -> (b.toInt() and 0xFF).toString() }.orEmpty() }
        val parsedValues = listOf(
            parsed.accX.f6(), parsed.accY.f6(), parsed.accZ.f6(), parsed.accMag.f6(), parsed.temp.f6(),
            parsed.gyroX.f6(), parsed.gyroY.f6(), parsed.gyroZ.f6(), parsed.gyroMag.f6(),
            parsed.angleX.f6(), parsed.angleY.f6(), parsed.angleZ.f6(), parsed.tiltMag.f6(), parsed.yawCompass.f6(),
            parsed.magX?.toString().orEmpty(), parsed.magY?.toString().orEmpty(), parsed.magZ?.toString().orEmpty(), parsed.magMag.f6(), parsed.magneticCompass.f6(),
            parsed.q0.f6(), parsed.q1.f6(), parsed.q2.f6(), parsed.q3.f6(),
            "", "", "", "", "", "", "", parsed.motionScore.f6()
        )
        val row = listOf(now(), (index + 1).toString(), steps.size.toString(), csv(active.type), csv(active.move), csv(active.force), active.height.toString(), active.rep.toString(), active.totalReps.toString(), elapsed.toString(), bytes.size.toString(), csv(parsed.frameType), csv(bytes.hex())) + parsedValues + rawBytes
        synchronized(lock) {
            try {
                writer?.write(row.joinToString(","))
                writer?.newLine()
                writer?.flush()
                totalRows++
            } catch (e: Exception) {
                error = e.message ?: "Unknown file write error"
                recording = false
            }
        }
    }

    DisposableEffect(bluetoothManager) {
        val listener: (ByteArray) -> Unit = { bytes ->
            liveFramesSeen++
            val parsed = parseFrame(bytes)
            if (currentRecording) {
                val active = currentStep
                if (active != null) {
                    writeFrame(bytes, active, parsed)
                    stepRows++
                    last = "Recording: ${parsed.frameType} | step rows: $stepRows | total rows: $totalRows | live frames: $liveFramesSeen"
                }
            } else {
                idleFramesSeen++
                if (idleFramesSeen == 1 || idleFramesSeen % 40 == 0) {
                    last = "Live sensor frames arriving: $liveFramesSeen | last: ${parsed.frameType}"
                }
            }
        }
        bluetoothManager.addDataListener(listener)
        onDispose { bluetoothManager.removeDataListener(listener) }
    }

    DisposableEffect(Unit) { onDispose { closeFile() } }

    LaunchedEffect(recording, secondsLeft) {
        if (recording && secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
            if (secondsLeft <= 0) {
                val rowsThisStep = stepRows
                recording = false
                writeMarker("STEP_END", step)
                if (rowsThisStep == 0) {
                    warning = "This step recorded ZERO sensor frames. Do not continue calibration until live frames are visible."
                    last = "ZERO frames recorded in this step. Check sensor stream/connection."
                } else {
                    warning = null
                }
                if (index >= steps.lastIndex) {
                    finished = true
                    closeFile()
                } else {
                    index++
                }
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Sensor Calibration") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Maximum raw punch calibration", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Test mode: only Jab 80 cm is enabled. Check live frames before recording.", textAlign = TextAlign.Center)
            LiveFrameCard(liveFramesSeen, idleFramesSeen, last, warning, error)
            if (!started) {
                InfoCard()
                PlanSelector(plan) { plan = it }
                Button(enabled = plan == calPlans.first(), onClick = { plan?.let { start(it) } }) { Text("Create file and start Jab 80 cm test") }
            } else if (finished) {
                Text("Calibration finished.", fontWeight = FontWeight.Bold)
                Text("Saved file:", fontWeight = FontWeight.Bold)
                Text(outputPath ?: "Unknown path", textAlign = TextAlign.Center)
                Text("Rows written: $totalRows", fontWeight = FontWeight.Bold)
                Button(onClick = { started = false; finished = false; plan = calPlans.first(); outputPath = null; index = 0; stepRows = 0; totalRows = 0; warning = null; error = null }) { Text("Choose another calibration") }
            } else if (step != null) {
                Text("Plan: ${plan?.label ?: "--"} | Steps: ${steps.size}", fontWeight = FontWeight.Bold)
                StepCard(step, index + 1, steps.size, recording, secondsLeft, stepRows, totalRows, liveFramesSeen, last, warning, error)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = !recording && error == null, onClick = {
                        try { bluetoothManager.enableAnglesAndMagAt100Hz() } catch (_: Exception) {}
                        stepRows = 0
                        warning = null
                        last = "Recording... waiting for sensor frames."
                        stepStart = System.currentTimeMillis()
                        writeMarker("STEP_START", step)
                        secondsLeft = step.seconds
                        recording = true
                    }) { Text(if (step.type == "baseline") "Record still bag" else "Record this punch") }
                    OutlinedButton(enabled = !recording, onClick = { writeMarker("STEP_SKIPPED", step); if (index < steps.lastIndex) index++ else finished = true }) { Text("Skip") }
                }
                Text("File: ${outputPath ?: "not created"}", fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PlanSelector(selected: CalPlan?, onSelect: (CalPlan) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Calibration file type", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        calPlans.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { p ->
                    val enabled = p == calPlans.first()
                    when {
                        selected == p && enabled -> Button({ onSelect(p) }, Modifier.weight(1f)) { Text(p.label, textAlign = TextAlign.Center) }
                        enabled -> OutlinedButton({ onSelect(p) }, Modifier.weight(1f)) { Text(p.label, textAlign = TextAlign.Center) }
                        else -> OutlinedButton(enabled = false, onClick = { }, modifier = Modifier.weight(1f)) { Text(p.label, textAlign = TextAlign.Center) }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LiveFrameCard(liveFramesSeen: Int, idleFramesSeen: Int, last: String, warning: String?, err: String?) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Live sensor frames: $liveFramesSeen", fontWeight = FontWeight.Bold)
        Text("Frames while not recording: $idleFramesSeen", fontSize = 12.sp)
        Text(last, fontSize = 12.sp)
        if (liveFramesSeen == 0) Text("If this stays at zero while the sensor is connected, recording will create only STEP_START / STEP_END markers.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        if (warning != null) Text(warning, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        if (err != null) Text("File error: $err", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoCard() = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Test plan", fontWeight = FontWeight.Bold)
        Text("1. For now, only Jab 80 cm is enabled so we can test quickly.")
        Text("2. First check that Live sensor frames is increasing.")
        Text("3. Then record baseline + light x3, medium x3, strong x3.")
        Text("4. If a step records zero frames, stop and send me the result.")
    }
}

@Composable
private fun StepCard(s: CalStep, n: Int, total: Int, rec: Boolean, left: Int, stepRows: Int, totalRows: Int, liveFramesSeen: Int, last: String, warning: String?, err: String?) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Step $n of $total", fontWeight = FontWeight.Bold)
        Text(s.instruction, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (s.type != "baseline") { Text("Move: ${s.move} | Force: ${s.force}"); Text("Height: ${s.height} cm from bottom | Repetition: ${s.rep} of ${s.totalReps}") }
        if (rec) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) { Text("Recording... $left s left", Modifier.padding(10.dp), fontWeight = FontWeight.Bold) } else Text("Stop the bag before pressing Record.")
        Text("Live frames: $liveFramesSeen | Step rows: $stepRows | Total rows: $totalRows", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(last, fontSize = 12.sp)
        if (warning != null) Text(warning, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        if (err != null) Text("File error: $err", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }
}

private fun buildSteps(plan: CalPlan): List<CalStep> {
    val heights = plan.height?.let { listOf(it) } ?: listOf(80, 100, 120, 140)
    val steps = mutableListOf(CalStep("baseline", "none", "none", heights.first(), 0, 0, 6, "Please stop the bag. Record the still bag baseline."))
    val forces = listOf("light", "medium", "strong")
    for (h in heights) for (f in forces) for (r in 1..3) steps += CalStep("punch", plan.move, f, h, r, 3, 5, "Stop the bag. Then punch once at $h cm from the bottom: ${plan.move}, $f force. Repetition $r of 3.")
    return steps
}

private fun parseFrame(b: ByteArray): ParsedFrame {
    if (b.size < 11 || b[0] != 0x55.toByte()) return ParsedFrame("raw_${b.size}_bytes")
    fun s16(i: Int): Int = ((b[i + 1].toInt()) shl 8) or (b[i].toInt() and 0xFF)
    fun head(d: Double): Double { var v = d % 360.0; if (v < 0) v += 360.0; return v }
    return when (b[1].toInt() and 0xFF) {
        0x51 -> { val x=s16(2)/32768.0*16.0; val y=s16(4)/32768.0*16.0; val z=s16(6)/32768.0*16.0; ParsedFrame("acceleration_temperature", x,y,z, sqrt(x*x+y*y+z*z), s16(8)/100.0, motionScore=abs(x)+abs(y)+abs(z)) }
        0x52 -> { val x=s16(2)/32768.0*2000.0; val y=s16(4)/32768.0*2000.0; val z=s16(6)/32768.0*2000.0; ParsedFrame("gyroscope", gyroX=x, gyroY=y, gyroZ=z, gyroMag=sqrt(x*x+y*y+z*z), motionScore=abs(x)+abs(y)+abs(z)) }
        0x53 -> { val roll=s16(2)/32768.0*180.0; val pitch=s16(4)/32768.0*180.0; val yaw=s16(6)/32768.0*180.0; ParsedFrame("tilt_angle_yaw_compass", angleX=roll, angleY=pitch, angleZ=yaw, tiltMag=sqrt(roll*roll+pitch*pitch), yawCompass=head(yaw)) }
        0x54 -> { val x=s16(2); val y=s16(4); val z=s16(6); ParsedFrame("magnetometer_magnetic_compass", magX=x, magY=y, magZ=z, magMag=sqrt((x*x+y*y+z*z).toDouble()), magneticCompass=head(atan2(y.toDouble(), x.toDouble())*180.0/PI)) }
        0x59 -> ParsedFrame("quaternion_possible", q0=s16(2)/32768.0, q1=s16(4)/32768.0, q2=s16(6)/32768.0, q3=s16(8)/32768.0)
        0x61 -> ParsedFrame("combined_0x61_raw_saved")
        0x62 -> ParsedFrame("combined_0x62_raw_saved")
        0x63 -> ParsedFrame("combined_0x63_raw_saved")
        0x64 -> ParsedFrame("combined_0x64_raw_saved")
        0x71 -> ParsedFrame("register_return_0x71_raw_saved")
        else -> ParsedFrame("wit_0x${(b[1].toInt() and 0xFF).toString(16)}_raw_saved")
    }
}

private const val RAW_BYTES = 32
private fun header(): String {
    val base = listOf("sessionTimeMillis","stepIndex","totalSteps","stepType","move","force","heightCmFromBagBottom","repetitionIndex","totalRepetitions","stepElapsedMillis","rawByteCount","frameType","rawHex","accXg","accYg","accZg","accMagnitudeG","temperatureC","gyroXdps","gyroYdps","gyroZdps","gyroMagnitudeDps","angleXdeg","angleYdeg","angleZdeg","tiltMagnitudeDeg","yawCompassDeg","magXraw","magYraw","magZraw","magMagnitudeRaw","magneticCompassDeg","quaternion0","quaternion1","quaternion2","quaternion3","displacementX","displacementY","displacementZ","displacementSpeedX","displacementSpeedY","displacementSpeedZ","portStatusRaw","motionScore")
    return (base + (0 until RAW_BYTES).map { "rawByte%02d".format(it) }).joinToString(",")
}
private fun now() = System.currentTimeMillis().toString()
private fun ByteArray.hex() = joinToString(" ") { String.format("%02X", it) }
private fun Double?.f6() = this?.let { String.format(Locale.US, "%.6f", it) }.orEmpty()
private fun csv(s: String) = "\"${s.replace("\"", "\"\"")}\""