package com.tvmate.shared.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.composables.icons.lucide.R as L

/** Lucide icon ids (drawables shipped by com.composables:icons-lucide-android). */
object Ic {
    val Play = L.drawable.lucide_ic_play
    val Pause = L.drawable.lucide_ic_pause
    val SkipBack = L.drawable.lucide_ic_skip_back
    val SkipForward = L.drawable.lucide_ic_skip_forward
    val Tv = L.drawable.lucide_ic_tv
    val TvMinimal = L.drawable.lucide_ic_tv_minimal
    val Radio = L.drawable.lucide_ic_radio
    val Star = L.drawable.lucide_ic_star
    val Settings = L.drawable.lucide_ic_settings
    val Gamepad = L.drawable.lucide_ic_gamepad_2
    val Up = L.drawable.lucide_ic_chevron_up
    val Down = L.drawable.lucide_ic_chevron_down
    val Left = L.drawable.lucide_ic_chevron_left
    val Right = L.drawable.lucide_ic_chevron_right
    val Back = L.drawable.lucide_ic_undo_2
    val ArrowLeft = L.drawable.lucide_ic_arrow_left
    val Home = L.drawable.lucide_ic_house
    val Power = L.drawable.lucide_ic_power
    val VolumeUp = L.drawable.lucide_ic_volume_2
    val VolumeOff = L.drawable.lucide_ic_volume_x
    val Plus = L.drawable.lucide_ic_plus
    val Minus = L.drawable.lucide_ic_minus
    val Search = L.drawable.lucide_ic_search
    val Sliders = L.drawable.lucide_ic_sliders_horizontal
    val Globe = L.drawable.lucide_ic_globe
    val Filter = L.drawable.lucide_ic_list_filter
    val Languages = L.drawable.lucide_ic_languages
    val Ban = L.drawable.lucide_ic_ban
    val Clock = L.drawable.lucide_ic_clock
    val Check = L.drawable.lucide_ic_check
    val Close = L.drawable.lucide_ic_x
    val Layers = L.drawable.lucide_ic_layers
    val EyeOff = L.drawable.lucide_ic_eye_off
    val Wifi = L.drawable.lucide_ic_wifi
    val WifiOff = L.drawable.lucide_ic_wifi_off
    val Refresh = L.drawable.lucide_ic_refresh_cw
    val CheckCircle = L.drawable.lucide_ic_circle_check
    val Info = L.drawable.lucide_ic_info
    val Lock = L.drawable.lucide_ic_lock
    val Keyboard = L.drawable.lucide_ic_keyboard
    val Music = L.drawable.lucide_ic_music
    val Alert = L.drawable.lucide_ic_triangle_alert
    val History = L.drawable.lucide_ic_history
    val Shield = L.drawable.lucide_ic_shield
}

@Composable
fun LIcon(@DrawableRes id: Int, modifier: Modifier = Modifier.size(20.dp), tint: Color = LocalContentColor.current, description: String? = null) {
    Icon(painterResource(id), description, modifier, tint)
}

// ---------- surfaces ----------

fun Modifier.raised(shape: Shape = RoundedCornerShape(16.dp), color: Color = TvMateColors.Panel): Modifier =
    this.shadow(2.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
        .background(color, shape)
        .border(1.dp, Color.White.copy(alpha = 0.45f), shape)

fun Modifier.sunken(shape: Shape = RoundedCornerShape(14.dp)): Modifier =
    this.background(TvMateColors.Face, shape).border(1.dp, Color.Black.copy(alpha = 0.07f), shape)

/** Visible focus for D-pad navigation (TV). Harmless on touch. */
@Composable
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(16.dp)): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this.onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(BorderStroke(3.dp, TvMateColors.Orange), shape) else Modifier)
}

// ---------- text helpers ----------

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp), color = TvMateColors.Mute)
}

@Composable
fun Led(text: String, color: Color = TvMateColors.Orange, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = TvMateColors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Tag(text: String, color: Color = TvMateColors.Mute, @DrawableRes icon: Int? = null) {
    Row(
        Modifier.border(1.dp, color, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (icon != null) LIcon(icon, Modifier.size(11.dp), color)
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

// ---------- buttons ----------

@Composable
fun RoundIcon(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = TvMateColors.Panel,
    tint: Color = TvMateColors.Ink,
    enabled: Boolean = true,
    description: String? = null,
) {
    Box(
        modifier.size(size).raised(CircleShape, container).focusRing(CircleShape).clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icon, Modifier.size(size * 0.45f), if (enabled) tint else tint.copy(alpha = 0.35f), description)
    }
}

enum class ButtonStyle { Orange, Ink, Outline }

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    style: ButtonStyle = ButtonStyle.Ink,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = when (style) { ButtonStyle.Orange -> TvMateColors.Orange; ButtonStyle.Ink -> TvMateColors.Ink; ButtonStyle.Outline -> Color.Transparent }
    val fg = if (style == ButtonStyle.Outline) TvMateColors.Ink else Color.White
    Row(
        modifier.fillMaxWidth().then(if (style == ButtonStyle.Outline) Modifier.border(1.5.dp, TvMateColors.Ink, shape) else Modifier.shadow(2.dp, shape))
            .background(bg, shape).focusRing(shape).clip(shape).clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { LIcon(icon, Modifier.size(18.dp), fg.copy(alpha = if (enabled) 1f else 0.4f)); Spacer(Modifier.width(9.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = fg.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, selected: Boolean = false, onClick: () -> Unit = {}) {
    val shape = CircleShape
    Row(
        modifier.raised(shape, if (selected) TvMateColors.Ink else TvMateColors.Panel).focusRing(shape).clip(shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) LIcon(icon, Modifier.size(15.dp), if (selected) TvMateColors.Orange else TvMateColors.Ink2)
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = if (selected) Color.White else TvMateColors.Ink, maxLines = 1)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().sunken().padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) TvMateColors.Ink else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else TvMateColors.Mute) }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onSearch: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().sunken().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        LIcon(Ic.Search, Modifier.size(18.dp), TvMateColors.Mute)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = TvMateColors.Mute)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TvMateColors.Ink),
                cursorBrush = SolidColor(TvMateColors.Orange),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) RoundIcon(Ic.Close, { onChange("") }, size = 26.dp, container = TvMateColors.Panel)
    }
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val shape = CircleShape
    Box(
        modifier.width(46.dp).height(26.dp).background(if (checked) TvMateColors.Orange else Color(0xFF9B978E), shape)
            .focusRing(shape).clip(shape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) },
    ) {
        Box(Modifier.padding(3.dp).size(20.dp).align(if (checked) Alignment.CenterEnd else Alignment.CenterStart).background(Color.White, CircleShape))
    }
}

