package com.tvmate.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvmate.app.atv.AtvException
import com.tvmate.app.atv.AtvIdentity
import com.tvmate.app.atv.AtvKey
import com.tvmate.app.atv.AtvPairing
import com.tvmate.app.atv.AtvRemote
import com.tvmate.app.atv.AtvWrongCode
import com.tvmate.app.atv.AtvState
import com.tvmate.shared.TvMateGraph
import com.tvmate.shared.data.ChannelFilters
import com.tvmate.shared.data.ChannelQuery
import com.tvmate.shared.data.MediaRef
import com.tvmate.shared.data.RefreshResult
import com.tvmate.shared.data.db.ChannelEntity
import com.tvmate.shared.data.db.StreamEntity
import com.tvmate.shared.data.net.StationDto
import com.tvmate.shared.link.Command
import com.tvmate.shared.link.StreamSpec
import com.tvmate.shared.link.TvState
import com.tvmate.shared.link.TvStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Tab { Remote, TV, Radio, Saved, Settings }

sealed interface AtvPairUi {
    data object Idle : AtvPairUi
    data object Starting : AtvPairUi
    data object WaitingForCode : AtvPairUi
    data object Verifying : AtvPairUi
    data class Error(val message: String) : AtvPairUi
}

data class Picker(val title: String, val items: List<Pair<String, String>>, val selected: String, val onPick: (String) -> Unit)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PhoneViewModel(app: Application) : AndroidViewModel(app) {
    private val host = (app as TvMatePhoneApp).host
    private val graph = host.graph
    val link = host.link

    // ---- Android TV Remote v2 (system remote: power, home, any app) ----
    private val atvIdentity = host.atvIdentity
    val atv = host.atv
    val atvPaired: StateFlow<Boolean> = host.atvPaired
    val atvPairUi = MutableStateFlow<AtvPairUi>(AtvPairUi.Idle)
    private var atvSession: AtvPairing? = null

    // ---- navigation / overlays ----
    val tab = MutableStateFlow(Tab.TV)
    val showFindTv = MutableStateFlow(false)
    val pairingTarget = MutableStateFlow<FoundTv?>(null)
    val nowPlayingOpen = MutableStateFlow(false)
    val optionsFor = MutableStateFlow<MediaRef?>(null)
    val picker = MutableStateFlow<Picker?>(null)
    val toast = MutableStateFlow<String?>(null)
    val pairError = MutableStateFlow<String?>(null)
    val pairing = MutableStateFlow(false)

    // ---- catalog status ----
    val catalogStatus = MutableStateFlow("")
    val channelCount: StateFlow<Int> = graph.catalog.channelCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val hiddenCount: StateFlow<Int> = graph.catalog.hiddenCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val filters: StateFlow<ChannelFilters> = graph.settings.filters.stateIn(viewModelScope, SharingStarted.Eagerly, ChannelFilters())

    // ---- live TV browsing ----
    val tvQuery = MutableStateFlow("")
    val tvCountry = MutableStateFlow("")
    val tvCategory = MutableStateFlow("")
    val tvLanguage = MutableStateFlow("")

