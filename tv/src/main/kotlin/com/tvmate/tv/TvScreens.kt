package com.tvmate.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.tvmate.shared.data.ChannelQuery
import com.tvmate.shared.data.MediaRef
import com.tvmate.shared.data.net.StationDto
import com.tvmate.shared.link.TvStatus
import com.tvmate.shared.ui.BigButton
import com.tvmate.shared.ui.ButtonStyle
import com.tvmate.shared.ui.ChannelLogo
import com.tvmate.shared.ui.Equalizer
import com.tvmate.shared.ui.Ic
import com.tvmate.shared.ui.LIcon
import com.tvmate.shared.ui.MediaRow
import com.tvmate.shared.ui.PlayDot
import com.tvmate.shared.ui.SectionLabel
import com.tvmate.shared.ui.Tag
import com.tvmate.shared.ui.Toggle
import com.tvmate.shared.ui.TvMateColors
import com.tvmate.shared.ui.TvMateFonts
import com.tvmate.shared.ui.focusRing
import com.tvmate.shared.ui.raised
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvRoot(host: TvHost) {
    val screen by host.screen.collectAsState()
    val paired by host.pairedPhones.collectAsState()
    val skipped by host.pairingSkipped.collectAsState()
    Box(Modifier.fillMaxSize().background(TvMateColors.Chassis)) {
        when {
            screen == TvScreen.Player -> PlayerScreen(host)
            paired == 0 && !skipped -> PairingScreen(host)
            paired >= 0 -> HomeScreen(host)
        }
    }
}

// ---------------------------------------------------------------- pairing

@Composable
fun PairingScreen(host: TvHost) {
    val code by host.pairingCode.collectAsState()
    Column(
        Modifier.fillMaxSize().background(TvMateColors.Ink).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LIcon(Ic.TvMinimal, Modifier.size(56.dp), TvMateColors.Orange)
        Spacer(Modifier.height(16.dp))
        Text("PAIR WITH YOUR PHONE", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 44.sp), color = Color.White)
        Spacer(Modifier.height(24.dp))
        CodeBoxes(code)
        Spacer(Modifier.height(24.dp))
        Text("Open TVMate on your phone, choose this TV, and enter the code.", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp), color = Color.White.copy(alpha = 0.8f))
        Text("Both devices must be on the same Wi-Fi.", style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp), color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(32.dp))
        val fr = remember { FocusRequester() }
        BigButton("Browse on this TV instead", { host.pairingSkipped.value = true }, Modifier.width(360.dp).focusRequester(fr), style = ButtonStyle.Orange)
        LaunchedEffect(Unit) { fr.requestFocus() }
    }
}

@Composable
private fun CodeBoxes(code: String, big: Boolean = true) {
    val w = if (big) 72.dp else 44.dp
    val h = if (big) 96.dp else 58.dp
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        code.forEach { ch ->
            Box(Modifier.size(w, h).background(TvMateColors.Face, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text(ch.toString(), style = MaterialTheme.typography.headlineLarge.copy(fontFamily = TvMateFonts.Mono, fontSize = if (big) 54.sp else 30.sp), color = TvMateColors.Ink)
            }
        }
    }
}

// ---------------------------------------------------------------- home

private enum class HomeTab(val label: String, val icon: Int) {
    TV("Live TV", Ic.TvMinimal), RADIO("Radio", Ic.Radio), SAVED("Saved", Ic.Star), SETTINGS("Settings", Ic.Settings)
}

@Composable
fun HomeScreen(host: TvHost) {
    var tab by remember { mutableStateOf(HomeTab.TV) }
    val now by host.nowPlaying.collectAsState()
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(240.dp).fillMaxHeight().background(TvMateColors.Ink).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TVMATE", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp), color = Color.White)
            Spacer(Modifier.height(16.dp))
            HomeTab.entries.forEach { t -> RailItem(t.label, t.icon, selected = t == tab) { tab = t } }
            Spacer(Modifier.weight(1f))
            val n = now
            if (n != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).focusRing().clickable { host.screen.value = TvScreen.Player }.padding(12.dp),
                ) {
                    SectionLabel("Now playing")
                    Text(n.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(28.dp)) {
            when (tab) {
                HomeTab.TV -> TvList(host)
                HomeTab.RADIO -> RadioList(host)
                HomeTab.SAVED -> SavedList(host)
                HomeTab.SETTINGS -> SettingsPane(host)
            }
        }
    }
}