// ---------- rows ----------

private val logoPalette = listOf(0xFFB3321F, 0xFF1E5AA8, 0xFF2F7D4B, 0xFF46433D, 0xFF7A4A1C, 0xFF6F6B63, 0xFF1C1B19, 0xFF8A3FD1).map { Color(it) }

@Composable
fun ChannelLogo(name: String, logoUrl: String?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val shape = RoundedCornerShape(size * 0.27f)
    val initials = remember(name) { name.filter { it.isLetterOrDigit() }.take(3).uppercase().ifEmpty { "TV" } }
    val color = remember(name) { logoPalette[(name.hashCode() and 0x7fffffff) % logoPalette.size] }
    Box(modifier.size(size).clip(shape).background(color), contentAlignment = Alignment.Center) {
        val fallback: @Composable () -> Unit = {
            Text(initials, style = MaterialTheme.typography.headlineMedium.copy(fontSize = (size.value * 0.36f).sp), color = Color.White, maxLines = 1)
        }
        if (logoUrl.isNullOrBlank()) fallback() else {
            SubcomposeAsyncImage(
                model = logoUrl, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxHeight().fillMaxWidth().background(Color.White).padding(3.dp),
                loading = { fallback() }, error = { fallback() },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaRow(
    title: String,
    modifier: Modifier = Modifier,
    logoUrl: String? = null,
    showLogo: Boolean = true,
    subtitle: String? = null,
    tags: @Composable RowScope.() -> Unit = {},
    trailing: @Composable () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().raised(shape).focusRing(shape).clip(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showLogo) ChannelLogo(title, logoUrl)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TvMateColors.Mute, maxLines = 1)
                tags()
            }
        }
        trailing()
    }
}

@Composable
fun PlayDot(modifier: Modifier = Modifier, dim: Boolean = false, size: Dp = 38.dp) {
    Box(
        modifier.size(size).background(if (dim) TvMateColors.Face else TvMateColors.Orange, CircleShape),
        contentAlignment = Alignment.Center,
    ) { LIcon(Ic.Play, Modifier.size(size * 0.42f), if (dim) TvMateColors.Mute else Color.White) }
}

@Composable
fun Alert(title: String, body: String, modifier: Modifier = Modifier, color: Color = TvMateColors.Fault, @DrawableRes icon: Int = Ic.Alert) {
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().raised(shape).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(color, CircleShape))
        LIcon(icon, Modifier.size(22.dp), color)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TvMateColors.Ink2)
        }
    }
}

@Composable
fun HelpCard(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int = Ic.Info) {
    Row(modifier.fillMaxWidth().raised().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LIcon(icon, Modifier.size(18.dp), TvMateColors.Orange)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TvMateColors.Ink2)
    }
}

@Composable
fun Equalizer(modifier: Modifier = Modifier, bars: Int = 9, height: Dp = 34.dp, color: Color = TvMateColors.Orange) {
    val t = rememberInfiniteTransition(label = "eq")
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        repeat(bars) { i ->
            val h by t.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(500 + (i * 97) % 500), RepeatMode.Reverse), label = "bar$i",
            )
            Box(Modifier.width(6.dp).fillMaxHeight(h).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

/** Single 6-box code input. The real input is one hidden text field so the keyboard and paste just work. */
@Composable
fun CodeInput(value: String, onChange: (String) -> Unit, length: Int, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    BasicTextField(
        value = value,
        onValueChange = { v -> onChange(v.uppercase().filter { it.isLetterOrDigit() }.take(length)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier,
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                repeat(length) { i ->
                    val ch = value.getOrNull(i)?.toString().orEmpty()
                    val active = i == value.length
                    Box(
                        Modifier.width(42.dp).height(56.dp).sunken(RoundedCornerShape(10.dp))
                            .then(if (active) Modifier.border(2.5.dp, TvMateColors.Orange, RoundedCornerShape(10.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { Text(ch, style = MaterialTheme.typography.headlineLarge.copy(fontFamily = TvMateFonts.Mono, fontSize = 26.sp)) }
                }
            }
        },
    )
}
