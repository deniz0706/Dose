package com.deniz0706.dose.reminder

import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.deniz0706.dose.model.MedicationReminder
import kotlinx.coroutines.launch

class ReminderAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true); setTurnScreenOn(true)
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val item = ReminderScheduler.decode(intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER)) ?: run { finish(); return }
        val timeId = intent.getLongExtra(ReminderScheduler.EXTRA_TIME_ID, item.effectiveTimes().first().id)
        setContent {
            ReminderAlert(item, timeId,
                onTaken = { lifecycleScope.launch { DoseActions.taken(this@ReminderAlertActivity, item, timeId); finish() } },
                onSnooze = { lifecycleScope.launch { DoseActions.snoozed(this@ReminderAlertActivity, item, timeId); finish() } }
            )
        }
    }
}

@Composable private fun ReminderAlert(reminder: MedicationReminder, timeId: Long, onTaken: () -> Unit, onSnooze: () -> Unit) {
    val paper=Color(0xFFF5F4F0); val cobalt=Color(0xFF1E3A8A); val ink=Color(0xFF22221F); val muted=Color(0xFF74736D)
    val time=reminder.effectiveTimes().firstOrNull{it.id==timeId} ?: reminder.effectiveTimes().first()
    MaterialTheme(colorScheme=lightColorScheme(primary=cobalt,background=paper,surface=Color(0xFFFEFEFC))) {
        Column(Modifier.fillMaxSize().background(paper).statusBarsPadding().navigationBarsPadding().padding(30.dp),
            horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
            Text("İLAÇ ZAMANI",color=cobalt,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
            Spacer(Modifier.height(24.dp)); Text("%02d:%02d".format(time.hour,time.minute),color=cobalt,fontSize=58.sp,fontWeight=FontWeight.Medium)
            Spacer(Modifier.height(14.dp)); Text(reminder.name,color=ink,fontSize=30.sp,fontWeight=FontWeight.SemiBold)
            if(reminder.dose.isNotBlank()){Spacer(Modifier.height(8.dp));Text(reminder.dose,color=muted,fontSize=17.sp)}
            if(reminder.note.isNotBlank()){Spacer(Modifier.height(6.dp));Text(reminder.note,color=muted,fontSize=14.sp)}
            Spacer(Modifier.height(46.dp))
            Button(onClick=onTaken,modifier=Modifier.fillMaxWidth().height(58.dp),shape=RoundedCornerShape(20.dp)){Text("Aldım",fontSize=17.sp,fontWeight=FontWeight.SemiBold)}
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick=onSnooze,modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(20.dp)){Text("${reminder.snoozeMinutes} dk sonra",color=cobalt)}
        }
    }
}