@Composable
private fun RailItem(label: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) TvMateColors.Orange else Color.Transparent).focusRing(shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LIcon(icon, Modifier.size(22.dp), Color.White)
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp), color = Color.White)
    }
}

@Composable
private fun PaneTitle(title: String, caption: String? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
        Text(title.uppercase(), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp))
        Spacer(Modifier.weight(1f))
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelMedium, color = TvMateColors.Mute)
    }
}

@Composable
private fun TvList(host: TvHost) {
    val filters by host.graph.settings.filters.collectAsState(initial = com.tvmate.shared.data.ChannelFilters())
    val channels by remember(filters) { host.graph.catalog.channels(ChannelQuery(filters = filters, limit = 400)) }.collectAsState(initial = emptyList())
    val total by host.graph.catalog.channelCount().collectAsState(initial = 0)
    Column {
        PaneTitle("Live TV", "$total channels")
        if (channels.isEmpty()) Text(if (total == 0) "Loading the channel list… this takes a minute the first time." else "No channels match your filters.", style = MaterialTheme.typography.bodyLarge, color = TvMateColors.Mute)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(channels, key = { it.id }) { c ->
                MediaRow(
                    title = c.name, logoUrl = c.logoUrl, subtitle = c.country,
                    tags = {
                        if (c.geoBlocked) Tag("Geo-blocked", TvMateColors.Fault, Ic.Ban)
                        if (c.not247) Tag("Not 24/7", TvMateColors.Amber, Ic.Clock)
                    },
                    trailing = { PlayDot() },
                    onClick = { host.playChannel(c) },
                )
            }
        }
    }
}

@Composable
private fun RadioList(host: TvHost) {
    var stations by remember { mutableStateOf<List<StationDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { host.graph.radio.search(limit = 100) }.onSuccess { stations = it }.onFailure { error = it.message ?: "Couldn’t reach Radio Browser" }
    }
    Column {
        PaneTitle("Radio", "most listened")
        error?.let { Text(it, color = TvMateColors.Fault, style = MaterialTheme.typography.bodyLarge) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stations, key = { it.stationuuid }) { s ->
                MediaRow(
                    title = s.name.trim(), logoUrl = s.favicon.ifBlank { null },
                    subtitle = s.country.ifBlank { s.countrycode },
                    tags = { if (s.bitrate > 0) Tag("${s.bitrate}k") },
                    trailing = { PlayDot() },
                    onClick = { host.playStation(s) },
                )
            }
        }
    }
}

