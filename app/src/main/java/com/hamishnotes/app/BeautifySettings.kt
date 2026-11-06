package com.hamishnotes.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged

/*
 * إعدادات «تجميل خط اليد» بتصميم بطاقات إعدادات: كل تغيير يُطبَّق فوراً (لا زر حفظ)،
 * والخط يُختار من نافذة مستقلة تُفتح من صف «خط الكتابة».
 */

internal val SheetBg = Color(0xFFF2F3F6)
internal val CardBg = Color.White
internal val RowDivider = Color(0xFFE9EBEF)
internal val Muted = Color(0xFF8A919E)

@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg),
        content = content
    )
}

@Composable
internal fun CardTitle(text: String) {
    Text(
        text, fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
internal fun RowDividerLine() = HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.8.dp, color = RowDivider)

@Composable
internal fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = Palette.TextPrimary)
            if (subtitle != null) Text(subtitle, fontSize = 12.5.sp, color = Muted, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Palette.Navy, checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE3E6EB), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

/** صف بقيمة وسهم (يفتح نافذة اختيار). */
@Composable
internal fun NavRow(title: String, onClick: () -> Unit, value: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 16.sp, color = Palette.TextPrimary)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { value() }
        Spacer(Modifier.width(6.dp))
        Text(if (I18n.english) "›" else "‹", fontSize = 22.sp, color = Muted)
    }
}

/** شريط اختيارات متجاورة (مثل iOS): الخيار المختار بخلفية بيضاء تنزلق إليه. */
@Composable
internal fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    // بلا BoxWithConstraints: النافذة قد تكون داخل قائمة منسدلة تقيس محتواها مسبقاً (لا يدعمه SubcomposeLayout)
    var widthPx by remember { mutableStateOf(0) }
    val n = options.size
    val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val segPx = if (n > 0) widthPx / n.toFloat() else 0f
    val off by animateFloatAsState(segPx * idx, label = "seg")
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEF0F3)).padding(3.dp)
            .onSizeChanged { widthPx = it.width }
    ) {
        if (widthPx > 0) {
            Box(
                Modifier.offset { IntOffset(off.roundToInt(), 0) }.width(with(density) { segPx.toDp() }).height(36.dp)
                    .shadow(2.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color.White)
            )
        }
        Row(Modifier.fillMaxWidth().height(36.dp)) {
            options.forEach { (v, label) ->
                val sel = v == selected
                val c by animateColorAsState(if (sel) Palette.Navy else Muted, label = "segc")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable { onSelect(v) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 14.sp, color = c, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun StepperRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float, format: (Float) -> String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 15.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
            RoundButton("−") { onChange((value - step).coerceIn(range)) }
            Text(format(value), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Navy, textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
            RoundButton("+") { onChange((value + step).coerceIn(range)) }
        }
        Slider(
            value = value, onValueChange = { onChange((kotlin.math.round(it / step) * step).coerceIn(range)) }, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Palette.Navy, inactiveTrackColor = Color(0xFFE3E6EB)),
            modifier = Modifier.fillMaxWidth().height(34.dp)
        )
    }
}

@Composable
internal fun RoundButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Color(0xFFEEF0F3)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 18.sp, color = Palette.Navy) }
}

private fun sampleFor(lang: Int) = if (lang == HwLang.ARABIC) "الخلية وحدة الحياة" else "The cell is the unit of life"

