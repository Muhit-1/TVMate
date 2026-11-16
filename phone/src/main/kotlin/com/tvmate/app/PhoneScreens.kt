package com.tvmate.app

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvmate.app.atv.AtvKey
import com.tvmate.app.atv.AtvState
import com.tvmate.shared.data.MediaRef
import com.tvmate.shared.data.db.FavoriteEntity
import com.tvmate.shared.data.db.RecentEntity
import com.tvmate.shared.link.TvStatus
import com.tvmate.shared.ui.*

// ------------------------------------------------------------------ shared bits

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = MaterialTheme.typography.headlineLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = TvMateColors.Mute)
        }
        trailing()
    }
}

private fun FavoriteEntity.toRef() = MediaRef(if (kind == "tv") MediaRef.Kind.TV else MediaRef.Kind.RADIO, itemId, name, subtitle, logoUrl, streamUrl)
private fun RecentEntity.toRef() = MediaRef(if (kind == "tv") MediaRef.Kind.TV else MediaRef.Kind.RADIO, itemId, name, subtitle, logoUrl, streamUrl)

// ------------------------------------------------------------------ Remote

@Composable
fun RemoteScreen(vm: PhoneViewModel) {
    val status by vm.link.status.collectAsState()
    val paired by vm.link.paired.collectAsState()
    val state by vm.tvState.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Remote", paired?.name) {
            when (status) {
                LinkStatus.Connected -> Led("Connected", TvMateColors.Locked)
                LinkStatus.Connecting -> Led("Connecting…")
                is LinkStatus.Offline -> Led("Offline", TvMateColors.Fault)
                LinkStatus.NotPaired -> Led("No TV", Color(0xFF8D8A82))
            }
        }
        when (status) {
            LinkStatus.NotPaired -> {
                HelpCard("Pair your TV once and this screen becomes its remote. Install TVMate on the TV first.")
                BigButton("Find your TV", { vm.showFindTv.value = true }, icon = Ic.TvMinimal, style = ButtonStyle.Orange)
            }
            LinkStatus.Connecting, is LinkStatus.Offline -> {
                Alert("Can’t reach ${paired?.name ?: "your TV"}", "It may be off, or on a different Wi-Fi. TVMate tries again every 5 seconds.", icon = Ic.WifiOff)
                BigButton("Find your TV again", { vm.showFindTv.value = true }, icon = Ic.Refresh, style = ButtonStyle.Outline)
            }
            LinkStatus.Connected -> RemoteBody(vm, state?.volumePercent ?: 0)
        }
    }
}

