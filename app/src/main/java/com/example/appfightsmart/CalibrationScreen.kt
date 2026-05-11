package com.example.appfightsmart

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlin.math.abs

private data class CalibrationStep(
    val type: String,
    val move: String,
    val force: String,
    val heightCmFromBagBottom: Int,
    val repetitions: Int,
    val durationSeconds: Int,
    val instruction: String
)

private data class ParsedSensorFrame(
    val frameType: String,
    val accXg: Double? = null,
    val accYg: Double? = null,
    val accZg: Double? = null,
    val gyroXdps: Double? = null,
    val gyroYdps: Double? = null,
    val gyroZdps: Double? = null,
    val angleXdeg: Double? = null,
    val angleYdeg: Double? = null,
    val angleZdeg: Double? = null,
    val magXraw: Int? = null,
    val magYraw: Int? = null,
    val magZraw: Int? = null,
    val motionScore: Double? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    navController: NavHostController,
    bluetoothManager: BluetoothManager
) {
    val context = LocalContext.current
    val steps = remember { buildPunchCalibrationSteps() }
    val fileLock = remember { Any() }
    var writer by remember { mutableStateOf<BufferedWriter?>(null) }
    var outputPath by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var stepStartMillis by remember { mutableLongStateOf(0L) }
    var frameCount by remember { mutableIntStateOf(0) }
    var lastFrameSummary by remember { mutableStateOf("Waiting for sensor data...") }
    var lastMotionScore by remember { mutableStateOf<Double?>(null) }

    val currentStep = steps.getOrNull(currentStepIndex)

    fun closeWriter() {
        synchronized(fileLock) {
            writer?.flush()
            writer?.close()
            writer = null
        }
    }

    fun createCalibrationFile(): BufferedWriter {
        val directory = File(context.getExternalFilesDir(null), "calibration")
        if (!directory.exists()) directory.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(directory, "fight_smart_calibration_$stamp.csv")
        outputPath = file.absolutePath
        val newWriter = BufferedWriter(FileWriter(file, false))
        newWriter.write("# FightSmart punch calibration\n")
        newWriter.write("# Created: $stamp\n")
        newWriter.write("# Bag height: 160 cm\n")
        newWriter.write("# Bag elevation from ground: 30 cm\n")
        newWriter.write("# Sensor pipe insertion from top: 80 cm\n")
        newWriter.write("# Estimated sensor height: 80 cm from bag bottom / 110 cm from ground\n")
        newWriter.write("# Each punch step asks for 3 repetitions. Keep the bag still before each recording.\n")
        newWriter.write("sessionTimeMillis,stepIndex,totalSteps,stepType,move,force,heightCmFromBagBottom,repetitions,stepElapsedMillis,frameType,rawHex,accXg,accYg,accZg,gyroXdps,gyroYdps,gyroZdps,angleXdeg,angleYdeg,angleZdeg,magXraw,magYraw,magZraw,motionScore\n")
        newWriter.flush()
        return newWriter
    }

    fun startSession() {
        closeWriter()
        writer = createCalibrationFile()
        started = true
        finished = false
        currentStepIndex = 0
        frameCount = 0
        lastFrameSummary = "File created. Start the first step."
    }

    fun writeFrame(bytes: ByteArray, step: CalibrationStep, parsed: ParsedSensorFrame) {
        val stepElapsedMillis = if (stepStartMillis > 0L) System.currentTimeMillis() - stepStartMillis else 0L
        val line = listOf(
            System.currentTimeMillis().toString(),
            (currentStepIndex + 1).toString(),
            steps.size.toString(),
            csv(step.type),
            csv(step.move),
            csv(step.force),
            step.heightCmFromBagBottom.toString(),
            step.repetitions.toString(),
            stepElapsedMillis.toString(),
            csv(parsed.frameType),
            csv(bytes.toHexString()),
            parsed.accXg?.format6().orEmpty(),
            parsed.accYg?.format6().orEmpty(),
            parsed.accZg?.format6().orEmpty(),
            parsed.gyroXdps?.format6().orEmpty(),
            parsed.gyroYdps?.format6().orEmpty(),
            parsed.gyroZdps?.format6().orEmpty(),
            parsed.angleXdeg?.format6().orEmpty(),
            parsed.angleYdeg?.format6().orEmpty(),
            parsed.angleZdeg?.format6().orEmpty(),
            parsed.magXraw?.toString().orEmpty(),
            parsed.magYraw?.toString().orEmpty(),
            parsed.magZraw?.toString().orEmpty(),
            parsed.motionScore?.format6().orEmpty()
        ).joinToString(",")
        synchronized(fileLock) {
            writer?.write(line)
            writer?.newLine()
        }
    }

    DisposableEffect(bluetoothManager, isRecording, currentStepIndex) {
        val listener: (ByteArray) -> Unit = { bytes ->
            if (isRecording && currentStep != null) {
                val parsed = parseWitFrame(bytes)
                writeFrame(bytes, currentStep, parsed)
                frameCount += 1
                lastMotionScore = parsed.motionScore
                lastFrameSummary = "Last frame: ${parsed.frameType} | frames saved: $frameCount"
            }
        }
        bluetoothManager.addDataListener(listener)
        onDispose {
            bluetoothManager.removeDataListener(listener)
        }
    }

    DisposableEffect(Unit) {
        onDispose { closeWriter() }
    }

    LaunchedEffect(isRecording, secondsLeft) {
        if (isRecording && secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
            if (secondsLeft <= 0) {
                isRecording = false
                synchronized(fileLock) { writer?.flush() }
                if (currentStepIndex >= steps.lastIndex) {
                    finished = true
                    closeWriter()
                } else {
                    currentStepIndex += 1
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensor Calibration") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Punch sensor calibration",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "This records raw and parsed sensor data for acceleration, gyroscope, angles, magnetometer frames, and any extra raw frames received. Do this with punches only for now.",
                textAlign = TextAlign.Center
            )

            if (!started) {
                CalibrationInfoCard()
                Button(onClick = { startSession() }) {
                    Text("Create file and start calibration")
                }
            } else if (finished) {
                Text("Calibration finished.", fontWeight = FontWeight.Bold)
                Text("Saved file:", fontWeight = FontWeight.Bold)
                Text(outputPath ?: "Unknown path", textAlign = TextAlign.Center)
                Button(onClick = { startSession() }) {
                    Text("Start another calibration file")
                }
            } else if (currentStep != null) {
                StepCard(
                    step = currentStep,
                    currentStepNumber = currentStepIndex + 1,
                    totalSteps = steps.size,
                    isRecording = isRecording,
                    secondsLeft = secondsLeft,
                    frameCount = frameCount,
                    lastFrameSummary = lastFrameSummary,
                    lastMotionScore = lastMotionScore
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        enabled = !isRecording,
                        onClick = {
                            frameCount = 0
                            lastFrameSummary = "Recording..."
                            stepStartMillis = System.currentTimeMillis()
                            secondsLeft = currentStep.durationSeconds
                            isRecording = true
                        }
                    ) {
                        Text(if (currentStep.type == "baseline") "Record still bag" else "Record this step")
                    }
                    OutlinedButton(
                        enabled = !isRecording,
                        onClick = {
                            if (currentStepIndex < steps.lastIndex) currentStepIndex += 1 else finished = true
                        }
                    ) {
                        Text("Skip")
                    }
                }
                Text("File: ${outputPath ?: "not created"}", fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun CalibrationInfoCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Test plan", fontWeight = FontWeight.Bold)
            Text("1. First record a still-bag baseline.")
            Text("2. Then follow the prompts for jab, cross, and hook.")
            Text("3. Each punch step asks for 3 repetitions at light, medium, and strong force.")
            Text("4. Heights are measured from the bottom of the bag: 80 cm, 100 cm, 120 cm, and 140 cm.")
            Text("5. Let the bag stop moving before starting the next step.")
        }
    }
}

@Composable
private fun StepCard(
    step: CalibrationStep,
    currentStepNumber: Int,
    totalSteps: Int,
    isRecording: Boolean,
    secondsLeft: Int,
    frameCount: Int,
    lastFrameSummary: String,
    lastMotionScore: Double?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Step $currentStepNumber of $totalSteps", fontWeight = FontWeight.Bold)
            Text(step.instruction, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (step.type != "baseline") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Move: ${step.move}")
                    Text("Force: ${step.force}")
                }
                Text("Height: ${step.heightCmFromBagBottom} cm from the bottom of the bag")
                Text("Repetitions: ${step.repetitions}")
            }
            if (isRecording) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Recording... $secondsLeft s left",
                        modifier = Modifier.padding(10.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text("Before pressing record, stop the bag and stand in your normal fighting position.")
            }
            Text(lastFrameSummary, fontSize = 12.sp)
            Text("Motion score: ${lastMotionScore?.format3() ?: "--"}", fontSize = 12.sp)
            Text("Frames in this step: $frameCount", fontSize = 12.sp)
        }
    }
}

