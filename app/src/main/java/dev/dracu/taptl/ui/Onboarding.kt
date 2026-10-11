package dev.dracu.taptl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dracu.taptl.Lang
import dev.dracu.taptl.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val PAGES = 4

/** First-run intro: what it does, pick a language, how to use it, safety. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var lang by remember { mutableStateOf(Lang.get(context)) }
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES - 1

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding().padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(!last, enter = fadeIn(), exit = fadeOut()) {
                    TextButton(onClick = onDone) { Text("Skip") }
                }
            }

            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                Box(Modifier.fillMaxSize().pageFade(pager, page).padding(horizontal = 24.dp)) {
                    when (page) {
                        0 -> InfoPage(
                            "Read any game in English",
                            "TapTL puts English over the text on your screen, right where it is.",
                        ) { MorphDemo() }
                        1 -> LanguagePage(lang) { lang = it; Lang.set(context, it) }
                        2 -> HowPage(lang)
                        else -> InfoPage(
                            "Safe by design",
                            "TapTL only looks at your screen, like a screenshot. " +
                                "It never touches the game or its files.",
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                EyeDemo()
                                Spacer(Modifier.height(24.dp))
                                Card {
                                    Text(
                                        "Try it on a second account first. Game rules can change.",
                                        Modifier.padding(16.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Dots(pager.currentPage, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))

            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(pager.currentPage > 0, enter = fadeIn(), exit = fadeOut()) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                        Text("Back")
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }) {
                    AnimatedContent(last, label = "next") { Text(if (it) "Get started" else "Next") }
                }
            }
        }
    }
}

/** Pages fade and shrink a little as they slide away. */
private fun Modifier.pageFade(pager: PagerState, page: Int) = graphicsLayer {
    val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
    alpha = 1f - off * 0.8f
    val s = 1f - off * 0.08f
    scaleX = s
    scaleY = s
}

@Composable
private fun Dots(current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(PAGES) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, label = "dot")
            val color by animateColorAsState(
                if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "dotColor",
            )
            Box(Modifier.height(8.dp).width(width).background(color, CircleShape))
        }
    }
}

