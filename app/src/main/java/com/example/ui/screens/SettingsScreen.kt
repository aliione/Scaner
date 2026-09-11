package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LanguageManager
import com.example.core.i18n.LocalAppLanguage
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.SoilProfile
import com.example.ui.theme.GeoAmber
import com.example.ui.theme.GeoCyan
import com.example.ui.theme.GeoGreenSignal
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardSurface
import com.example.ui.theme.SlateDarkBackground
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val strings = LocalAppStrings.current
    val currentLanguage = LocalAppLanguage.current
    val isPersian = currentLanguage == AppLanguage.PERSIAN

    var selectedSoil by remember { mutableStateOf(SoilProfile.DEFAULT_SOILS.first()) }
    var soundFeedback by remember { mutableStateOf(true) }
    var hapticFeedback by remember { mutableStateOf(true) }
    var sensorHeightCm by remember { mutableStateOf("10") }
    var sensorSpeedHz by remember { mutableIntStateOf(20) }
    var bluetoothAutoConnect by remember { mutableStateOf(true) }
    var useBoustrophedonZigZag by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateDarkBackground)
            )
        },
        containerColor = SlateDarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Language & Vazirmatn Typography Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, GeoCyan)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = GeoCyan)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            strings.appLanguageLabel,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isPersian)
                            "زبان فعلی: فارسی همراه با چیدمان راست‌به‌چپ (RTL) و فونت استاندارد Vazirmatn."
                        else
                            "Selected Language: English. Switch to Persian to enable RTL and Vazirmatn typography.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = isPersian,
                            onClick = { LanguageManager.setLanguage(AppLanguage.PERSIAN) },
                            label = { Text("فارسی (Vazirmatn)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeoCyan.copy(alpha = 0.25f),
                                selectedLabelColor = GeoCyan
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isPersian,
                            onClick = { LanguageManager.setLanguage(AppLanguage.ENGLISH) },
                            label = { Text("English", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeoCyan.copy(alpha = 0.25f),
                                selectedLabelColor = GeoCyan
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(GeoCyan.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            strings.vazirmatnFontNotice,
                            color = GeoCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Hardware Sensor & Sense Speed (Visualizer 3D Feature)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = GeoAmber)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.senseSpeed, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isPersian)
                            "تنظیم نرخ نمونه‌برداری سنسور (Sense Speed) و فاصله زمانی ارسال پالس برای پیمایش میدانی."
                        else
                            "Sets hardware sampling frequency (Sense Speed) and pulse interval for real-time field surveys.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isPersian) "نرخ نمونه‌برداری:" else "Default Sampling Rate:", color = TextPrimary, fontSize = 13.sp)
                        Text("$sensorSpeedHz Hz (${1000 / sensorSpeedHz} ms)", color = GeoAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Slider(
                        value = sensorSpeedHz.toFloat(),
                        onValueChange = { sensorSpeedHz = it.toInt() },
                        valueRange = 5f..100f,
                        steps = 18,
                        colors = SliderDefaults.colors(thumbColor = GeoAmber, activeTrackColor = GeoAmber),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            10 to if (isPersian) "دقیق / آهسته" else "Slow / Precision",
                            25 to if (isPersian) "استاندارد" else "Standard",
                            50 to if (isPersian) "گام سریع" else "Fast Walk"
                        ).forEach { (hz, label) ->
                            FilterChip(
                                selected = sensorSpeedHz == hz,
                                onClick = { sensorSpeedHz = hz },
                                label = { Text("$hz Hz - $label", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GeoAmber.copy(alpha = 0.25f),
                                    selectedLabelColor = GeoAmber
                                )
                            )
                        }
                    }
                }
            }

            // Bluetooth Sensor Hardware Link
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = GeoCyan)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.bluetoothSensor, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isPersian)
                            "پشتیبانی از BLE (Nordic UART, ESP32, nRF52) و بلوتوث کلاسیک SPP (HC-05, HC-06, Arduino)."
                        else
                            "Supports BLE (Nordic UART Service, ESP32, nRF52) and Classic Bluetooth SPP (HC-05, HC-06, Arduino).",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.autoConnectLastSensor, color = TextPrimary, fontSize = 13.sp)
                        Switch(
                            checked = bluetoothAutoConnect,
                            onCheckedChange = { bluetoothAutoConnect = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = GeoCyan, checkedTrackColor = GeoCyan.copy(alpha = 0.4f))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.zigZagWalk, color = TextPrimary, fontSize = 13.sp)
                        Switch(
                            checked = useBoustrophedonZigZag,
                            onCheckedChange = { useBoustrophedonZigZag = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = GeoCyan, checkedTrackColor = GeoCyan.copy(alpha = 0.4f))
                        )
                    }
                }
            }
            // Soil Profile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Landscape, contentDescription = null, tint = GeoCyan)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.soilProfileAndAttenuation, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Text(
                        if (isPersian)
                            "ترکیب خاک زمین بر تضعیف امواج الکترومغناطیسی و دقت تخمین عمق عوارض زیرسطحی تأثیرگذار است."
                        else
                            "Soil composition affects electromagnetic signal dampening and calculated depth inversion.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    val presets = SoilProfile.DEFAULT_SOILS.take(4)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.forEach { profile ->
                            val soilNameFa = when (profile.id) {
                                "dry_sand" -> "ماسه خشک (Dry Sand)"
                                "wet_loam" -> "خاک لوم مرطوب (Wet Loam)"
                                "clay_heavy" -> "خاک رس سنگین (Clay Heavy)"
                                "gravel_mixed" -> "شن و سنگ‌ریزه (Gravel)"
                                else -> profile.name
                            }
                            FilterChip(
                                selected = selectedSoil.id == profile.id,
                                onClick = { selectedSoil = profile },
                                label = {
                                    Text("${if (isPersian) soilNameFa else profile.name} (α: ${profile.attenuationFactor})", fontSize = 12.sp)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GeoCyan.copy(alpha = 0.25f),
                                    selectedLabelColor = GeoCyan
                                )
                            )
                        }
                    }
                }
            }

            // Calibration & Sensor Elevation
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = GeoCyan)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.sensorProbeElevation, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = sensorHeightCm,
                        onValueChange = { sensorHeightCm = it },
                        label = { Text(strings.elevationCm) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeoCyan,
                            focusedLabelColor = GeoCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.acousticToneFeedback, color = TextPrimary, fontSize = 13.sp)
                        Switch(
                            checked = soundFeedback,
                            onCheckedChange = { soundFeedback = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = GeoCyan, checkedTrackColor = GeoCyan.copy(alpha = 0.4f))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.hapticVibration, color = TextPrimary, fontSize = 13.sp)
                        Switch(
                            checked = hapticFeedback,
                            onCheckedChange = { hapticFeedback = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = GeoCyan, checkedTrackColor = GeoCyan.copy(alpha = 0.4f))
                        )
                    }
                }
            }

            // Scientific Ethics & Methodology Disclaimer
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, GeoAmber.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = GeoAmber)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.scientificEthicsTitle, color = GeoAmber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        strings.scientificEthicsDescription,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
