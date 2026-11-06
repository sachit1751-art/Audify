package com.music.audify.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.music.audify.wear.R

/** Short watch-specific first-run guide. No phone-only permissions or setup are requested here. */
@Composable
fun WearOnboarding(onFinish: () -> Unit) {
    ScreenScaffold {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(R.drawable.ic_watch), contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(38.dp))
                Spacer(Modifier.size(12.dp))
                Text("Audify on your wrist", style = WearType.title, color = Color.White,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.size(8.dp))
                Text("Control music playing on your phone, browse Up Next, and switch audio output.\n\nOpen Audify on your phone and keep Bluetooth and connectivity enabled to link your devices.",
                    style = WearType.rowSub, color = Secondary, textAlign = TextAlign.Center)
                Spacer(Modifier.size(16.dp))
                Pill(text = "Get started", onClick = onFinish)
                Spacer(Modifier.size(8.dp))
                Text("You can continue without connecting now.", style = WearType.caption,
                    color = Secondary, textAlign = TextAlign.Center)
            }
        }
    }
}
