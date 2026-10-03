package com.deniz0706.dose.ui

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deniz0706.dose.model.MedicationReminder
import com.deniz0706.dose.reminder.ReminderScheduler
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Paper = Color(0xFFF5F4F0)
private val TopLight = Color(0xFFFEFEFC)
private val TopShade = Color(0xFFF4F3EF)
private val Cobalt = Color(0xFF1E3A8A)
private val Ink = Color(0xFF22221F)
private val Muted = Color(0xFF74736D)
private val Hairline = Color(0xFFE4E2DC)

@Composable
fun DoseRoot(vm: MedicationViewModel = viewModel()) {
    val reminders by vm.reminders.collectAsState()
    var adding by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Cobalt,
            background = Paper,
            surface = TopLight,
            onBackground = Ink,
            onSurface = Ink
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
            Box {
                HomeScreen(
                    reminders = reminders,
                    onAdd = { adding = true },
                    onToggle = vm::toggle,
                    onDelete = vm::delete
                )
                if (adding) {
                    AddMedicationSheet(
                        onDismiss = { adding = false },
                        onSave = {
                            vm.save(it)
                            adding = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    reminders: List<MedicationReminder>,
    onAdd: () -> Unit,
    onToggle: (MedicationReminder) -> Unit,
    onDelete: (MedicationReminder) -> Unit
) {
    val active = reminders.filter { it.enabled }
    val next = active.minByOrNull {
        ReminderScheduler.nextOccurrence(it).toInstant().toEpochMilli()
    }
    val sorted = reminders.sortedWith(compareBy({ it.hour }, { it.minute }))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 28.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("İlaçlarım", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("tr", "TR"))),
                    fontSize = 14.sp,
                    color = Muted
                )
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .shadow(5.dp, CircleShape)
                    .background(TopLight, CircleShape)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 30.sp, fontWeight = FontWeight.Light, color = Cobalt)
            }
        }

        Spacer(Modifier.height(34.dp))

        Text("SIRADAKİ", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp, color = Cobalt)
        Spacer(Modifier.height(10.dp))

        StoneSurface {
            if (next == null) {
                Column(Modifier.padding(24.dp)) {
                    Text("Henüz bir hatırlatıcı yok", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text("İlk ilacını eklemek için + düğmesine dokun.", color = Muted, fontSize = 14.sp)
                }
            } else {
                val occurrence = ReminderScheduler.nextOccurrence(next)
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "%02d:%02d".format(next.hour, next.minute),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Medium,
                        color = Cobalt
                    )
                    Spacer(Modifier.width(22.dp))
                    Column {
                        Text(next.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        if (next.dose.isNotBlank()) Text(next.dose, color = Muted, fontSize = 14.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(relativeTime(occurrence), color = Cobalt, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(Modifier.height(34.dp))
        Text("BUGÜN", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp, color = Muted)
        Spacer(Modifier.height(10.dp))

        if (sorted.isEmpty()) {
            Text("Programın burada görünecek.", color = Muted, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            StoneSurface {
                Column {
                    sorted.forEachIndexed { index, item ->
                        MedicationRow(item, onToggle, onDelete)
                        if (index != sorted.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 92.dp),
                                thickness = 1.dp,
                                color = Hairline
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(36.dp))
        Text(
            "Dose · v0.1",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = Muted.copy(alpha = .65f),
            fontSize = 11.sp
        )
        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
}

@Composable
private fun StoneSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = .10f), spotColor = Color.Black.copy(alpha = .08f)),
        shape = RoundedCornerShape(26.dp),
        color = TopLight,
        tonalElevation = 0.dp,
        content = content
    )
}

@Composable
private fun MedicationRow(
    item: MedicationReminder,
    onToggle: (MedicationReminder) -> Unit,
    onDelete: (MedicationReminder) -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { showDelete = !showDelete }
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "%02d:%02d".format(item.hour, item.minute),
            modifier = Modifier.width(72.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = if (item.enabled) Cobalt else Muted
        )
        Column(Modifier.weight(1f)) {
            Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (item.enabled) Ink else Muted)
            if (item.dose.isNotBlank()) Text(item.dose, fontSize = 12.sp, color = Muted)
            AnimatedVisibility(showDelete, enter = fadeIn() + slideInVertically()) {
                Text(
                    "Sil",
                    color = Color(0xFF9C3E3E),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .clickable { onDelete(item) }
                )
            }
        }
        Switch(
            checked = item.enabled,
            onCheckedChange = { onToggle(item) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = TopLight,
                checkedTrackColor = Cobalt,
                uncheckedThumbColor = TopLight,
                uncheckedTrackColor = Color(0xFFD5D3CC),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun AddMedicationSheet(
    onDismiss: () -> Unit,
    onSave: (MedicationReminder) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(9) }
    var minute by remember { mutableIntStateOf(0) }
    var days by remember { mutableStateOf((1..7).toSet()) }
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .18f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
            color = TopLight
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .background(Color(0xFFD8D6D0), CircleShape)
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(22.dp))
                Text("Yeni ilaç", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(22.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("İlaç adı") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Doz / not (isteğe bağlı)") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(20.dp))

                Text("Saat", fontSize = 12.sp, color = Muted)
                Text(
                    "%02d:%02d".format(hour, minute),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Medium,
                    color = Cobalt,
                    modifier = Modifier.clickable {
                        TimePickerDialog(context, { _, h, m ->
                            hour = h
                            minute = m
                        }, hour, minute, true).show()
                    }
                )

                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tekrar", fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text(
                        if (days.size == 7) "Her gün" else "Her gün seç",
                        color = Cobalt,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { days = (1..7).toSet() }
                    )
                }
                Spacer(Modifier.height(10.dp))

                val labels = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    labels.forEachIndexed { index, label ->
                        val day = index + 1
                        val selected = day in days
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(if (selected) Cobalt else TopShade, CircleShape)
                                .clickable {
                                    days = if (selected) days - day else days + day
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 10.sp, color = if (selected) Color.White else Muted)
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(
                                MedicationReminder(
                                    name = name.trim(),
                                    dose = dose.trim(),
                                    hour = hour,
                                    minute = minute,
                                    repeatDays = days.ifEmpty { (1..7).toSet() }
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = name.isNotBlank(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cobalt)
                ) {
                    Text("Kaydet", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun relativeTime(target: ZonedDateTime): String {
    val minutes = java.time.Duration.between(ZonedDateTime.now(), target).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "şimdi"
        minutes < 60 -> "${minutes} dk sonra"
        minutes < 24 * 60 -> "${minutes / 60} sa ${minutes % 60} dk sonra"
        else -> target.format(DateTimeFormatter.ofPattern("EEEE", Locale("tr", "TR")))
    }
}
