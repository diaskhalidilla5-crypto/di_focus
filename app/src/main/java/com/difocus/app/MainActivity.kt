package com.difocus.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val interceptedPkg = intent?.getStringExtra("BLOCKED_PACKAGE")
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF38BDF8),
                    secondary = Color(0xFFF59E0B),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B)
                )
            ) {
                DIFocusAppScreen(interceptedPkg)
            }
        }
    }
}

@Composable
fun DIFocusAppScreen(interceptedPkg: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var selectedDay by remember { mutableStateOf("Сегодня") }
    val days = listOf("Сегодня", "Завтра", "Сб, 26 окт", "Вс, 27 окт")
    var tasksState by remember { mutableStateOf(FocusRepository.tasks.toList()) }
    var activeTaskForSolve by remember { mutableStateOf<FocusTask?>(null) }
    var statusBanner by remember { mutableStateOf(interceptedPkg?.let { "Перехвачен запуск $it! Выполните задания для разблокировки." }) }

    val bgGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .padding(16.dp)
    ) {
        // Шапка DI Focus
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF312E81), RoundedCornerShape(12.dp))
                        .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Di", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("DI Focus", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    val locked = FocusRepository.hasIncompleteTasksToday() && !FocusRepository.isEmergencyBypassActive(context)
                    Text(
                        text = if (locked) "Блокировка активна" else "Телефон разблокирован",
                        color = if (locked) Color(0xFFF59E0B) else Color(0xFF34D399),
                        fontSize = 13.sp
                    )
                }
            }
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) {
                Text("Спец. возможности", color = Color(0xFF38BDF8), fontSize = 12.sp)
            }
        }

        statusBanner?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color(0xFF7F1D1D),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(msg, color = Color.White, modifier = Modifier.padding(10.dp), fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Переключатель дней
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.forEach { day ->
                val isSelected = day == selectedDay
                Button(
                    onClick = { selectedDay = day },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B),
                        contentColor = if (isSelected) Color(0xFF0F172A) else Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(day, fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Вкладки
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFF38BDF8)
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Задания") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("+ Новая") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("SOS Выход") })
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> {
                val filtered = tasksState.filter { it.dayKey == selectedDay }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filtered) { task ->
                        TaskCard(
                            task = task,
                            onOpenTask = { activeTaskForSolve = task }
                        )
                    }
                }
            }
            1 -> {
                CreateTaskScreen(
                    selectedDay = selectedDay,
                    onTaskCreated = { newTask ->
                        FocusRepository.tasks.add(newTask)
                        tasksState = FocusRepository.tasks.toList()
                        selectedTab = 0
                    }
                )
            }
            2 -> {
                EmergencyBypassScreen(
                    onBypassGranted = {
                        FocusRepository.activateEmergencyBypass(context, 5)
                        statusBanner = "Экстренный доступ открыт на 5 минут!"
                    }
                )
            }
        }
    }

    // Модальное окно решения задачи / чата с ИИ / фото-пруфа
    activeTaskForSolve?.let { task ->
        TaskWorkspaceDialog(
            task = task,
            onDismiss = { activeTaskForSolve = null },
            onTaskUpdated = {
                tasksState = FocusRepository.tasks.toList()
            }
        )
    }
}

@Composable
fun TaskCard(task: FocusTask, onOpenTask: () -> Unit) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (task.completed) Color(0xFF34D399) else Color(0xFF334155),
                RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (task.type == TaskType.STUDY_FILE) "Учебный файл + ИИ" else "Фото-пруф ИИ",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (task.completed) "Выполнено" else "Ожидает",
                    color = if (task.completed) Color(0xFF34D399) else Color(0xFFFDE047),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(task.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(task.description, color = Color(0xFF94A3B8), fontSize = 13.sp)

            if (task.problemFileName != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Задания: ${task.problemFileName}" +
                        (task.answerKeyFileName?.let { " | Ответы: $it" } ?: ""),
                    color = Color(0xFFBAE6FD),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onOpenTask,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color(0xFF0F172A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (task.type == TaskType.STUDY_FILE) "Решать / Спросить ИИ-тьютора" else "Сделать фото-пруф")
            }
        }
    }
}