@Composable
private fun RemoteBody(vm: PhoneViewModel, tvmateVolume: Int) {
    val atvPaired by vm.atvPaired.collectAsState()
    val atvState by vm.atv.state.collectAsState()
    val atvVolume by vm.atv.volume.collectAsState()
    val atvReady = atvState == AtvState.Ready
    val volume = atvVolume?.let { if (it.max > 0) it.level * 100 / it.max else 0 } ?: tvmateVolume
    if (!atvPaired) {
        Column(Modifier.fillMaxWidth().raised().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Get the full remote", style = MaterialTheme.typography.titleMedium)
            Text("Pair once more to use Power, Home and control any app on the TV, not just TVMate.", style = MaterialTheme.typography.bodyMedium, color = TvMateColors.Ink2)
            BigButton("Pair the full remote", { vm.startAtvPairing() }, icon = Ic.Gamepad, style = ButtonStyle.Orange)
        }
    } else if (!atvReady) {
        Led(if (atvState is AtvState.Failed) "Full remote offline. Retrying…" else "Connecting the full remote…", TvMateColors.Amber)
    }
    Column(Modifier.fillMaxWidth().raised(RoundedCornerShape(26.dp)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundIcon(Ic.Power, { vm.systemKey(AtvKey.POWER) }, size = 46.dp, container = TvMateColors.Ink, tint = TvMateColors.Orange, enabled = atvReady, description = "Power")
            RoundIcon(Ic.VolumeOff, { vm.mute() }, size = 46.dp, description = "Mute")
        }
        // D-pad
        Box(Modifier.size(196.dp).sunken(CircleShape), contentAlignment = Alignment.Center) {
            RoundIcon(Ic.Up, { vm.key(KeyEvent.KEYCODE_DPAD_UP) }, Modifier.align(Alignment.TopCenter).padding(top = 8.dp), size = 46.dp, container = Color.Transparent, description = "Up")
            RoundIcon(Ic.Down, { vm.key(KeyEvent.KEYCODE_DPAD_DOWN) }, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), size = 46.dp, container = Color.Transparent, description = "Down")
            RoundIcon(Ic.Left, { vm.key(KeyEvent.KEYCODE_DPAD_LEFT) }, Modifier.align(Alignment.CenterStart).padding(start = 8.dp), size = 46.dp, container = Color.Transparent, description = "Left")
            RoundIcon(Ic.Right, { vm.key(KeyEvent.KEYCODE_DPAD_RIGHT) }, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), size = 46.dp, container = Color.Transparent, description = "Right")
            Box(
                Modifier.size(80.dp).raised(CircleShape, TvMateColors.Orange).clip(CircleShape).background(TvMateColors.Orange)
                    .then(Modifier.clickableNoRipple { vm.key(KeyEvent.KEYCODE_DPAD_CENTER) }),
                contentAlignment = Alignment.Center,
            ) { Text("OK", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp), color = Color.White) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeyButton("Back", Ic.Back, Modifier.weight(1f)) { vm.key(KeyEvent.KEYCODE_BACK) }
            KeyButton("Home", Ic.Home, Modifier.weight(1f), enabled = atvReady) { vm.systemKey(AtvKey.HOME) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            RoundIcon(Ic.Minus, { vm.volumeStep(-1) }, size = 50.dp, description = "Volume down")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$volume", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp))
                SectionLabel("Volume")
            }
            RoundIcon(Ic.Plus, { vm.volumeStep(1) }, size = 50.dp, description = "Volume up")
        }
    }
    if (!atvPaired) HelpCard("Until the full remote is paired, D-pad, OK, Back and volume only work inside the TVMate TV app. Power and Home need the full remote.")
}

@Composable
private fun KeyButton(text: String, icon: Int, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.raised(shape, TvMateColors.Face).focusRing(shape).clip(shape).clickableEnabled(enabled, onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(icon, Modifier.size(17.dp), if (enabled) TvMateColors.Ink else TvMateColors.Mute.copy(alpha = 0.5f))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) TvMateColors.Ink else TvMateColors.Mute.copy(alpha = 0.5f))
    }
}

// ------------------------------------------------------------------ Live TV

