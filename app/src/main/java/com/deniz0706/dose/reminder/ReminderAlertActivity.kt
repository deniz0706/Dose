package com.deniz0706.dose.reminder

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
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
import com.deniz0706.dose.model.MedicationReminder

class ReminderAlertActivity : Activity() {
    private var reminder: MedicationReminder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        reminder = ReminderScheduler.decode(intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER))
        val item = reminder ?: run { finish(); return }

        setContent {
            ReminderAlert(
                reminder = item,
                onTaken = {
                    NotificationHelper.cancel(this, item)
                    finish()
                },
                onSnooze = {
                    NotificationHelper.cancel(this, item)
                    ReminderScheduler.snooze(this, item)
                    finish()
                }
            )
        }
    }
}

@Composable
private fun ReminderAlert(
    reminder: MedicationReminder,
    onTaken: () -> Unit,
    onSnooze: () -> Unit
) {
    val paper = Color(0xFFF5F4F0)
    val cobalt = Color(0xFF1E3A8A)
    val ink = Color(0xFF22221F)
    val muted = Color(0xFF74736D)

    MaterialTheme(colorScheme = lightColorScheme(primary = cobalt, background = paper, surface = Color(0xFFFEFEFC))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(paper)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("İLAÇ ZAMANI", color = cobalt, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(24.dp))
            Text(
                "%02d:%02d".format(reminder.hour, reminder.minute),
                color = cobalt,
                fontSize = 58.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(14.dp))
            Text(reminder.name, color = ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            if (reminder.dose.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(reminder.dose, color = muted, fontSize = 17.sp)
            }
            Spacer(Modifier.height(46.dp))
            Button(
                onClick = onTaken,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Aldım", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("10 dk sonra", color = cobalt)
            }
        }
    }
}