    private val tvQueryState = combine(tvQuery.debounce(200), tvCountry, tvCategory, tvLanguage, filters) { q, c, cat, lang, f ->
        ChannelQuery(text = q, country = c, category = cat, language = lang, filters = f)
    }
    val channels: StateFlow<List<ChannelEntity>> = tvQueryState.flatMapLatest { graph.catalog.channels(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val countries = graph.catalog.countries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = graph.catalog.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val languages = graph.catalog.languages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- radio browsing ----
    val radioQuery = MutableStateFlow("")
    val radioTag = MutableStateFlow("")
    val radioCountry = MutableStateFlow("")
    val stations = MutableStateFlow<List<StationDto>>(emptyList())
    val radioLoading = MutableStateFlow(false)
    val radioError = MutableStateFlow<String?>(null)
    val radioTags = MutableStateFlow<List<String>>(emptyList())

    // ---- saved ----
    val favorites = graph.user.favorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recents = graph.user.recents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favoriteKeys: StateFlow<Set<String>> = favorites.map { l -> l.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // ---- what the TV is doing ----
    val tvState: StateFlow<TvState?> = link.tvState
    private var queue: List<MediaRef> = emptyList()

    init {
        refreshCatalog(force = false)
        viewModelScope.launch {
            combine(radioQuery.debounce(350), radioTag, radioCountry) { q, t, c -> Triple(q, t, c) }.collect { (q, t, c) -> loadStations(q, t, c) }
        }
        viewModelScope.launch { runCatching { graph.radio.tags() }.onSuccess { l -> radioTags.value = l.map { it.name }.filter { it.isNotBlank() }.take(14) } }
        // First run: if no TV is paired yet, start on the Find TV screen.
        viewModelScope.launch {
            delay(600)
            if (link.paired.value == null) showFindTv.value = true
        }
    }

    // ---------------------------------------------------------------- catalog

    fun refreshCatalog(force: Boolean) {
        viewModelScope.launch {
            catalogStatus.value = if (force) "Refreshing channel list…" else catalogStatus.value
            catalogStatus.value = when (val r = graph.catalog.refreshIfStale(force)) {
                RefreshResult.Fresh -> "Channel list is up to date"
                is RefreshResult.Updated -> "Updated: ${r.channels} channels"
                is RefreshResult.Failed -> "Couldn’t update the channel list: ${r.message}"
            }
        }
    }

    private suspend fun loadStations(q: String, tag: String, country: String) {
        radioLoading.value = true
        radioError.value = null
        runCatching { graph.radio.search(name = q.trim(), countryCode = country, tag = tag, limit = 60) }
            .onSuccess { stations.value = it }
            .onFailure { radioError.value = "Couldn’t reach Radio Browser. Check your connection and try again." }
        radioLoading.value = false
    }

    fun retryRadio() { viewModelScope.launch { loadStations(radioQuery.value, radioTag.value, radioCountry.value) } }

    fun openCountryPicker() {
        viewModelScope.launch {
            val list = runCatching { graph.radio.countries() }.getOrDefault(emptyList())
                .filter { it.iso_3166_1.isNotBlank() }.map { it.iso_3166_1 to "${it.name} (${it.stationcount})" }
            picker.value = Picker("Country", list, radioCountry.value) { radioCountry.value = it; picker.value = null }
        }
    }

    // ---------------------------------------------------------------- playing

    private fun requireTv(): Boolean {
        if (link.status.value != LinkStatus.Connected) {
            toast.value = if (link.paired.value == null) "Pair a TV first." else "The TV isn’t connected. Turn it on and open TVMate."
            if (link.paired.value == null) showFindTv.value = true
            return false
        }
        return true
    }

    private fun StreamEntity.spec() = StreamSpec(url, referrer, userAgent, quality)

    fun playChannel(c: ChannelEntity, from: List<ChannelEntity> = channels.value, streamFirst: Int = 0) {
        if (!requireTv()) return
        val ref = MediaRef(MediaRef.Kind.TV, c.id, c.name, c.country, c.logoUrl)
        queue = from.map { MediaRef(MediaRef.Kind.TV, it.id, it.name, it.country, it.logoUrl) }
        sendRef(ref, streamFirst)
    }

    fun playStation(s: StationDto, from: List<StationDto> = stations.value) {
        if (!requireTv()) return
        queue = from.map { it.toRef() }
        sendRef(s.toRef(), 0)
    }

    fun playRef(ref: MediaRef, queueFrom: List<MediaRef> = listOf(ref)) {
        if (!requireTv()) return
        queue = queueFrom
        sendRef(ref, 0)
    }

    private fun StationDto.toRef() = MediaRef(
        MediaRef.Kind.RADIO, stationuuid, name.trim(),
        listOf(country.ifBlank { countrycode }, codec, if (bitrate > 0) "${bitrate}k" else "").filter { it.isNotBlank() }.joinToString(" · "),
        favicon.ifBlank { null }, playUrl,
    )

    private fun sendRef(ref: MediaRef, streamFirst: Int) {
        viewModelScope.launch {
            val cmd = if (ref.kind == MediaRef.Kind.TV) {
                val all = graph.catalog.streams(ref.id).map { it.spec() }
                if (all.isEmpty()) { toast.value = "No streams for ${ref.name}."; return@launch }
                val ordered = if (streamFirst in all.indices) all.drop(streamFirst) + all.take(streamFirst) else all
                Command.PlayTv(ref.id, ref.name, ref.subtitle, ref.logoUrl, ordered)
            } else {
                Command.PlayRadio(ref.id, ref.name, ref.subtitle, ref.logoUrl, StreamSpec(ref.streamUrl.orEmpty()))
            }
            if (link.send(cmd)) graph.user.markPlayed(ref) else toast.value = "Couldn’t reach the TV."
        }
    }

    /** Previous / next item in the list the user started from. */
    fun step(delta: Int) {
        val id = tvState.value?.itemId ?: return
        val i = queue.indexOfFirst { it.id == id }
        val next = queue.getOrNull(i + delta) ?: return
        if (i >= 0) sendRef(next, 0)
    }

    fun canStep(delta: Int): Boolean {
        val id = tvState.value?.itemId ?: return false
        val i = queue.indexOfFirst { it.id == id }
        return i >= 0 && queue.getOrNull(i + delta) != null
    }

    fun togglePause() {
        val s = tvState.value ?: return
        link.send(Command.PlayPause(s.status == TvStatus.PAUSED))
    }

    fun stopTv() { link.send(Command.Stop); nowPlayingOpen.value = false }
    fun volumeSet(percent: Int) { link.send(Command.VolumeSet(percent)) }

    /** Uses the system remote when it's connected, otherwise falls back to the TVMate TV app. */
    fun volumeStep(delta: Int) { if (!atv.sendKey(if (delta > 0) AtvKey.VOLUME_UP else AtvKey.VOLUME_DOWN)) link.send(Command.VolumeStep(delta)) }
    fun mute() { if (!atv.sendKey(AtvKey.VOLUME_MUTE)) link.send(Command.MuteToggle) }
    fun key(code: Int) { if (!atv.sendKey(code)) link.send(Command.Key(code)) }
    /** Keys only the system remote can send (Power, Home). */
    fun systemKey(code: Int) { if (!atv.sendKey(code)) toast.value = "Pair the full remote first." }

    // ---------------------------------------------------------------- system remote pairing

    fun startAtvPairing() {
        val host = link.paired.value?.host
        if (host == null) { toast.value = "Pair a TV first."; return }
        atvSession?.close()
        atvPairUi.value = AtvPairUi.Starting
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = AtvPairing.connect(host, atvIdentity).also { it.begin() }
                atvSession = session
                atvPairUi.value = AtvPairUi.WaitingForCode
            } catch (e: AtvException) {
                atvPairUi.value = AtvPairUi.Error(e.message ?: "Couldn’t start pairing.")
            } catch (e: Exception) {
                atvPairUi.value = AtvPairUi.Error("Couldn’t start pairing. Make sure the TV is on and on the same Wi-Fi.")
            }
        }
    }

    fun submitAtvCode(code: String) {
        val session = atvSession ?: return
        atvPairUi.value = AtvPairUi.Verifying
        viewModelScope.launch(Dispatchers.IO) {
            try {
                session.finish(code)
                session.close()
                atvSession = null
                graph.settings.setAtvPaired(true)
                atvPairUi.value = AtvPairUi.Idle
                toast.value = "Full remote paired."
            } catch (e: AtvWrongCode) {
                atvPairUi.value = AtvPairUi.WaitingForCode
                toast.value = e.message
            } catch (e: AtvException) {
                atvPairUi.value = AtvPairUi.Error(e.message ?: "Pairing failed.")
            } catch (e: Exception) {
                atvPairUi.value = AtvPairUi.Error("Pairing failed. Start again.")
            }
        }
    }

    fun cancelAtvPairing() { atvSession?.close(); atvSession = null; atvPairUi.value = AtvPairUi.Idle }

    fun selectStream(channelId: String, index: Int) {
        val s = tvState.value
        if (s?.itemId == channelId) link.send(Command.SelectStream(index))
        else viewModelScope.launch { graph.catalog.channel(channelId)?.let { playChannel(it, listOf(it), index) } }
    }

    /** The streams available for a channel (for the options sheet). */
    suspend fun streamsFor(channelId: String): List<StreamEntity> = graph.catalog.streams(channelId)

    // ---------------------------------------------------------------- favorites / hide

    fun toggleFavorite(ref: MediaRef) {
        viewModelScope.launch { graph.user.setFavorite(ref, ref.key !in favoriteKeys.value) }
    }

    fun hideChannel(channelId: String, name: String) {
        viewModelScope.launch {
            graph.catalog.hide(channelId)
            toast.value = "Hid $name. Undo in Settings."
            optionsFor.value = null
        }
    }

    fun unhideAll() { viewModelScope.launch { graph.catalog.unhideAll(); toast.value = "All channels are visible again." } }

    // ---------------------------------------------------------------- settings

    fun setShowGeo(v: Boolean) { viewModelScope.launch { graph.settings.setShowGeoBlocked(v) } }
    fun setShowNot247(v: Boolean) { viewModelScope.launch { graph.settings.setShowNot247(v) } }
    fun setShowNsfw(v: Boolean) { viewModelScope.launch { graph.settings.setShowNsfw(v) } }

    // ---------------------------------------------------------------- pairing

    fun startPairing(tv: FoundTv) { pairError.value = null; pairingTarget.value = tv }

    fun submitCode(code: String) {
        val tv = pairingTarget.value ?: return
        viewModelScope.launch {
            pairing.value = true
            pairError.value = null
            link.pair(tv, code)
                .onSuccess { pairingTarget.value = null; showFindTv.value = false; tab.value = Tab.Remote; toast.value = "Paired with ${it.name}." }
                .onFailure { pairError.value = it.message ?: "Couldn’t pair." }
            pairing.value = false
        }
    }

    fun forgetTv() { viewModelScope.launch { link.forget(); showFindTv.value = true } }

    override fun onCleared() { link.stopDiscovery(); atvSession?.close() }
}
