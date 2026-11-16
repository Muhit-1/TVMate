package com.tvmate.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvmate.shared.data.MediaRef
import com.tvmate.shared.data.db.StreamEntity
import com.tvmate.shared.link.TvStatus
import com.tvmate.shared.ui.*
import kotlinx.coroutines.delay

@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

fun Modifier.clickableEnabled(enabled: Boolean, onClick: () -> Unit): Modifier = clickable(enabled = enabled, onClick = onClick)

@Composable
fun PhoneApp(vm: PhoneViewModel = viewModel()) {
    val tab by vm.tab.collectAsState()
    val showFind by vm.showFindTv.collectAsState()
    val pairTarget by vm.pairingTarget.collectAsState()
    val nowOpen by vm.nowPlayingOpen.collectAsState()
    val options by vm.optionsFor.collectAsState()
    val picker by vm.picker.collectAsState()
    val toast by vm.toast.collectAsState()
    val paired by vm.link.paired.collectAsState()
    val atvPair by vm.atvPairUi.collectAsState()

    BackHandler(enabled = atvPair != AtvPairUi.Idle) { vm.cancelAtvPairing() }
    BackHandler(enabled = pairTarget != null) { vm.pairingTarget.value = null }
    BackHandler(enabled = pairTarget == null && showFind && paired != null) { vm.showFindTv.value = false }
    BackHandler(enabled = nowOpen && !showFind) { vm.nowPlayingOpen.value = false }

    Box(Modifier.fillMaxSize().background(TvMateColors.Chassis).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.Remote -> RemoteScreen(vm)
                    Tab.TV -> LiveTvScreen(vm)
                    Tab.Radio -> RadioScreen(vm)
                    Tab.Saved -> SavedScreen(vm)
                    Tab.Settings -> SettingsScreen(vm)
                }
            }
            MiniPlayer(vm)
            NavBar(tab) { vm.tab.value = it }
        }
        if (nowOpen) NowPlayingScreen(vm)
        if (showFind) FindTvScreen(vm)
        pairTarget?.let { PairScreen(vm, it) }
        if (atvPair != AtvPairUi.Idle) AtvPairScreen(vm, atvPair)
        options?.let { OptionsSheet(vm, it) }
        picker?.let { PickerSheet(it) { vm.picker.value = null } }
        toast?.let { msg ->
            LaunchedEffect(msg) { delay(3_000); if (vm.toast.value == msg) vm.toast.value = null }
            Text(
                msg, style = MaterialTheme.typography.labelLarge, color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 130.dp, start = 24.dp, end = 24.dp)
                    .background(TvMateColors.Ink, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ nav + mini player

@Composable
private fun NavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val items = listOf(Tab.Remote to (Ic.Gamepad to "Remote"), Tab.TV to (Ic.TvMinimal to "TV"), Tab.Radio to (Ic.Radio to "Radio"), Tab.Saved to (Ic.Star to "Saved"), Tab.Settings to (Ic.Settings to "Settings"))
    Row(Modifier.fillMaxWidth().background(TvMateColors.Panel).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (tab, pair) ->
            val on = tab == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) TvMateColors.Face else Color.Transparent)
                    .clickable { onSelect(tab) }.padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                LIcon(pair.first, Modifier.size(21.dp), if (on) TvMateColors.Orange else TvMateColors.Mute)
                Text(pair.second, style = MaterialTheme.typography.labelSmall, color = if (on) TvMateColors.Ink else TvMateColors.Mute)
            }
        }
    }
}

@Composable
private fun MiniPlayer(vm: PhoneViewModel) {
    val s by vm.tvState.collectAsState()
    val state = s ?: return
    val title = state.title ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).raised(RoundedCornerShape(18.dp), TvMateColors.Ink)
            .clip(RoundedCornerShape(18.dp)).clickable { vm.nowPlayingOpen.value = true }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChannelLogo(title, state.logoUrl, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
            Text(
                "On ${state.tvName} · " + when (state.status) { TvStatus.PLAYING -> "playing"; TvStatus.PAUSED -> "paused"; TvStatus.BUFFERING -> "buffering"; TvStatus.RETRYING -> "retrying"; TvStatus.FAILED -> "stopped"; TvStatus.IDLE -> "idle" },
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1,
            )
        }
        RoundIcon(if (state.status == TvStatus.PAUSED) Ic.Play else Ic.Pause, { vm.togglePause() }, size = 38.dp, container = TvMateColors.Orange, tint = Color.White, description = "Play or pause")
    }
}

// ------------------------------------------------------------------ find + pair

