package com.deniz0706.dose.ui

import android.app.TimePickerDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
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
import com.deniz0706.dose.model.MedicationTime
import com.deniz0706.dose.model.DoseEvent
import com.deniz0706.dose.model.DoseStatus
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
    val events by vm.events.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MedicationReminder?>(null) }
    var section by remember { mutableStateOf("today") }

    BackHandler(enabled = adding || editing != null) { adding = false; editing = null }
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
                when (section) {
                    "history" -> HistoryScreen(events, reminders, onBack = { section = "today" })
                    "stats" -> StatsScreen(events, onBack = { section = "today" })
                    "archive" -> ArchiveScreen(reminders, onRestore=vm::restore, onDelete=vm::delete, onBack={section="today"})
                    "settings" -> SettingsScreen(onArchive={section="archive"}, onBack = { section = "today" })
                    else -> HomeScreen(
                        reminders = reminders.filterNot { it.archived }, events = events,
                        onAdd = { adding = true }, onToggle = vm::toggle, onDelete = vm::delete,
                        onEdit = { editing = it }, onTaken = vm::markTaken, onUndoTaken = vm::undoTaken,
                        onHistory = { section = "history" }, onStats = { section = "stats" }, onSettings = { section = "settings" }
                    )
                }
                AnimatedVisibility(
                    visible = adding || editing != null,
                    enter = fadeIn(animationSpec = tween(220)),
                    exit = fadeOut(animationSpec = tween(180))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = .18f))
                            .clickable { adding = false; editing = null }
                    )
                }

                AnimatedVisibility(
                    visible = adding || editing != null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(340)
                    ) + fadeIn(animationSpec = tween(240)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(260)
                    ) + fadeOut(animationSpec = tween(180))
                ) {
                    AddMedicationSheet(
                        initial = editing,
                        onDismiss = { adding = false; editing = null },
                        onSave = {
                            vm.save(it)
                            adding = false; editing = null
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
    events: List<DoseEvent>,
    onAdd: () -> Unit,
    onToggle: (MedicationReminder) -> Unit,
    onDelete: (MedicationReminder) -> Unit,
    onArchive: (MedicationReminder) -> Unit = {},
    onEdit: (MedicationReminder) -> Unit,
    onTaken: (MedicationReminder, Long) -> Unit,
    onUndoTaken: (MedicationReminder, Long) -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val isWide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
    val today = LocalDate.now()
    val todayIso = today.dayOfWeek.value
    val filteredReminders = reminders.filter { search.isBlank() || it.name.contains(search, ignoreCase=true) }
    val todayReminders = filteredReminders.filter { it.repeatDays.isEmpty() || todayIso in it.repeatDays }
    val occurrences = filteredReminders.filter { it.enabled }.flatMap { r -> r.effectiveTimes().map { t -> Triple(r, t, ReminderScheduler.nextOccurrence(r, t)) } }
    val nextOccurrence = occurrences.minByOrNull { it.third.toInstant().toEpochMilli() }
    val next = nextOccurrence?.first
    val nextTime = nextOccurrence?.second
    val sorted = todayReminders.flatMap { r -> r.effectiveTimes().map { t -> r to t } }.sortedWith(compareBy({ it.second.hour }, { it.second.minute }))
    val todayEvents = events.filter { it.scheduledDate == today.toString() }
    val effectiveTodayEvents = sorted.mapNotNull { (reminder, time) ->
        todayEvents.firstOrNull { it.reminderId == reminder.id && it.timeId == time.id } ?: run {
            val scheduled = ZonedDateTime.now().withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
            if (reminder.enabled && scheduled.isBefore(ZonedDateTime.now())) DoseEvent(
                key = "${reminder.id}:${time.id}:$today",
                reminderId = reminder.id, timeId = time.id, scheduledDate = today.toString(),
                scheduledHour = time.hour, scheduledMinute = time.minute, status = DoseStatus.MISSED
            ) else null
        }
    }
    val takenCount = todayEvents.count { it.status == DoseStatus.TAKEN }
    val totalToday = sorted.count { it.first.enabled }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = if(isWide) 64.dp else 28.dp)
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
            Text("Geçmiş", color = Cobalt, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp).clickable(onClick = onHistory))
            Text("İstatistik", color = Cobalt, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp).clickable(onClick = onStats))
            Text("Ayarlar", color = Cobalt, fontSize = 12.sp, modifier = Modifier.padding(end = 14.dp).clickable(onClick = onSettings))
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

        Spacer(Modifier.height(18.dp))
        OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("İlaç ara")},shape=RoundedCornerShape(18.dp))
        Spacer(Modifier.height(24.dp))

        Text("SIRADAKİ", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp, color = Cobalt)
        Spacer(Modifier.height(10.dp))

        StoneSurface {
            if (totalToday > 0 && takenCount >= totalToday) {
                Column(Modifier.padding(24.dp)) {
                    Text("Bugün tamamlandı ✓", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Cobalt)
                    Spacer(Modifier.height(5.dp))
                    Text("Bugünkü planlanan dozların tamamı işaretlendi.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress={1f}, modifier=Modifier.fillMaxWidth().height(3.dp), color=Cobalt, trackColor=Hairline)
                }
            } else if (next == null) {
                Column(Modifier.padding(24.dp)) {
                    Text("Henüz bir hatırlatıcı yok", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text("İlk ilacını eklemek için + düğmesine dokun.", color = Muted, fontSize = 14.sp)
                }
            } else {
                val occurrence = nextOccurrence?.third ?: ReminderScheduler.nextOccurrence(next, nextTime ?: next.effectiveTimes().first())
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "%02d:%02d".format(nextTime?.hour ?: next.hour, nextTime?.minute ?: next.minute),
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

        Spacer(Modifier.height(28.dp))
        Text("GÜNÜN AKIŞI",fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.7.sp,color=Muted)
        Spacer(Modifier.height(10.dp))
        if(sorted.isNotEmpty()) StoneSurface { Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=18.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            sorted.take(5).forEach { (r,t) ->
                val status=todayEvents.firstOrNull { it.reminderId==r.id && it.timeId==t.id }?.status
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(if(status==DoseStatus.TAKEN) "✓" else if(status==DoseStatus.MISSED) "!" else "○",color=if(status==DoseStatus.MISSED) Color(0xFF9C6A2E) else Cobalt,fontSize=18.sp,fontWeight=FontWeight.Bold)
                    Text("%02d:%02d".format(t.hour,t.minute),fontSize=11.sp,color=Muted)
                }
            }
        }}
        Spacer(Modifier.height(34.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BUGÜN", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp, color = Muted, modifier = Modifier.weight(1f))
            Text("$takenCount / $totalToday tamamlandı", fontSize = 12.sp, color = Cobalt)
        }
        if (totalToday > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (takenCount.toFloat() / totalToday).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Cobalt,
                trackColor = Hairline
            )
        }
        Spacer(Modifier.height(10.dp))

        if (sorted.isEmpty()) {
            Text("Programın burada görünecek.", color = Muted, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            StoneSurface {
                Column {
                    sorted.forEachIndexed { index, pair ->
                        val item = pair.first
                        val time = pair.second
                        val taken = todayEvents.any { it.reminderId == item.id && it.timeId == time.id && it.status == DoseStatus.TAKEN }
                        val missed = effectiveTodayEvents.any { it.reminderId == item.id && it.timeId == time.id && it.status == DoseStatus.MISSED }
                        MedicationRow(item, time, taken, missed, onToggle, onDelete, onEdit, onTaken, onUndoTaken)
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

        val lowStock = reminders.filter { it.stock != null && it.stock <= it.lowStockThreshold }
        if (lowStock.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("STOK", fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=1.7.sp, color=Muted)
            Spacer(Modifier.height(10.dp))
            StoneSurface { Column {
                lowStock.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp, vertical=13.dp)) {
                        Text(item.name, modifier=Modifier.weight(1f), fontWeight=FontWeight.Medium)
                        Column(horizontalAlignment=Alignment.End) {
                            Text(if(item.stock==0) "Stok bitti" else "${item.stock} doz kaldı", color=Color(0xFF9C6A2E), fontSize=12.sp, fontWeight=FontWeight.Medium)
                            val weekly = item.repeatDays.ifEmpty { (1..7).toSet() }.size * item.effectiveTimes().size
                            if(item.stock != null && item.stock > 0 && weekly > 0) {
                                val daysLeft = kotlin.math.ceil(item.stock * 7.0 / weekly).toInt()
                                Text("yaklaşık $daysLeft gün", color=Muted, fontSize=10.sp)
                            }
                        }
                    }
                    if(index != lowStock.lastIndex) HorizontalDivider(color=Hairline)
                }
            }}
        }
        Spacer(Modifier.height(28.dp))
        Text("YAKLAŞAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp, color = Muted)
        Spacer(Modifier.height(10.dp))
        val upcoming = occurrences.sortedBy { it.third.toInstant().toEpochMilli() }.take(5)
        if (upcoming.isEmpty()) {
            Text("Yaklaşan etkin doz yok.", color=Muted, modifier=Modifier.padding(vertical=12.dp))
        } else StoneSurface {
            Column {
                upcoming.forEachIndexed { index, entry ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("%02d:%02d".format(entry.second.hour, entry.second.minute), color = Cobalt, fontWeight = FontWeight.Medium, modifier = Modifier.width(70.dp))
                        Column(Modifier.weight(1f)) { Text(entry.first.name, fontWeight = FontWeight.Medium); Text(relativeTime(entry.third), color = Muted, fontSize = 11.sp) }
                    }
                    if (index != upcoming.lastIndex) HorizontalDivider(color = Hairline, modifier = Modifier.padding(start = 90.dp))
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("YARIN", fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=1.7.sp, color=Muted)
        Spacer(Modifier.height(10.dp))
        val tomorrow = today.plusDays(1)
        val tomorrowItems = reminders.filter { it.enabled && (it.repeatDays.isEmpty() || tomorrow.dayOfWeek.value in it.repeatDays) }
            .flatMap { r -> r.effectiveTimes().map { t -> r to t } }
            .sortedWith(compareBy({it.second.hour},{it.second.minute}))
        if(tomorrowItems.isEmpty()) Text("Yarın planlanan doz yok.",color=Muted,modifier=Modifier.padding(vertical=12.dp))
        else StoneSurface { Column {
            tomorrowItems.take(6).forEachIndexed { index,pair ->
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                    Text("%02d:%02d".format(pair.second.hour,pair.second.minute),color=Cobalt,fontWeight=FontWeight.Medium,modifier=Modifier.width(70.dp))
                    Text(pair.first.name,fontWeight=FontWeight.Medium)
                }
                if(index != tomorrowItems.take(6).lastIndex) HorizontalDivider(color=Hairline,modifier=Modifier.padding(start=90.dp))
            }
            if(tomorrowItems.size>6) Text("+${tomorrowItems.size-6} doz daha",color=Muted,fontSize=11.sp,modifier=Modifier.padding(20.dp))
        }}
        Spacer(Modifier.height(36.dp))
        Column(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Dose",
                color = Muted.copy(alpha = .65f),
                fontSize = 11.sp
            )
            Text(
                "Developed by Deniz",
                color = Muted.copy(alpha = .65f),
                fontSize = 10.sp
            )
            Text(
                "E<3",
                color = Cobalt.copy(alpha = .72f),
                fontSize = 10.sp
            )
        }
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
    time: MedicationTime,
    taken: Boolean,
    missed: Boolean,
    onToggle: (MedicationReminder) -> Unit,
    onDelete: (MedicationReminder) -> Unit,
    onEdit: (MedicationReminder) -> Unit,
    onTaken: (MedicationReminder, Long) -> Unit,
    onUndoTaken: (MedicationReminder, Long) -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("İlacı sil?") },
            text = { Text("${item.name} ve planlanan hatırlatmaları kaldırılacak.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(item) }) { Text("Sil", color = Color(0xFF9C3E3E)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } }
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { showDelete = !showDelete }
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "%02d:%02d".format(time.hour, time.minute),
            modifier = Modifier.width(72.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = if (item.enabled) Cobalt else Muted
        )
        Column(Modifier.weight(1f)) {
            Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (item.enabled) Ink else Muted)
            if (item.dose.isNotBlank()) Text(item.dose, fontSize = 12.sp, color = Muted)
            if (item.note.isNotBlank()) Text(item.note, fontSize = 11.sp, color = Muted)
            if (taken) Text("ALINDI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Cobalt)
            else if (missed) Text("KAÇIRILDI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C6A2E))
            item.stock?.let { stock ->
                if (stock <= item.lowStockThreshold) Text("Stok: $stock · azalıyor", fontSize = 10.sp, color = Color(0xFF9C6A2E))
            }
            AnimatedVisibility(showDelete, enter = fadeIn() + slideInVertically()) {
                Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Düzenle", color = Cobalt, fontSize = 12.sp, modifier = Modifier.clickable { onEdit(item) })
                    if (!taken) Text("Aldım", color = Cobalt, fontSize = 12.sp, modifier = Modifier.clickable { onTaken(item, time.id) }) else Text("Geri al", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { onUndoTaken(item, time.id) })
                    Text("Sil", color = Color(0xFF9C3E3E), fontSize = 12.sp, modifier = Modifier.clickable { confirmDelete = true })
                }
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
    initial: MedicationReminder? = null,
    onDismiss: () -> Unit,
    onSave: (MedicationReminder) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var dose by remember(initial) { mutableStateOf(initial?.dose.orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    var stockText by remember(initial) { mutableStateOf(initial?.stock?.toString().orEmpty()) }
    var snoozeMinutes by remember(initial) { mutableIntStateOf(initial?.snoozeMinutes ?: 10) }
    var times by remember(initial) { mutableStateOf(initial?.effectiveTimes() ?: listOf(MedicationTime(hour = initial?.hour ?: 9, minute = initial?.minute ?: 0))) }
    var days by remember(initial) { mutableStateOf(initial?.repeatDays ?: (1..7).toSet()) }
    val context = LocalContext.current

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
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (initial == null) "Yeni ilaç" else "İlacı düzenle",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Kapat",
                        color = Cobalt,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(10.dp)
                            .clickable(onClick = onDismiss)
                    )
                }
                Spacer(Modifier.height(18.dp))

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
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Kendi notun (isteğe bağlı)") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = stockText,
                    onValueChange = { stockText = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Stok adedi (isteğe bağlı)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Erteleme", fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                    listOf(5, 10, 15, 30).forEach { value ->
                        Text(
                            "$value dk",
                            color = if (snoozeMinutes == value) Cobalt else Muted,
                            fontWeight = if (snoozeMinutes == value) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 12.dp).clickable { snoozeMinutes = value }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saatler", fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text("+ saat ekle", color = Cobalt, fontSize = 12.sp, modifier = Modifier.clickable {
                        TimePickerDialog(context, { _, h, m ->
                            times = times + MedicationTime(hour = h, minute = m)
                        }, 9, 0, true).show()
                    })
                }
                times.forEach { time ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("%02d:%02d".format(time.hour, time.minute), fontSize = 34.sp, fontWeight = FontWeight.Medium, color = Cobalt,
                            modifier = Modifier.weight(1f).clickable {
                                TimePickerDialog(context, { _, h, m ->
                                    times = times.map { if (it.id == time.id) it.copy(hour=h, minute=m) else it }
                                }, time.hour, time.minute, true).show()
                            })
                        if (times.size > 1) Text("Sil", color = Color(0xFF9C3E3E), fontSize = 12.sp, modifier = Modifier.clickable { times = times.filterNot { it.id == time.id } })
                    }
                }

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
                                    id = initial?.id ?: System.currentTimeMillis(),
                                    name = name.trim(),
                                    dose = dose.trim(),
                                    note = note.trim(),
                                    stock = stockText.toIntOrNull(),
                                    snoozeMinutes = snoozeMinutes,
                                    hour = times.first().hour,
                                    minute = times.first().minute,
                                    times = times,
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
                    Text(if (initial == null) "Kaydet" else "Değişiklikleri kaydet", fontWeight = FontWeight.SemiBold)
                }
            }
        }
}


@Composable
private fun HistoryScreen(events: List<DoseEvent>, reminders: List<MedicationReminder>, onBack: () -> Unit) {
    var days by remember { mutableIntStateOf(7) }
    val cutoff = LocalDate.now().minusDays(days.toLong() - 1)
    val visible = events.filter { runCatching { LocalDate.parse(it.scheduledDate) >= cutoff }.getOrDefault(false) }
        .sortedWith(compareByDescending<DoseEvent> { it.scheduledDate }.thenByDescending { it.scheduledHour * 60 + it.scheduledMinute })
    val names = reminders.associate { it.id to it.name }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Geçmiş", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Kapat", color = Cobalt, modifier = Modifier.clickable(onClick = onBack))
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(7,30).forEach { d -> FilterChip(selected = days == d, onClick = { days = d }, label = { Text("$d gün") }) }
        }
        Spacer(Modifier.height(18.dp))
        if (visible.isNotEmpty()) {
            val grouped = visible.groupBy { it.scheduledDate }
            StoneSurface { Column(Modifier.padding(18.dp)) {
                Text("Özet", fontWeight=FontWeight.SemiBold, fontSize=16.sp)
                Spacer(Modifier.height(10.dp))
                grouped.entries.take(7).forEach { (date, dayEvents) ->
                    val taken = dayEvents.count { it.status == DoseStatus.TAKEN }
                    val missed = dayEvents.count { it.status == DoseStatus.MISSED }
                    Row(Modifier.fillMaxWidth().padding(vertical=5.dp)) {
                        Text(date, color=Muted, fontSize=12.sp, modifier=Modifier.weight(1f))
                        Text("$taken alındı" + if(missed>0) " · $missed kaçırıldı" else "", color=if(missed>0) Color(0xFF9C6A2E) else Cobalt, fontSize=12.sp)
                    }
                }
            }}
            Spacer(Modifier.height(14.dp))
        }
        if (visible.isEmpty()) Text("Henüz kayıt yok. Aldım veya Ertele işlemleri burada görünecek.", color = Muted)
        else StoneSurface { Column {
            visible.forEachIndexed { index, event ->
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(event.medicationName.ifBlank { names[event.reminderId] ?: "İlaç" }, fontWeight = FontWeight.Medium)
                        Text("${event.scheduledDate} · %02d:%02d".format(event.scheduledHour,event.scheduledMinute), color = Muted, fontSize = 12.sp)
                    }
                    Text(when(event.status){ DoseStatus.TAKEN -> "ALINDI"; DoseStatus.SNOOZED -> "ERTELENDİ"; DoseStatus.MISSED -> "KAÇIRILDI" }, color=if(event.status==DoseStatus.MISSED) Color(0xFF9C6A2E) else Cobalt, fontSize=11.sp, fontWeight=FontWeight.Bold)
                }
                if(index != visible.lastIndex) HorizontalDivider(color=Hairline)
            }
        }}
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun StatsScreen(events: List<DoseEvent>, onBack: () -> Unit) {
    var days by remember { mutableIntStateOf(7) }
    val cutoff=LocalDate.now().minusDays(days.toLong()-1)
    val visible=events.filter { runCatching { LocalDate.parse(it.scheduledDate)>=cutoff }.getOrDefault(false) }
    val finalByDose=visible.groupBy { it.key }.mapNotNull { (_,list) -> list.maxByOrNull { it.updatedAt } }
    val taken=finalByDose.count { it.status==DoseStatus.TAKEN }
    val missed=finalByDose.count { it.status==DoseStatus.MISSED }
    val snoozed=visible.count { it.status==DoseStatus.SNOOZED }
    val completed=taken+missed
    val adherence=if(completed==0) 0 else (taken*100/completed)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=28.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment=Alignment.CenterVertically){Text("İstatistik",fontSize=34.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text("Kapat",color=Cobalt,modifier=Modifier.clickable(onClick=onBack))}
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){listOf(7,30).forEach{d->FilterChip(selected=days==d,onClick={days=d},label={Text("$d gün")})}}
        Spacer(Modifier.height(18.dp))
        StoneSurface { Column(Modifier.padding(22.dp)) {
            Text("%$adherence",fontSize=44.sp,fontWeight=FontWeight.SemiBold,color=Cobalt)
            Text("planlanan dozların alınma oranı",color=Muted,fontSize=12.sp)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress={(adherence/100f).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(5.dp),color=Cobalt,trackColor=Hairline)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()){StatValue("Alındı",taken,Modifier.weight(1f));StatValue("Kaçırıldı",missed,Modifier.weight(1f));StatValue("Erteleme",snoozed,Modifier.weight(1f))}
        }}
        Spacer(Modifier.height(16.dp))
        Text("Bu ekran yalnızca kaydettiğin hatırlatmaları özetler; tıbbi değerlendirme yapmaz.",color=Muted,fontSize=11.sp)
    }
}
@Composable private fun StatValue(label:String,value:Int,modifier:Modifier=Modifier){Column(modifier){Text(value.toString(),fontSize=24.sp,fontWeight=FontWeight.SemiBold);Text(label,color=Muted,fontSize=11.sp)}}