@Composable
private fun InfoPage(title: String, body: String, art: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        art()
        Spacer(Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LanguagePage(lang: Lang, onPick: (Lang) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Pick a language", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "The language of the text in your game. You can change it later.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        LanguageList(lang, onPick, Modifier.weight(1f))
    }
}

// ---- Illustrations ----

private val GAME_BG = Brush.linearGradient(listOf(Color(0xFF243447), Color(0xFF10161D)))
private val GAME_TEXT = Color(0xFFE6E9ED)

/** A fake game screen: dark panel the illustrations draw on. */
@Composable
private fun GameScreen(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp)).background(GAME_BG),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The English as the overlay draws it: a dark bubble with white text. */
@Composable
private fun EnglishBubble(text: String) {
    Text(
        text,
        Modifier
            .background(Color(0xCC000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = Color.White,
        fontWeight = FontWeight.Medium,
    )
}

private val MENU_SAMPLES = listOf(
    listOf("开始游戏", "设置", "商店"),
    listOf("プレイ", "設定", "ショップ"),
    listOf("플레이", "설정", "상점"),
    listOf("Jugar", "Ajustes", "Tienda"),
    listOf("खेलें", "सेटिंग्स", "दुकान"),
    listOf("Jouer", "Paramètres", "Boutique"),
)
private val MENU_ENGLISH = listOf("Play", "Settings", "Store")

/** A game menu whose labels flip from another language into English, over and over. */
@Composable
private fun MorphDemo() {
    var english by remember { mutableStateOf(false) }
    var sample by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1600)
            english = !english
            if (!english) sample = (sample + 1) % MENU_SAMPLES.size
        }
    }
    GameScreen {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.width(180.dp)) {
            MENU_ENGLISH.indices.forEach { i ->
                val label = if (english) MENU_ENGLISH[i] else MENU_SAMPLES[sample][i]
                AnimatedContent(
                    targetState = english to label,
                    transitionSpec = {
                        val wait = i * 120
                        (fadeIn(tween(350, wait)) + slideInVertically(tween(350, wait)) { it / 2 }) togetherWith
                            (fadeOut(tween(200, wait)) + slideOutVertically(tween(200, wait)) { -it / 2 }) using
                            SizeTransform(clip = false)
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "morph$i",
                ) { (isEnglish, text) ->
                    if (isEnglish) EnglishBubble(text)
                    else Text(text, color = GAME_TEXT, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
    }
}

/** "Settings" as a game would show it in each language. */
private fun settingsWord(lang: Lang) = when (lang) {
    Lang.CHINESE -> "设置"
    Lang.JAPANESE -> "設定"
    Lang.KOREAN -> "설정"
    Lang.HINDI -> "सेटिंग्स"
    Lang.SPANISH -> "Ajustes"
    Lang.PORTUGUESE -> "Configurações"
    Lang.FRENCH -> "Paramètres"
    Lang.GERMAN -> "Einstellungen"
    Lang.ITALIAN -> "Impostazioni"
    Lang.VIETNAMESE -> "Cài đặt"
    Lang.INDONESIAN -> "Pengaturan"
    Lang.TURKISH -> "Ayarlar"
}

private val HOW_STEPS = listOf("Open your game", "Swipe down from the top", "Tap the Translate tile", "English appears")

/** Loops through the four steps: game, shade comes down, tile is tapped, English shows. */
@Composable
private fun HowPage(lang: Lang) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(if (step == 3) 2200 else 1400)
            step = (step + 1) % 4
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        TileDemo(step, lang)
        Spacer(Modifier.height(24.dp))
        Text("How it works", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        HOW_STEPS.forEachIndexed { i, s ->
            val a by animateFloatAsState(if (i == step) 1f else 0.55f, label = "stepAlpha")
            NumberedLine(i + 1, s, active = i == step, modifier = Modifier.graphicsLayer { alpha = a }.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "It hides itself while you play and comes back in menus.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TileDemo(step: Int, lang: Lang) {
    val scheme = MaterialTheme.colorScheme
    val on = step >= 2
    // A finger dot: slides down on step 1, taps on step 2.
    val finger = remember { Animatable(0f) }
    LaunchedEffect(step) {
        finger.snapTo(0f)
        if (step == 1 || step == 2) finger.animateTo(1f, tween(700))
    }
    GameScreen {
        AnimatedContent(
            targetState = step == 3,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) },
            label = "label",
        ) { english ->
            if (english) EnglishBubble("Settings") else Text(settingsWord(lang), color = GAME_TEXT)
        }

        AnimatedVisibility(
            visible = step == 1 || step == 2,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(tween(450)) { -it } + fadeIn(),
            exit = slideOutVertically(tween(350)) { -it } + fadeOut(),
        ) {
            Box(
                Modifier.fillMaxWidth().background(scheme.surfaceContainerHigh).padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                val tileColor by animateColorAsState(if (on) scheme.primary else scheme.surfaceVariant, label = "tile")
                val content = if (on) scheme.onPrimary else scheme.onSurfaceVariant
                Row(
                    Modifier
                        .graphicsLayer {
                            val press = if (step == 2) 1f - 0.08f * (1f - (2 * finger.value - 1f).absoluteValue) else 1f
                            scaleX = press
                            scaleY = press
                        }
                        .background(tileColor, RoundedCornerShape(26.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_tile), null, Modifier.size(20.dp), tint = content)
                    Text("Translate", color = content, style = MaterialTheme.typography.labelLarge)
                }
                if (step == 2) {
                    // Tap ripple over the tile.
                    Box(
                        Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = 0.6f + finger.value
                                scaleY = 0.6f + finger.value
                                alpha = 1f - finger.value
                            }
                            .background(Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                }
            }
        }

        if (step == 1) {
            // Swipe-down hint: a dot moving down from the top edge.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = finger.value * 60.dp.toPx()
                        alpha = 1f - finger.value
                    }
                    .padding(top = 6.dp)
                    .size(22.dp)
                    .background(Color.White.copy(alpha = 0.7f), CircleShape)
            )
        }
    }
}

/** An eye that looks left and right: TapTL only looks. */
@Composable
private fun EyeDemo() {
    val color = MaterialTheme.colorScheme.primary
    val look by rememberInfiniteTransition(label = "eye").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "look",
    )
    Canvas(Modifier.size(width = 160.dp, height = 100.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 6.dp.toPx()
        val eye = Path().apply {
            moveTo(stroke, h / 2)
            quadraticTo(w / 2, -h * 0.35f, w - stroke, h / 2)
            quadraticTo(w / 2, h * 1.35f, stroke, h / 2)
            close()
        }
        drawPath(eye, color, style = Stroke(stroke))
        val c = Offset(w / 2 + look * w * 0.14f, h / 2)
        drawCircle(color, radius = h * 0.24f, center = c)
        drawCircle(Color.White.copy(alpha = 0.8f), radius = h * 0.07f, center = c + Offset(-h * 0.07f, -h * 0.07f))
    }
}