@Composable
fun LiveTvScreen(vm: PhoneViewModel) {
    val channels by vm.channels.collectAsState()
    val count by vm.channelCount.collectAsState()
    val query by vm.tvQuery.collectAsState()
    val country by vm.tvCountry.collectAsState()
    val category by vm.tvCategory.collectAsState()
    val language by vm.tvLanguage.collectAsState()
    val countries by vm.countries.collectAsState()
    val categories by vm.categories.collectAsState()
    val languages by vm.languages.collectAsState()
    val favorites by vm.favoriteKeys.collectAsState()
    val status by vm.catalogStatus.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Live TV", if (count == 0) status.ifBlank { "Loading channel list…" } else "$count channels") {
            RoundIcon(Ic.Refresh, { vm.refreshCatalog(true) }, description = "Refresh channel list")
        }
        SearchField(query, { vm.tvQuery.value = it }, "Search channels")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Chip(countries.firstOrNull { it.code == country }?.name ?: "Country", icon = Ic.Globe, selected = country.isNotEmpty()) {
                    vm.picker.value = Picker("Country", countries.map { it.code to it.name }, country) { vm.tvCountry.value = it; vm.picker.value = null }
                }
            }
            item {
                Chip(categories.firstOrNull { it.id == category }?.name ?: "Category", icon = Ic.Filter, selected = category.isNotEmpty()) {
                    vm.picker.value = Picker("Category", categories.map { it.id to it.name }, category) { vm.tvCategory.value = it; vm.picker.value = null }
                }
            }
            item {
                Chip(languages.firstOrNull { it.code == language }?.name ?: "Language", icon = Ic.Languages, selected = language.isNotEmpty()) {
                    vm.picker.value = Picker("Language", languages.map { it.code to it.name }, language) { vm.tvLanguage.value = it; vm.picker.value = null }
                }
            }
            if (country.isNotEmpty() || category.isNotEmpty() || language.isNotEmpty()) item {
                Chip("Clear", icon = Ic.Close) { vm.tvCountry.value = ""; vm.tvCategory.value = ""; vm.tvLanguage.value = "" }
            }
        }
        if (channels.isEmpty()) {
            Text(
                if (count == 0) "The channel list downloads the first time you open TVMate. It takes about a minute." else "No channels match. Try a different search or clear the filters.",
                style = MaterialTheme.typography.bodyMedium, color = TvMateColors.Mute,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(channels, key = { it.id }) { c ->
                val ref = MediaRef(MediaRef.Kind.TV, c.id, c.name, c.country, c.logoUrl)
                MediaRow(
                    title = c.name, logoUrl = c.logoUrl,
                    subtitle = listOfNotNull(c.country, c.categories.split(",").firstOrNull()?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    tags = {
                        if (c.geoBlocked) Tag("Geo-blocked", TvMateColors.Fault, Ic.Ban)
                        if (c.not247) Tag("Not 24/7", TvMateColors.Amber, Ic.Clock)
                    },
                    trailing = {
                        if (ref.key in favorites) LIcon(Ic.Star, Modifier.size(18.dp), TvMateColors.Orange)
                        RoundIcon(Ic.Sliders, { vm.optionsFor.value = ref }, size = 34.dp, container = TvMateColors.Face, description = "Options")
                        PlayDot(dim = c.geoBlocked)
                    },
                    onLongClick = { vm.optionsFor.value = ref },
                    onClick = { vm.playChannel(c) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Radio

@Composable
fun RadioScreen(vm: PhoneViewModel) {
    val stations by vm.stations.collectAsState()
    val query by vm.radioQuery.collectAsState()
    val tag by vm.radioTag.collectAsState()
    val country by vm.radioCountry.collectAsState()
    val tags by vm.radioTags.collectAsState()
    val loading by vm.radioLoading.collectAsState()
    val error by vm.radioError.collectAsState()
    val favorites by vm.favoriteKeys.collectAsState()
    val tv by vm.tvState.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Radio", if (loading) "Searching…" else "${stations.size} stations")
        TunerDial(
            reading = if (tv?.kind == "radio") tv?.title.orEmpty() else "Tune in",
            caption = if (tv?.kind == "radio") "Playing on ${tv?.tvName}" else "Pick a station to play on your TV",
        )
        SearchField(query, { vm.radioQuery.value = it }, "Search stations")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Chip(if (country.isEmpty()) "Country" else country, icon = Ic.Globe, selected = country.isNotEmpty()) { vm.openCountryPicker() } }
            items(tags) { t -> Chip(t, icon = Ic.Music, selected = t == tag) { vm.radioTag.value = if (t == tag) "" else t } }
        }
        error?.let {
            Alert("Radio Browser isn’t answering", it)
            BigButton("Try again", { vm.retryRadio() }, icon = Ic.Refresh, style = ButtonStyle.Outline)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(stations, key = { it.stationuuid }) { s ->
                val ref = MediaRef(MediaRef.Kind.RADIO, s.stationuuid, s.name.trim(), null, s.favicon.ifBlank { null }, s.playUrl)
                MediaRow(
                    title = s.name.trim(), logoUrl = s.favicon.ifBlank { null },
                    subtitle = listOf(s.country.ifBlank { s.countrycode }, s.language).filter { it.isNotBlank() }.joinToString(" · "),
                    tags = { if (s.bitrate > 0) Tag("${s.bitrate}k") ; if (s.codec.isNotBlank()) Tag(s.codec) },
                    trailing = {
                        if (ref.key in favorites) LIcon(Ic.Star, Modifier.size(18.dp), TvMateColors.Orange)
                        RoundIcon(Ic.Star, { vm.toggleFavorite(ref) }, size = 34.dp, container = TvMateColors.Face, description = "Favorite")
                        PlayDot()
                    },
                    onClick = { vm.playStation(s) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Saved

@Composable
fun SavedScreen(vm: PhoneViewModel) {
    val favorites by vm.favorites.collectAsState()
    val recents by vm.recents.collectAsState()
    var seg by remember { mutableIntStateOf(0) }
    val refs = if (seg == 0) favorites.map { it.toRef() } else recents.map { it.toRef() }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Saved", "Quick access")
        Segmented(listOf("Favorites", "Recent"), seg, { seg = it })
        if (refs.isEmpty()) {
            HelpCard(if (seg == 0) "Long-press a channel, or tap the star on a station, to keep it here." else "Things you play on your TV show up here. The last 30 are kept.", icon = if (seg == 0) Ic.Star else Ic.History)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(refs, key = { it.key }) { ref ->
                MediaRow(
                    title = ref.name, logoUrl = ref.logoUrl,
                    subtitle = (if (ref.kind == MediaRef.Kind.TV) "TV" else "Radio") + (ref.subtitle?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    trailing = {
                        if (seg == 0) RoundIcon(Ic.Star, { vm.toggleFavorite(ref) }, size = 34.dp, container = TvMateColors.Face, tint = TvMateColors.Orange, description = "Remove from favorites")
                        PlayDot()
                    },
                    onLongClick = { if (ref.kind == MediaRef.Kind.TV) vm.optionsFor.value = ref },
                    onClick = { vm.playRef(ref, refs) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Settings

@Composable
fun SettingsScreen(vm: PhoneViewModel) {
    val paired by vm.link.paired.collectAsState()
    val status by vm.link.status.collectAsState()
    val filters by vm.filters.collectAsState()
    val hidden by vm.hiddenCount.collectAsState()
    val catalog by vm.catalogStatus.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { ScreenHeader("Settings") }
        item { SectionLabel("TV") }
        item {
            SettingsGroup {
                SettingRow(Ic.TvMinimal, paired?.name ?: "No TV paired", if (paired == null) "Pair a TV to use the remote" else "${paired?.host} · " + when (status) { LinkStatus.Connected -> "connected"; LinkStatus.Connecting -> "connecting"; else -> "offline" }) {
                    Chip(if (paired == null) "Pair" else "Change") { vm.showFindTv.value = true }
                }
                if (paired != null) SettingRow(Ic.Close, "Forget this TV", "You’ll enter a code again to pair") { Chip("Forget") { vm.forgetTv() } }
            }
        }
        item { SectionLabel("Channel list") }
        item {
            SettingsGroup {
                SettingRow(Ic.Ban, "Show geo-blocked", "May not play in your country") { Toggle(filters.showGeoBlocked, vm::setShowGeo) }
                SettingRow(Ic.Clock, "Show not 24/7", "Channels that go off-air") { Toggle(filters.showNot247, vm::setShowNot247) }
                SettingRow(Ic.Shield, "Show adult channels", "Off by default") { Toggle(filters.showNsfw, vm::setShowNsfw) }
                SettingRow(Ic.EyeOff, "Hidden channels", "$hidden hidden by you") { if (hidden > 0) Chip("Show all") { vm.unhideAll() } }
            }
        }
        item { SectionLabel("Data") }
        item {
            SettingsGroup {
                SettingRow(Ic.Refresh, "Refresh channel list", catalog.ifBlank { "Refreshes itself once a day" }) { Chip("Refresh") { vm.refreshCatalog(true) } }
            }
        }
        item {
            Text(
                "TVMate carries no channels of its own. It lists public streams from iptv-org and Radio Browser. Takedown requests: takedown@example.com",
                style = MaterialTheme.typography.labelSmall, color = TvMateColors.Mute, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().raised(RoundedCornerShape(16.dp)), content = content)
}

@Composable
private fun SettingRow(icon: Int, title: String, caption: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LIcon(icon, Modifier.size(19.dp), TvMateColors.Ink2)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp))
            Text(caption, style = MaterialTheme.typography.labelSmall, color = TvMateColors.Mute)
        }
        trailing()
    }
}

// ------------------------------------------------------------------ Now playing

@Composable
fun NowPlayingScreen(vm: PhoneViewModel) {
    val state by vm.tvState.collectAsState()
    val favorites by vm.favoriteKeys.collectAsState()
    val s = state
    Column(Modifier.fillMaxSize().background(TvMateColors.Chassis).padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            RoundIcon(Ic.Down, { vm.nowPlayingOpen.value = false }, description = "Close")
            Led("Playing on ${s?.tvName ?: "TV"}", TvMateColors.Locked)
        }
        if (s?.title == null) {
            HelpCard("Nothing is playing on the TV. Pick a channel or station to start.")
            return@Column
        }
        val isRadio = s.kind == "radio"
        val ref = MediaRef(if (isRadio) MediaRef.Kind.RADIO else MediaRef.Kind.TV, s.itemId.orEmpty(), s.title.orEmpty(), s.meta, s.logoUrl)
        Column(
            Modifier.fillMaxWidth().background(TvMateColors.Ink, RoundedCornerShape(22.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChannelLogo(s.title.orEmpty(), s.logoUrl, size = 54.dp)
                Text(if (isRadio) "RADIO" else "LIVE TV", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp), color = TvMateColors.Orange)
            }
            Text(s.title.orEmpty().uppercase(), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp, lineHeight = 36.sp), color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (s.status) { TvStatus.BUFFERING -> "BUFFERING"; TvStatus.PAUSED -> "PAUSED"; TvStatus.PLAYING -> "● LIVE"; TvStatus.RETRYING -> "RETRYING"; TvStatus.FAILED -> "STOPPED"; TvStatus.IDLE -> "IDLE" },
                    style = MaterialTheme.typography.labelMedium, color = Color.White,
                    modifier = Modifier.background(if (s.status == TvStatus.FAILED) TvMateColors.Fault else TvMateColors.Orange, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
                if (!s.meta.isNullOrBlank()) Text(s.meta.orEmpty(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            }
            if (isRadio && s.status == TvStatus.PLAYING) Equalizer()
        }
        if (s.status == TvStatus.RETRYING || s.status == TvStatus.FAILED) {
            Alert(
                if (s.status == TvStatus.FAILED) "This channel isn’t playing" else "This stream stopped",
                s.message ?: "Trying another stream…", color = TvMateColors.Amber,
            )
            if (!isRadio) BigButton("Hide this channel", { vm.hideChannel(ref.id, ref.name); vm.stopTv() }, icon = Ic.EyeOff, style = ButtonStyle.Outline)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isRadio && s.streamCount > 1) {
                Chip("Stream ${s.streamIndex + 1} of ${s.streamCount}", icon = Ic.Layers, selected = true) {
                    vm.selectStream(ref.id, (s.streamIndex + 1) % s.streamCount)
                }
            }
            Chip(if (ref.key in favorites) "Favorite" else "Add favorite", icon = Ic.Star, selected = ref.key in favorites) { vm.toggleFavorite(ref) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Ic.SkipBack, { vm.step(-1) }, size = 54.dp, enabled = vm.canStep(-1), description = "Previous")
            RoundIcon(if (s.status == TvStatus.PAUSED) Ic.Play else Ic.Pause, { vm.togglePause() }, size = 76.dp, container = TvMateColors.Orange, tint = Color.White, description = "Play or pause")
            RoundIcon(Ic.SkipForward, { vm.step(1) }, size = 54.dp, enabled = vm.canStep(1), description = "Next")
        }
        VolumeBar(s.volumePercent, s.muted, vm)
        Spacer(Modifier.weight(1f))
        BigButton("Stop on TV", { vm.stopTv() }, icon = Ic.Close, style = ButtonStyle.Outline)
    }
}

@Composable
private fun VolumeBar(percent: Int, muted: Boolean, vm: PhoneViewModel) {
    var value by remember(percent) { mutableFloatStateOf(percent / 100f) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("TV volume")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIcon(if (muted) Ic.VolumeOff else Ic.VolumeUp, { vm.mute() }, size = 38.dp, description = "Mute")
            androidx.compose.material3.Slider(
                value = value, onValueChange = { value = it }, onValueChangeFinished = { vm.volumeSet((value * 100).toInt()) },
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Color.White, activeTrackColor = TvMateColors.Orange, inactiveTrackColor = TvMateColors.Face,
                ),
            )
            Text("${(value * 100).toInt()}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
        }
    }
}