@Composable
fun FindTvScreen(vm: PhoneViewModel) {
    val found by vm.link.found.collectAsState()
    val paired by vm.link.paired.collectAsState()
    val context = LocalContext.current
    var ip by remember { mutableStateOf("") }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.link.startDiscovery() }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else vm.link.startDiscovery()
    }

    Column(Modifier.fillMaxSize().background(TvMateColors.Chassis).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (paired != null) RoundIcon(Ic.ArrowLeft, { vm.showFindTv.value = false }, description = "Back")
            Column(Modifier.weight(1f)) {
                Text("FIND YOUR TV", style = MaterialTheme.typography.headlineLarge)
                Text("Same Wi-Fi as your phone", style = MaterialTheme.typography.labelMedium, color = TvMateColors.Mute)
            }
        }
        TunerDial(reading = "${found.size}", caption = if (found.isEmpty()) "Scanning for TVs…" else "TV${if (found.size == 1) "" else "s"} found")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(found, key = { it.host }) { tv ->
                MediaRow(
                    title = tv.name, subtitle = tv.host, showLogo = false,
                    tags = { if (paired?.host == tv.host) Tag("Paired", TvMateColors.Locked, Ic.CheckCircle) },
                    trailing = { LIcon(Ic.Right, Modifier.size(20.dp), TvMateColors.Mute) },
                    onClick = { vm.startPairing(tv) },
                )
            }
            if (found.isEmpty()) item {
                HelpCard("Open TVMate on your TV first. It shows a code and starts listening.")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SearchField(ip, { ip = it }, "Or type the TV’s IP address", onSearch = { vm.link.addManual(ip); ip = "" }) }
            RoundIcon(Ic.Plus, { vm.link.addManual(ip); ip = "" }, size = 46.dp, container = TvMateColors.Ink, tint = Color.White, description = "Add TV by IP")
        }
        HelpCard("Don’t see your TV? Allow Nearby devices for TVMate in Android settings, and turn the TV on.", Modifier.padding(bottom = 12.dp))
    }
}

@Composable
fun PairScreen(vm: PhoneViewModel, tv: FoundTv) {
    val error by vm.pairError.collectAsState()
    val busy by vm.pairing.collectAsState()
    var code by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Column(Modifier.fillMaxSize().background(TvMateColors.Chassis).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            RoundIcon(Ic.ArrowLeft, { vm.pairingTarget.value = null }, description = "Back")
            Led(tv.name)
        }
        Text("ENTER THE CODE", style = MaterialTheme.typography.headlineLarge)
        Text("Type the 6 characters shown on your TV. You only do this once; TVMate remembers this TV.", style = MaterialTheme.typography.bodyLarge, color = TvMateColors.Ink2)
        Spacer(Modifier.height(4.dp))
        CodeInput(code, { code = it }, 6, Modifier.focusRequester(fr), onDone = { if (code.length == 6) vm.submitCode(code) })
        HelpCard("The code is sent only to your TV, on your home network.", icon = Ic.Lock)
        error?.let { Alert("Couldn’t pair", it) }
        if (busy) Led("Waiting for the TV to confirm…", modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.weight(1f))
        BigButton("Pair", { vm.submitCode(code) }, icon = Ic.Check, style = ButtonStyle.Orange, enabled = code.length == 6 && !busy)
        BigButton("Cancel", { vm.pairingTarget.value = null }, style = ButtonStyle.Outline, modifier = Modifier.padding(bottom = 12.dp))
    }
}

// ------------------------------------------------------------------ sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsSheet(vm: PhoneViewModel, ref: MediaRef) {
    val favorites by vm.favoriteKeys.collectAsState()
    val tv by vm.tvState.collectAsState()
    var streams by remember { mutableStateOf<List<StreamEntity>>(emptyList()) }
    LaunchedEffect(ref.id) { if (ref.kind == MediaRef.Kind.TV) streams = vm.streamsFor(ref.id) }
    ModalBottomSheet(onDismissRequest = { vm.optionsFor.value = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TvMateColors.Chassis) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(ref.name.uppercase(), style = MaterialTheme.typography.headlineMedium)
            SheetOption(Ic.Star, if (ref.key in favorites) "Remove from favorites" else "Add to favorites") { vm.toggleFavorite(ref); vm.optionsFor.value = null }
            if (ref.kind == MediaRef.Kind.TV && streams.isNotEmpty()) {
                SectionLabel("Stream")
                val current = if (tv?.itemId == ref.id) tv?.streamIndex ?: 0 else -1
                streams.forEachIndexed { i, s ->
                    SheetOption(
                        Ic.Layers, "Stream ${i + 1}", selected = i == current,
                        caption = listOfNotNull(s.quality, if (s.geoBlocked) "geo-blocked" else null, if (s.not247) "not 24/7" else null, if (s.referrer != null) "needs referrer" else null).joinToString(" · ").ifBlank { null },
                    ) { vm.selectStream(ref.id, i); vm.optionsFor.value = null }
                }
                SheetOption(Ic.EyeOff, "Hide this channel", caption = "Undo in Settings → Hidden channels", danger = true) { vm.hideChannel(ref.id, ref.name) }
            }
        }
    }
}