@Composable
fun CreateTaskScreen(selectedDay: String, onTaskCreated: (FocusTask) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var isStudyTask by remember { mutableStateOf(true) }
    var problemFileName by remember { mutableStateOf<String?>(null) }
    var problemContent by remember { mutableStateOf("") }
    var answerFileName by remember { mutableStateOf<String?>(null) }
    var answerContent by remember { mutableStateOf("") }

    val problemPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            problemFileName = it.lastPathSegment ?: "tasks_file.txt"
            problemContent = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
        }
    }

    val answerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            answerFileName = it.lastPathSegment ?: "answers_key.txt"
            answerContent = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Новое задание на: $selectedDay", color = Color.White, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = isStudyTask,
                onClick = { isStudyTask = true },
                label = { Text("Файл с задачами") }
            )
            FilterChip(
                selected = !isStudyTask,
                onClick = { isStudyTask = false },
                label = { Text("Фото-пруф") }
            )
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Название задания") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = desc,
            onValueChange = { desc = it },
            label = { Text("Описание или критерий") },
            modifier = Modifier.fillMaxWidth()
        )
        if (isStudyTask) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { problemPicker.launch("*/*") }, modifier = Modifier.weight(1f)) {
                    Text(problemFileName ?: "Файл заданий")
                }
                OutlinedButton(onClick = { answerPicker.launch("*/*") }, modifier = Modifier.weight(1f)) {
                    Text(answerFileName ?: "Файл ответов (опц.)")
                }
            }
            OutlinedTextField(
                value = problemContent,
                onValueChange = { problemContent = it },
                label = { Text("Текст задач (из файла или вручную)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
        Button(
            onClick = {
                if (title.isNotBlank()) {
                    onTaskCreated(
                        FocusTask(
                            id = System.currentTimeMillis().toString(),
                            dayKey = selectedDay,
                            title = title,
                            description = desc,
                            type = if (isStudyTask) TaskType.STUDY_FILE else TaskType.PHOTO_PROOF,
                            problemFileName = problemFileName ?: if (isStudyTask) "tasks.txt" else null,
                            problemFileContent = problemContent.ifBlank { null },
                            answerKeyFileName = answerFileName,
                            answerKeyContent = answerContent.ifBlank { null }
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить в расписание")
        }
    }
}

@Composable
fun TaskWorkspaceDialog(
    task: FocusTask,
    onDismiss: () -> Unit,
    onTaskUpdated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var solutionText by remember { mutableStateOf(task.userSolution) }
    var chatInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf(task.aiFeedback) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp != null) {
            isLoading = true
            scope.launch {
                val res = GeminiService.verifyPhotoProof(FocusRepository.getApiKey(context), task, bmp)
                task.completed = res.completed
                task.aiFeedback = res.feedback
                feedbackText = res.feedback
                isLoading = false
                onTaskUpdated()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = { Text(task.title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.type == TaskType.STUDY_FILE) {
                    Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = task.problemFileContent ?: task.description,
                            color = Color(0xFFBAE6FD),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    OutlinedTextField(
                        value = solutionText,
                        onValueChange = {
                            solutionText = it
                            task.userSolution = it
                        },
                        label = { Text("Твое решение и ответы") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    Button(
                        onClick = {
                            isLoading = true
                            scope.launch {
                                val res = GeminiService.verifyStudySolution(
                                    FocusRepository.getApiKey(context),
                                    task,
                                    solutionText
                                )
                                task.completed = res.completed
                                task.aiFeedback = res.feedback
                                feedbackText = res.feedback
                                isLoading = false
                                onTaskUpdated()
                            }
                        },
                        enabled = !isLoading && solutionText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Отправить решение на проверку ИИ")
                    }

                    Divider(color = Color(0xFF334155))
                    Text("Чат с ИИ-тьютором (только по теме задачи):", color = Color(0xFFFDE047), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Что непонятно в задаче?") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val q = chatInput
                                chatInput = ""
                                isLoading = true
                                scope.launch {
                                    val reply = GeminiService.askStudyTutor(FocusRepository.getApiKey(context), task, q)
                                    feedbackText = "Ответ тьютора: $reply"
                                    isLoading = false
                                }
                            },
                            enabled = !isLoading && chatInput.isNotBlank()
                        ) {
                            Text("Спросить")
                        }
                    }
                } else {
                    Text(task.description, color = Color(0xFF94A3B8))
                    Button(
                        onClick = { cameraLauncher.launch(null) },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сделать живое фото с камеры")
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                feedbackText?.let {
                    Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(8.dp)) {
                        Text(it, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
fun EmergencyBypassScreen(onBypassGranted: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val start = System.currentTimeMillis()
            while (isHolding && progress < 1f) {
                val elapsed = System.currentTimeMillis() - start
                progress = (elapsed / 15000f).coerceAtMost(1f)
                if (progress >= 1f) {
                    onBypassGranted()
                }
                delay(50)
            }
        } else {
            progress = 0f
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Text("Экстренная разблокировка", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Если нужно срочно вызвать такси, позвонить или показать билет — удерживай кнопку ниже 15 секунд без отрыва пальца. Телефон разблокируется ровно на 5 минут.",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp
        )
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(if (isHolding) Color(0xFFD97706) else Color(0xFF334155), RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            tryAwaitRelease()
                            isHolding = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isHolding) "Удерживай... ${(15 - (progress * 15).toInt())} сек" else "УДЕРЖИВАТЬ 15 СЕКУНД",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