@Composable
private fun ArchiveScreen(reminders:List<MedicationReminder>,onRestore:(MedicationReminder)->Unit,onDelete:(MedicationReminder)->Unit,onBack:()->Unit){
    val archived=reminders.filter { it.archived }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=28.dp).verticalScroll(rememberScrollState())){
        Spacer(Modifier.height(28.dp));Row(verticalAlignment=Alignment.CenterVertically){Text("Arşiv",fontSize=34.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text("Kapat",color=Cobalt,modifier=Modifier.clickable(onClick=onBack))}
        Spacer(Modifier.height(20.dp))
        if(archived.isEmpty()) Text("Arşivlenmiş ilaç yok.",color=Muted) else StoneSurface{Column{
            archived.forEachIndexed{index,item->Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(item.name,fontWeight=FontWeight.Medium);Text(item.dose,color=Muted,fontSize=11.sp)};Text("Geri yükle",color=Cobalt,fontSize=12.sp,modifier=Modifier.clickable{onRestore(item)});Spacer(Modifier.width(14.dp));Text("Sil",color=Color(0xFF9C3E3E),fontSize=12.sp,modifier=Modifier.clickable{onDelete(item)})};if(index!=archived.lastIndex)HorizontalDivider(color=Hairline)}}
        }}
    }
}