@Composable
private fun SheetOption(icon: Int, title: String, selected: Boolean = false, caption: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val fg = if (selected) Color.White else if (danger) TvMateColors.Fault else TvMateColors.Ink
    Row(
        Modifier.fillMaxWidth().raised(shape, if (selected) TvMateColors.Ink else TvMateColors.Panel).focusRing(shape).clip(shape).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LIcon(icon, Modifier.size(20.dp), fg)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = fg)
            if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White.copy(alpha = 0.75f) else TvMateColors.Mute)
        }
        if (selected) LIcon(Ic.Check, Modifier.size(18.dp), TvMateColors.Orange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(picker: Picker, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, picker) { picker.items.filter { q.isBlank() || it.second.contains(q, ignoreCase = true) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TvMateColors.Chassis) {
        Column(Modifier.padding(horizontal = 18.dp).fillMaxHeight(0.8f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(picker.title.uppercase(), style = MaterialTheme.typography.headlineMedium)
            SearchField(q, { q = it }, "Search ${picker.title.lowercase()}")
            if (picker.items.isEmpty()) Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = TvMateColors.Mute)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { SheetOption(Ic.Globe, "Any", selected = picker.selected.isEmpty()) { picker.onPick("") } }
                items(shown, key = { it.first }) { (code, label) -> SheetOption(Ic.Globe, label, selected = code == picker.selected) { picker.onPick(code) } }
            }
        }
    }
}

@Composable
fun AtvPairScreen(vm: PhoneViewModel, ui: AtvPairUi) {
    var code by remember { mutableStateOf("") }
    val paired by vm.link.paired.collectAsState()
    val fr = remember { FocusRequester() }
    LaunchedEffect(ui) { if (ui == AtvPairUi.WaitingForCode) fr.requestFocus() }
    Column(Modifier.fillMaxSize().background(TvMateColors.Chassis).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            RoundIcon(Ic.ArrowLeft, { vm.cancelAtvPairing() }, description = "Back")
            Led(paired?.name ?: "TV")
        }
        Text("FULL REMOTE", style = MaterialTheme.typography.headlineLarge)
        when (ui) {
            AtvPairUi.Starting -> {
                Text("Connecting to your TV…", style = MaterialTheme.typography.bodyLarge, color = TvMateColors.Ink2)
                Led("A code will appear on the TV screen")
            }
            AtvPairUi.WaitingForCode, AtvPairUi.Verifying -> {
                Text("Your TV is showing a 6-character code. Type it here. Digits 0–9 and letters A–F.", style = MaterialTheme.typography.bodyLarge, color = TvMateColors.Ink2)
                Spacer(Modifier.height(4.dp))
                CodeInput(code, { code = it }, 6, Modifier.focusRequester(fr), onDone = { if (code.length == 6) vm.submitAtvCode(code) })
                HelpCard("This uses Google’s own remote service on your TV, so it works with any app. TVMate never sees your TV’s password.", icon = Ic.Lock)
                if (ui == AtvPairUi.Verifying) Led("Checking the code…", modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            is AtvPairUi.Error -> Alert("Couldn’t pair the full remote", ui.message)
            AtvPairUi.Idle -> {}
        }
        Spacer(Modifier.weight(1f))
        if (ui is AtvPairUi.Error) BigButton("Try again", { code = ""; vm.startAtvPairing() }, icon = Ic.Refresh, style = ButtonStyle.Orange)
        else if (ui == AtvPairUi.WaitingForCode || ui == AtvPairUi.Verifying) BigButton("Pair", { vm.submitAtvCode(code) }, icon = Ic.Check, style = ButtonStyle.Orange, enabled = code.length == 6 && ui != AtvPairUi.Verifying)
        BigButton("Cancel", { vm.cancelAtvPairing() }, style = ButtonStyle.Outline, modifier = Modifier.padding(bottom = 12.dp))
    }
}