private fun buildPunchCalibrationSteps(): List<CalibrationStep> {
    val steps = mutableListOf<CalibrationStep>()
    steps += CalibrationStep(
        type = "baseline",
        move = "none",
        force = "none",
        heightCmFromBagBottom = 80,
        repetitions = 0,
        durationSeconds = 6,
        instruction = "Please stop the bag. Record the still bag baseline."
    )

    val moves = listOf("Jab", "Cross", "Hook")
    val heights = listOf(80, 100, 120, 140)
    val forces = listOf("light", "medium", "strong")

    for (height in heights) {
        for (move in moves) {
            for (force in forces) {
                steps += CalibrationStep(
                    type = "punch",
                    move = move,
                    force = force,
                    heightCmFromBagBottom = height,
                    repetitions = 3,
                    durationSeconds = 8,
                    instruction = "Punch at $height cm from the bottom: $move, $force force, 3 times."
                )
            }
        }
    }
    return steps
}

private fun parseWitFrame(bytes: ByteArray): ParsedSensorFrame {
    if (bytes.size < 11 || bytes[0] != 0x55.toByte()) {
        return ParsedSensorFrame(frameType = "raw_${bytes.size}_bytes")
    }
    fun s16(offset: Int): Int {
        val low = bytes[offset].toInt() and 0xFF
        val high = bytes[offset + 1].toInt()
        return (high shl 8) or low
    }

    return when (bytes[1].toInt() and 0xFF) {
        0x51 -> {
            val ax = s16(2) / 32768.0 * 16.0
            val ay = s16(4) / 32768.0 * 16.0
            val az = s16(6) / 32768.0 * 16.0
            ParsedSensorFrame("acceleration", accXg = ax, accYg = ay, accZg = az, motionScore = abs(ax) + abs(ay) + abs(az))
        }
        0x52 -> {
            val gx = s16(2) / 32768.0 * 2000.0
            val gy = s16(4) / 32768.0 * 2000.0
            val gz = s16(6) / 32768.0 * 2000.0
            ParsedSensorFrame("gyroscope", gyroXdps = gx, gyroYdps = gy, gyroZdps = gz, motionScore = abs(gx) + abs(gy) + abs(gz))
        }
        0x53 -> ParsedSensorFrame(
            frameType = "angle",
            angleXdeg = s16(2) / 32768.0 * 180.0,
            angleYdeg = s16(4) / 32768.0 * 180.0,
            angleZdeg = s16(6) / 32768.0 * 180.0
        )
        0x54 -> ParsedSensorFrame(
            frameType = "magnetometer",
            magXraw = s16(2),
            magYraw = s16(4),
            magZraw = s16(6)
        )
        0x61 -> ParsedSensorFrame(frameType = "combined_0x61")
        0x62 -> ParsedSensorFrame(frameType = "combined_0x62")
        0x63 -> ParsedSensorFrame(frameType = "combined_0x63")
        0x64 -> ParsedSensorFrame(frameType = "combined_0x64")
        0x71 -> ParsedSensorFrame(frameType = "register_return_0x71")
        else -> ParsedSensorFrame(frameType = "wit_0x${(bytes[1].toInt() and 0xFF).toString(16)}")
    }
}

private fun ByteArray.toHexString(): String = joinToString(" ") { String.format("%02X", it) }
private fun Double.format6(): String = String.format(Locale.US, "%.6f", this)
private fun Double.format3(): String = String.format(Locale.US, "%.3f", this)
private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