@Composable
private fun SettingsScreen(onArchive: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val alarmManager = remember { context.getSystemService(android.app.AlarmManager::class.java) }
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val notificationManager = remember { context.getSystemService(android.app.NotificationManager::class.java) }
    val fullScreen = Build.VERSION.SDK_INT < 34 || notificationManager.canUseFullScreenIntent()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=28.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Ayarlar",fontSize=34.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
            Text("Kapat",color=Cobalt,modifier=Modifier.clickable(onClick=onBack))
        }
        Spacer(Modifier.height(28.dp))
        Text("HATIRLATMA GÜVENİLİRLİĞİ",fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp,color=Muted)
        Spacer(Modifier.height(10.dp))
        StoneSurface { Column(Modifier.padding(20.dp)) {
            ReliabilityRow("Bildirimler", notifications)
            Spacer(Modifier.height(10.dp))
            ReliabilityRow("Tam zamanlı alarm", exact)
            Spacer(Modifier.height(10.dp))
            ReliabilityRow("Tam ekran alarm", fullScreen)
            if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(12.dp))
                Text("Tam alarm iznini aç", color=Cobalt, fontSize=12.sp, modifier=Modifier.clickable {
                    runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
                })
            }
            if (!fullScreen && Build.VERSION.SDK_INT >= 34) {
                Spacer(Modifier.height(10.dp))
                Text("Tam ekran iznini aç", color=Cobalt, fontSize=12.sp, modifier=Modifier.clickable {
                    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))) }
                })
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick={ ReminderScheduler.scheduleTest(context) },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=Cobalt)) {
                Text("10 saniye sonra test alarmı")
            }
            Spacer(Modifier.height(12.dp))
            Text("Bazı Android cihazlarında pil tasarrufu hatırlatmaları geciktirebilir. Dose mevcut izinları kullanır ancak teslimatı garanti edemez.",fontSize=12.sp,color=Muted)
        }}
        Spacer(Modifier.height(22.dp))
        Text("Arşivlenmiş ilaçlar",color=Cobalt,fontWeight=FontWeight.Medium,modifier=Modifier.clickable(onClick=onArchive))
        Spacer(Modifier.height(30.dp))
        Text("HAKKINDA",fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp,color=Muted)
        Spacer(Modifier.height(10.dp))
        StoneSurface { Column(Modifier.padding(20.dp)) {
            Text("Dose",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
            Text("Kişisel ilaç hatırlatıcısı",color=Muted,fontSize=13.sp)
            Spacer(Modifier.height(14.dp)); Text("Developed by Deniz",color=Muted,fontSize=12.sp); Text("E<3",color=Cobalt,fontSize=11.sp)
        }}
    }
}
@Composable private fun ReliabilityRow(label:String,ok:Boolean) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text(label,modifier=Modifier.weight(1f),fontWeight=FontWeight.Medium)
        Text(if(ok) "Hazır" else "İzin gerekli",color=if(ok) Cobalt else Color(0xFF9C6A2E),fontSize=12.sp,fontWeight=FontWeight.Bold)
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