/** إعدادات تجميل خط اليد (تُطبَّق فوراً). */
@Composable
fun AutoFontDialog(onDismiss: () -> Unit) {
    var fontPicker by remember { mutableStateOf(false) }
    val on = AutoFont.on
    val ink = AutoFont.output == AutoFont.OUT_INK
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    LaunchedEffect(AutoFont.lang) { HandwritingReader.prefetch(AutoFont.lang) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).heightIn(max = maxH),
            shape = RoundedCornerShape(26.dp), color = SheetBg, shadowElevation = 18.dp
        ) {
            Column {
                // العنوان
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        L("تجميل خط اليد", "Handwriting beautification"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, color = Palette.TextPrimary, modifier = Modifier.align(Alignment.Center)
                    )
                    Text(
                        L("تم", "Done"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Navy,
                        modifier = Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(10.dp)).clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
                    // التشغيل
                    SettingsCard {
                        SwitchRow(
                            L("تجميل خط اليد أثناء الكتابة", "Beautify while writing"),
                            L("عند إنهاء الجملة تُعاد كتابتها في مكانها بالخط المختار", "When you finish a sentence it is rewritten in place in the chosen font"),
                            on
                        ) { v ->
                            AutoFont.changeOn(v)
                            if (v) AppController.messages.tryEmit(L("اكتب جملتك، وستتجمّل عند إنهائها", "Write your sentence; it is beautified when you finish"))
                        }
                    }
                    Column(Modifier.alpha(if (on) 1f else 0.55f)) {
                        // الخط
                        CardTitle(L("الخط", "Font"))
                        SettingsCard {
                            NavRow(L("خط الكتابة", "Writing font"), onClick = { fontPicker = true }) {
                                Text(
                                    AutoFont.font, fontFamily = FontStore.family(AutoFont.font), fontSize = 17.sp,
                                    color = Palette.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            // معاينة بالخط المختار
                            val ready = FontStore.state[AutoFont.font] == true || FontStore.isReady(AutoFont.font)
                            if (!ready) LaunchedEffect(AutoFont.font) { FontStore.ensure(AutoFont.font) }
                            Text(
                                sampleFor(AutoFont.lang),
                                fontFamily = if (ready) FontStore.family(AutoFont.font) else null,
                                fontSize = 30.sp, lineHeight = 40.sp, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp)
                                    .clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F8FA)).padding(horizontal = 12.dp, vertical = 14.dp)
                            )
                            if (ink) {
                                RowDividerLine()
                                SwitchRow(L("خط أعرض", "Bolder"), checked = AutoFont.dynamicBold) { AutoFont.changeBold(it) }
                            }
                        }
                        // الشكل واللغة
                        CardTitle(L("تُحفظ على شكل", "Save as"))
                        SettingsCard {
                            Segmented(
                                listOf(AutoFont.OUT_INK to L("✎ حبر قلم", "✎ Pen ink"), AutoFont.OUT_TEXT to L("T مربع نص", "T Text box")),
                                AutoFont.output
                            ) { AutoFont.changeOutput(it) }
                            Text(
                                if (ink) L("حبر بشكل الخط تماماً: تمسحه بالممحاة وتحدده وتلوّنه مثل أي كتابة.", "Ink shaped exactly like the font: erase, select and recolor it like any writing.")
                                else L("نص يمكن تعديله؛ شخبطة على كلمة تحذفها و∧ يضيف كلمة.", "Editable text; scribble a word to delete it, ∧ to insert one."),
                                fontSize = 12.5.sp, color = Muted, lineHeight = 17.sp,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                            )
                        }
                        CardTitle(L("لغة الكتابة", "Writing language"))
                        SettingsCard {
                            Segmented(listOf(HwLang.ARABIC to "العربية", HwLang.ENGLISH to "English"), AutoFont.lang) { AutoFont.changeLang(it) }
                        }
                        // التنسيق
                        CardTitle(L("التنسيق", "Layout"))
                        SettingsCard {
                            SwitchRow(L("توحيد حجم الخط والمسافة بين الأسطر", "Unify font size & line spacing"), checked = AutoFont.unify) {
                                AutoFont.changeUnify(it, AutoFont.unifySize, AutoFont.unifySpacing)
                            }
                            AnimatedVisibility(AutoFont.unify) {
                                Column {
                                    RowDividerLine()
                                    StepperRow(L("حجم الخط", "Font size"), AutoFont.unifySize.toFloat(), AutoFont.SIZE_MIN.toFloat()..AutoFont.SIZE_MAX.toFloat(), 1f, { it.toInt().toString() }) {
                                        AutoFont.changeUnify(true, it.toInt(), AutoFont.unifySpacing)
                                    }
                                    StepperRow(L("المسافة بين الأسطر", "Line spacing"), AutoFont.unifySpacing, 1f..3f, 0.1f, { "%.1f".format(it) }) {
                                        AutoFont.changeUnify(true, AutoFont.unifySize, it)
                                    }
                                }
                            }
                            RowDividerLine()
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(L("قوة الجذب إلى أسطر الدفتر", "Snapping intensity"), fontSize = 16.sp, color = Palette.TextPrimary)
                                Slider(
                                    value = AutoFont.snap.toFloat(), onValueChange = { AutoFont.changeSnap(kotlin.math.round(it).toInt()) },
                                    valueRange = 0f..3f, steps = 2,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White, activeTrackColor = Palette.Navy, inactiveTrackColor = Color(0xFFE3E6EB),
                                        activeTickColor = Color.White, inactiveTickColor = Muted
                                    )
                                )
                                Row(Modifier.fillMaxWidth()) {
                                    listOf(L("بلا", "Off"), L("ضعيف", "Weak"), L("عادي", "Standard"), L("قوي", "Strong")).forEachIndexed { i, t ->
                                        Text(
                                            t, fontSize = 12.sp, color = if (i == AutoFont.snap) Palette.Navy else Muted,
                                            fontWeight = if (i == AutoFont.snap) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = when (i) { 0 -> TextAlign.Start; 3 -> TextAlign.End; else -> TextAlign.Center },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                        // التوقيت
                        CardTitle(L("متى تتحول الجملة", "When a sentence converts"))
                        SettingsCard {
                            Segmented(
                                listOf(1000 to L("١ ث", "1 s"), 2000 to L("٢ ث", "2 s"), 3000 to L("٣ ث", "3 s"), 0 to L("عند ✓", "On ✓")),
                                AutoFont.delayMs
                            ) { AutoFont.changeDelay(it) }
                            Text(
                                L("بعد التوقف عن الكتابة، وفوراً عندما تبدأ سطراً جديداً.", "After you pause, and right away when you start a new line."),
                                fontSize = 12.5.sp, color = Muted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                            )
                            if (!ink) {
                                RowDividerLine()
                                SwitchRow(L("جمع الأسطر المتتالية في فقرة واحدة", "Join consecutive lines into one paragraph"), checked = AutoFont.joinLines) { AutoFont.changeJoin(it) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (fontPicker) FontPickerSheet(AutoFont.font, AutoFont.lang, onPick = { AutoFont.changeFont(it) }, onDismiss = { fontPicker = false })
}

/**
 * نافذة اختيار الخط: قائمة تمرير، كل خط يُعرض بنص تجريبي بشكله، مع بحث وفئات.
 * الخطوط غير المنزلة تُنزَّل عند اختيارها (ومعاينتها تلقائياً على Wi-Fi).
 */
@Composable
fun FontPickerSheet(current: String, lang: Int, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val arabic = lang == HwLang.ARABIC
    var tab by remember { mutableStateOf(if (FontStore.recent.isNotEmpty()) "recent" else "hand") }
    val auto = remember { FontStore.unmetered() }
    val sample = sampleFor(lang)
    val list = remember(tab, query, FontStore.recent, arabic) {
        val q = query.trim()
        val base = when (tab) {
            "recent" -> FontStore.recent.mapNotNull { FontStore.entry(it) }
            "hand" -> FontStore.catalog.filter { if (arabic) FontStore.isArabicHand(it) else it.cat == 'H' }
            "fav" -> FontStore.catalog.filter { it.name in FontStore.favorites }
            else -> FontStore.catalog.filter { !arabic || it.arabic }
        }
        val f = if (q.isEmpty()) base else FontStore.catalog.filter { it.name.contains(q, ignoreCase = true) }
        if (tab == "recent" && q.isEmpty()) f else f.sortedBy { if (it.bundled != null) 0 else if (FontStore.isReady(it.name)) 1 else 2 }
    }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).heightIn(min = 420.dp, max = maxH),
            shape = RoundedCornerShape(26.dp), color = SheetBg, shadowElevation = 18.dp
        ) {
            Column {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        (if (I18n.english) "‹ " else "› ") + L("رجوع", "Back"), fontSize = 16.sp, color = Palette.Navy,
                        modifier = Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(10.dp)).clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                    Text(
                        L("خط الكتابة", "Writing font"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, color = Palette.TextPrimary, modifier = Modifier.align(Alignment.Center)
                    )
                }
                // البحث
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFE6E8EC))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⌕", fontSize = 18.sp, color = Muted)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(L("ابحث في ${FontStore.catalog.size} خطاً", "Search ${FontStore.catalog.size} fonts"), color = Muted, fontSize = 15.sp)
                        BasicTextField(
                            query, { query = it }, singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, color = Palette.TextPrimary), modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                // الفئات
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "recent" to L("المستعملة", "Recent"),
                        "hand" to L("خط يد", "Handwriting"),
                        "fav" to L("★ المفضلة", "★ Favorites"),
                        "all" to L("الكل", "All")
                    ).forEach { (k, label) ->
                        val sel = tab == k && query.isBlank()
                        Text(
                            label, fontSize = 14.sp, color = if (sel) Color.White else Palette.TextPrimary,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) Palette.Navy else Color.White)
                                .clickable { tab = k; query = "" }.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (tab == "fav") L("اضغط ☆ بجانب أي خط ليظهر هنا", "Tap ☆ next to any font to keep it here") else L("لا توجد نتائج", "No results"),
                            color = Muted
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).background(CardBg)
                    ) {
                        items(list, key = { it.name }) { e ->
                            FontRow(e, sample, selected = e.name == current, auto = auto) {
                                scope.launch {
                                    if (FontStore.ensure(e.name)) {
                                        FontStore.markUsed(e.name)
                                        onPick(e.name)
                                        onDismiss()
                                    } else {
                                        AppController.messages.tryEmit(L("تعذر تنزيل الخط. تأكد من الإنترنت", "Could not download the font. Check your connection"))
                                    }
                                }
                            }
                            RowDividerLine()
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FontRow(e: FontEntry, sample: String, selected: Boolean, auto: Boolean, onClick: () -> Unit) {
    val st = FontStore.state[e.name]
    val ready = st == true || FontStore.isReady(e.name)
    if (auto && !ready) LaunchedEffect(e.name) { FontStore.ensure(e.name, retryFailed = false) }
    val fam = if (ready) FontStore.family(e.name) else null
    Row(
        Modifier.fillMaxWidth().background(if (selected) Color(0xFFF1F4FA) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (fam != null) sample else e.name, fontFamily = fam, fontSize = if (fam != null) 24.sp else 17.sp,
                lineHeight = 32.sp, color = if (fam != null) Palette.TextPrimary else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(e.name, fontSize = 12.sp, color = Muted, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        when {
            st == false -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Navy)
            selected -> Text("✓", fontSize = 20.sp, color = Palette.Navy, fontWeight = FontWeight.Bold)
            !ready -> Text("⤓", fontSize = 20.sp, color = Muted)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            if (e.name in FontStore.favorites) "★" else "☆", fontSize = 18.sp, color = Palette.Gold,
            modifier = Modifier.clip(CircleShape).clickable { FontStore.toggleFavorite(e.name) }.padding(4.dp)
        )
    }
}
