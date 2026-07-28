package com.rork.recto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rork.recto.ui.theme.CalibrationCyan
import com.rork.recto.ui.theme.Hairline
import com.rork.recto.ui.theme.RegistrationMagenta
import com.rork.recto.ui.theme.SignalGreen

@Composable
fun ReceiptScreen(navController: NavController, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("VERIFICATION RECEIPT", style = MaterialTheme.typography.labelLarge, color = CalibrationCyan)
                    Text("Capture sealed", style = MaterialTheme.typography.headlineLarge)
                }
                IconButton(onClick = { navController.navigate("home") { popUpTo("home") { inclusive = true } } }) {
                    Icon(Icons.Rounded.Close, "Close receipt")
                }
            }
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth().height(190.dp).background(Color.White, RoundedCornerShape(10.dp))) {
                Column(Modifier.padding(22.dp)) {
                    repeat(5) { index ->
                        Box(Modifier.fillMaxWidth(if (index == 4) .65f else 1f).height(8.dp).background(Color(0xFFD3D8D9)))
                        Spacer(Modifier.height(14.dp))
                    }
                    Box(Modifier.fillMaxWidth(.78f).height(18.dp).background(Color.Black))
                    Text("CONTENT REMOVED", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.labelMedium, color = RegistrationMagenta)
                }
            }
            Spacer(Modifier.height(20.dp))
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = SignalGreen)
                        Text("  DESTRUCTIVE REDACTION VERIFIED", style = MaterialTheme.typography.labelLarge, color = SignalGreen)
                    }
                    Spacer(Modifier.height(18.dp))
                    ReceiptRow("SOURCE LAYERS", "FLATTENED")
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Hairline)
                    ReceiptRow("HIDDEN TEXT", "NONE DETECTED")
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Hairline)
                    ReceiptRow("PIXEL COVERAGE", "100.0%")
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Hairline)
                    ReceiptRow("PROOFCHECK", "98 / 100")
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Hairline)
                    ReceiptRow("RECEIPT ID", "RC-7F3A-0192")
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { navController.navigate("home") { popUpTo("home") { inclusive = true } } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CalibrationCyan, contentColor = Color.Black)
            ) {
                Icon(Icons.Rounded.FileDownload, null)
                Text("  SAVE SEALED PDF", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