@Composable
private fun SavedList(host: TvHost) {
    val favorites by host.graph.user.favorites().collectAsState(initial = emptyList())
    Column {
        PaneTitle("Saved")
        if (favorites.isEmpty()) Text("Favorites you add on your phone appear here.", style = MaterialTheme.typography.bodyLarge, color = TvMateColors.Mute)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(favorites, key = { it.key }) { f ->
                MediaRow(
                    title = f.name, logoUrl = f.logoUrl, subtitle = if (f.kind == "tv") "TV" else "Radio",
                    trailing = { PlayDot() },
                    onClick = {
                        host.playRef(MediaRef(if (f.kind == "tv") MediaRef.Kind.TV else MediaRef.Kind.RADIO, f.itemId, f.name, f.subtitle, f.logoUrl, f.streamUrl))
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsPane(host: TvHost) {
    val scope = rememberCoroutineScope()
    val code by host.pairingCode.collectAsState()
    val paired by host.pairedPhones.collectAsState()
    val filters by host.graph.settings.filters.collectAsState(initial = com.tvmate.shared.data.ChannelFilters())
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PaneTitle("Settings") }
        item {
            Column(Modifier.fillMaxWidth().raised().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Pair a phone")
                CodeBoxes(code, big = false)
                Text("Enter this code in TVMate on your phone. $paired phone(s) paired.", style = MaterialTheme.typography.bodyMedium, color = TvMateColors.Ink2)
                BigButton("Forget all phones", { scope.launch { host.graph.settings.clearPhoneTokens() } }, Modifier.width(300.dp), style = ButtonStyle.Outline, icon = Ic.Close)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().raised().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionLabel("Channel list")
                ToggleRow("Show geo-blocked channels", "May not play in your country", filters.showGeoBlocked) { scope.launch { host.graph.settings.setShowGeoBlocked(it) } }
                ToggleRow("Show not 24/7 channels", "Channels that go off-air", filters.showNot247) { scope.launch { host.graph.settings.setShowNot247(it) } }
                ToggleRow("Show adult channels", "Off by default", filters.showNsfw) { scope.launch { host.graph.settings.setShowNsfw(it) } }
            }
        }
        item {
            Text(
                "TVMate carries no channels of its own. It lists public streams from iptv-org and Radio Browser. Takedown requests: takedown@example.com",
                style = MaterialTheme.typography.labelSmall, color = TvMateColors.Mute,
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
            Text(caption, style = MaterialTheme.typography.labelSmall, color = TvMateColors.Mute)
        }
        Toggle(checked, onChange)
    }
}

// ---------------------------------------------------------------- player

@Composable
fun PlayerScreen(host: TvHost) {
    val state by host.state.collectAsState()
    var poke by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }
    LaunchedEffect(state.title, state.status, poke) {
        overlay = true
        if (state.status == TvStatus.PLAYING || state.status == TvStatus.PAUSED) {
            delay(5_000)
            overlay = false
        }
    }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(fr).focusable().onKeyEvent { ev ->
            if (ev.type == KeyEventType.KeyDown) {
                poke++
                if (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.MediaPlayPause) {
                    host.togglePause(); true
                } else false
            } else false
        },
    ) {
        if (state.kind == "radio") RadioNowPlaying(state.title.orEmpty(), state.meta, state.logoUrl, state.status)
        else AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { player = host.player.player; useController = false } },
            modifier = Modifier.fillMaxSize(),
        )

        val problem = state.status == TvStatus.RETRYING || state.status == TvStatus.FAILED
        if (problem) {
            Banner(
                title = if (state.status == TvStatus.FAILED) "This channel isn’t playing" else "This stream stopped",
                body = state.message.orEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            )
        } else if (overlay || state.status == TvStatus.BUFFERING) {
            InfoBar(state.title.orEmpty(), state.meta, state.logoUrl, state.status, state.streamIndex, state.streamCount, Modifier.align(Alignment.BottomCenter).padding(32.dp))
        }
    }
}

@Composable
private fun InfoBar(title: String, meta: String?, logo: String?, status: TvStatus, index: Int, total: Int, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TvMateColors.Ink.copy(alpha = 0.93f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ChannelLogo(title, logo, size = 56.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp), color = Color.White, maxLines = 1)
            val sub = listOfNotNull(meta, if (total > 1) "Stream ${index + 1} of $total" else null).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp), color = Color.White.copy(alpha = 0.75f))
        }
        val label = when (status) { TvStatus.BUFFERING -> "BUFFERING"; TvStatus.PAUSED -> "PAUSED"; else -> "LIVE" }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.background(TvMateColors.Orange, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
private fun Banner(title: String, body: String, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TvMateColors.Ink.copy(alpha = 0.95f)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LIcon(Ic.Alert, Modifier.size(34.dp), TvMateColors.Amber)
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp), color = Color.White)
            Text(body, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp), color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun RadioNowPlaying(title: String, meta: String?, logo: String?, status: TvStatus) {
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A2824), Color(0xFF141311)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ChannelLogo(title, logo, size = 140.dp)
            Text(title, style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp), color = Color.White, maxLines = 2)
            if (!meta.isNullOrBlank()) Text(meta, style = MaterialTheme.typography.labelMedium.copy(fontSize = 18.sp), color = Color.White.copy(alpha = 0.75f))
            if (status == TvStatus.PLAYING) Equalizer(height = 48.dp, bars = 14) else Text(if (status == TvStatus.PAUSED) "PAUSED" else "BUFFERING…", style = MaterialTheme.typography.labelMedium, color = TvMateColors.Orange)
            Spacer(Modifier.height(8.dp))
            Text("OK to pause · Back for menu", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
        }
    }
}
