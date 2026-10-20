package com.minimo.launcher.ui.xmb

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimo.launcher.R
import com.minimo.launcher.ui.entities.AppInfo
import com.minimo.launcher.ui.home.FolderInfo
import com.minimo.launcher.ui.home.HomeScreenState
import com.minimo.launcher.ui.home.HomeViewModel
import com.minimo.launcher.utils.AppCategorizer
import com.minimo.launcher.utils.AutoCategory
import com.minimo.launcher.utils.Constants
import com.minimo.launcher.utils.RecentApps
import com.minimo.launcher.utils.launchAppInfo
import com.minimo.launcher.utils.lockScreen
import com.minimo.launcher.utils.showNotificationDrawer
import com.minimo.launcher.utils.uninstallApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Model
// ---------------------------------------------------------------------------------------------

private sealed interface XmbItem {
    val key: String
    val title: String

    data class AppEntry(val app: AppInfo) : XmbItem {
        override val key: String get() = "app:${app.id}"
        override val title: String get() = app.name
    }

    data class ActionEntry(
        override val key: String,
        override val title: String,
        val glyph: XmbGlyph,
        val onClick: () -> Unit
    ) : XmbItem
}

private data class XmbCategory(
    val key: String,
    val title: String,
    val glyph: XmbGlyph,
    val items: List<XmbItem>,
    val folder: FolderInfo? = null,
    val emptyText: String = "Nothing here yet"
)

private data class BubbleEntry(val label: String, val onClick: () -> Unit)

private data class BubbleState(
    val anchorX: Float,
    val anchorY: Float,
    val below: Boolean,
    val entries: List<BubbleEntry>
)

private const val KEY_RECENT = "recent"
private const val KEY_APPS = "apps"
private const val KEY_FAVOURITES = "favourites"
private const val KEY_SETTINGS = "settings"

// Tab order: Recent, All Apps, (Favourites), your categories, automatic categories, Settings.
private fun buildCategories(
    state: HomeScreenState,
    context: Context,
    recentIds: List<String>,
    autoMap: Map<String, AutoCategory>,
    openSettings: () -> Unit,
    newCategory: () -> Unit
): List<XmbCategory> {
    val byName = compareBy<AppInfo, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
    val visible = state.allApps.filter {
        !it.isHidden && it.packageName != Constants.MINIMO_SETTINGS_PACKAGE
    }
    val sorted = visible.sortedWith(byName)
    val byId = visible.associateBy { it.id }

    val result = mutableListOf<XmbCategory>()

    result += XmbCategory(
        key = KEY_RECENT,
        title = "Recent",
        glyph = XmbGlyph.Clock,
        items = recentIds.mapNotNull { byId[it] }.map { XmbItem.AppEntry(it) },
        emptyText = "Apps you open will show up here"
    )

    result += XmbCategory(
        key = KEY_APPS,
        title = "All Apps",
        glyph = XmbGlyph.Apps,
        items = sorted.map { XmbItem.AppEntry(it) }
    )

    val favourites = state.favouriteApps.filter { it.folderId == null && !it.isHidden }
    if (favourites.isNotEmpty()) {
        result += XmbCategory(
            key = KEY_FAVOURITES,
            title = "Favourites",
            glyph = XmbGlyph.Star,
            items = favourites.map { XmbItem.AppEntry(it) }
        )
    }

    // Categories the user created (they can add apps to them from the app's long-press menu).
    state.folders.forEach { info ->
        result += XmbCategory(
            key = "folder:${info.folder.id}",
            title = info.folder.name,
            glyph = XmbGlyph.Folder,
            items = info.apps.sortedWith(byName).map { XmbItem.AppEntry(it) },
            folder = info,
            emptyText = "Empty. Long-press an app and choose Add to Folder"
        )
    }

    // Automatic categories. Apps the user put in their own category are not repeated here.
    if (autoMap.isNotEmpty()) {
        val groups = sorted
            .filter { it.folderId == null }
            .groupBy { autoMap[it.id] ?: AutoCategory.Other }
        AutoCategory.values().forEach { category ->
            val apps = groups[category].orEmpty()
            if (apps.isNotEmpty()) {
                result += XmbCategory(
                    key = "auto:${category.name}",
                    title = category.title,
                    glyph = XmbGlyph.Folder,
                    items = apps.map { XmbItem.AppEntry(it) }
                )
            }
        }
    }

    result += XmbCategory(
        key = KEY_SETTINGS,
        title = "Settings",
        glyph = XmbGlyph.Settings,
        items = listOf(
            XmbItem.ActionEntry("action:launcher", "Launcher Settings", XmbGlyph.Settings) {
                openSettings()
            },
            XmbItem.ActionEntry("action:newcategory", "New Category", XmbGlyph.Folder) {
                newCategory()
            },
            XmbItem.ActionEntry("action:notifications", "Notifications", XmbGlyph.Bell) {
                context.showNotificationDrawer()
            },
            XmbItem.ActionEntry("action:lock", "Lock Screen", XmbGlyph.Lock) {
                context.lockScreen()
            }
        )
    )
    return result
}

// ---------------------------------------------------------------------------------------------
// Alphabet helpers
// ---------------------------------------------------------------------------------------------

private val ALPHABET: List<Char> = listOf('#') + ('A'..'Z').toList()

private fun letterOf(title: String): Char {
    val c = title.trim().firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

private fun rankOf(c: Char): Int = if (c == '#') 0 else (c - 'A') + 1

private fun indexForLetter(items: List<XmbItem>, letter: Char): Int {
    if (items.isEmpty()) return 0
    val target = rankOf(letter)
    val ranks = items.map { rankOf(letterOf(it.title)) }
    val exact = ranks.indexOfFirst { it == target }
    if (exact >= 0) return exact
    val next = ranks.indexOfFirst { it > target }
    return if (next >= 0) next else items.lastIndex
}

// ---------------------------------------------------------------------------------------------
// Geometry
// ---------------------------------------------------------------------------------------------

private class XmbGeometry(val w: Float, val h: Float, d: Density) {
    val catIconPx = with(d) { 48.dp.toPx() }
    val catSpacing = with(d) { 96.dp.toPx() }
    val iconPx = with(d) { 44.dp.toPx() }
    val itemSpacing = with(d) { 62.dp.toPx() }
    val rowHeight = with(d) { 56.dp.toPx() }
    val gap = with(d) { 14.dp.toPx() }

    /** y of the category icon row (centre). */
    val barY = h * 0.16f

    /** x of the selected category icon (centre) – the items hang below it. */
    val selX = max(w * 0.26f, with(d) { 76.dp.toPx() })

    /** y of the selected item (centre). */
    val selY = barY + h * 0.25f

    fun itemY(index: Int, itemPos: Float): Float {
        val d = index - itemPos
        return selY + d * itemSpacing + gap * d.coerceIn(-1f, 1f)
    }

    fun hitCategory(p: Offset, catPos: Float, count: Int): Int? {
        if (abs(p.y - barY) > catIconPx * 0.95f) return null
        val i = ((p.x - selX) / catSpacing + catPos).roundToInt()
        if (i !in 0 until count) return null
        val cx = selX + (i - catPos) * catSpacing
        return if (abs(p.x - cx) < catSpacing / 2f) i else null
    }

    fun hitItem(p: Offset, itemPos: Float, count: Int): Int? {
        if (p.y < barY + catIconPx * 1.4f || p.x < selX - iconPx) return null
        val j = (itemPos + (p.y - selY) / itemSpacing).roundToInt()
        return if (j in 0 until count) j else null
    }
}

private val TextShadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(1f, 2f), 4f)

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

/**
 * PS3 XrossMediaBar style home.
 *
 *  - swipe left/right        -> change category
 *  - swipe / fling up/down   -> move through the items (fast fling, or drag the A-Z bar)
 *  - tap                     -> select (tap a non-selected entry) / launch (tap the selected item)
 *  - long press an app       -> bubble menu (opens below the app in the upper half, above it in the lower half)
 *  - long press a category   -> bubble menu (rename / delete) for your own categories
 *  - long press empty space  -> search apps
 *  - pull past the first item -> notification panel
 *  - back                    -> returns to the first tab, never leaves to another launcher
 */
@Composable
fun XmbHomeBody(
    state: HomeScreenState,
    viewModel: HomeViewModel,
    statusBarVisible: Boolean,
    navigationBarVisible: Boolean,
    useDarkStatusBarIcons: Boolean,
    useDarkNavigationBarIcons: Boolean,
    onOpenAppDrawer: () -> Unit,
    onSettingsClick: () -> Unit,
    onDeleteShortcut: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val iconCacheRevision by viewModel.iconCacheRevision.collectAsStateWithLifecycle()

    // The XMB background is always dark, so keep the status bar icons light.
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
        }
    }

    remember { RecentApps.load(context) }
    val recentIds by RecentApps.ids.collectAsStateWithLifecycle()

    val packageManager = context.packageManager
    val autoMap by produceState<Map<String, AutoCategory>>(emptyMap(), state.allApps) {
        value = withContext(Dispatchers.Default) {
            state.allApps.associate {
                it.id to AppCategorizer.categorize(packageManager, it.packageName)
            }
        }
    }

    var searchOpen by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var bubble by remember { mutableStateOf<BubbleState?>(null) }
    var dragLetter by remember { mutableStateOf<Char?>(null) }

    val openSettings by rememberUpdatedState(onSettingsClick)
    val categories = remember(
        state.favouriteApps, state.allApps, state.folders, recentIds, autoMap
    ) {
        buildCategories(
            state = state,
            context = context,
            recentIds = recentIds,
            autoMap = autoMap,
            openSettings = { openSettings() },
            newCategory = { showNewCategory = true }
        )
    }
    val categoriesNow by rememberUpdatedState(categories)

    // catPos: fractional index of the selected category. itemPos: same for the item list.
    val catPos = remember { Animatable(0f) }
    val itemPos = remember { Animatable(0f) }
    val savedItem = remember { mutableStateMapOf<String, Int>() }
    val catPosProvider = remember { { catPos.value } }
    val itemPosProvider = remember { { itemPos.value } }

    val nearCat by remember(categories.size) {
        derivedStateOf {
            catPos.value.roundToInt().coerceIn(0, (categories.size - 1).coerceAtLeast(0))
        }
    }
    val nearCatNow by rememberUpdatedState(nearCat)
    val centerItem by remember { derivedStateOf { itemPos.value.roundToInt() } }

    val catFadeProvider = remember {
        { 1f - (abs(catPos.value - catPos.value.roundToInt()) * 2f).coerceIn(0f, 1f) }
    }

    // Pick the first tab (Recent, or All Apps when nothing was opened yet) and keep the same tab
    // selected when tabs are added or removed (new category, newly detected category ...).
    var currentKey by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    val keys = categories.map { it.key }
    LaunchedEffect(keys) {
        val recentHasItems = categories.firstOrNull { it.key == KEY_RECENT }?.items?.isNotEmpty() == true
        val wanted = currentKey ?: if (recentHasItems) KEY_RECENT else KEY_APPS
        val found = keys.indexOf(wanted)
        val idx = if (found >= 0) found else keys.indexOf(KEY_RECENT).coerceAtLeast(0)
        catPos.snapTo(idx.toFloat())
        currentKey = keys.getOrNull(idx)
        ready = true
    }
    LaunchedEffect(nearCat) {
        if (ready) keys.getOrNull(nearCat)?.let { currentKey = it }
    }

    // Remember the selected item of each category while moving between categories.
    var lastCatKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(nearCat, categories) {
        val cat = categories.getOrNull(nearCat) ?: return@LaunchedEffect
        if (cat.key != lastCatKey) {
            lastCatKey?.let { savedItem[it] = itemPos.value.roundToInt() }
            val restored = (savedItem[cat.key] ?: 0).coerceIn(0, (cat.items.size - 1).coerceAtLeast(0))
            itemPos.snapTo(restored.toFloat())
            lastCatKey = cat.key
        }
    }

    val itemCount = categories.getOrNull(nearCat)?.items?.size ?: 0
    LaunchedEffect(itemCount) {
        val last = (itemCount - 1).coerceAtLeast(0).toFloat()
        if (itemPos.value > last) itemPos.snapTo(last)
    }

    // Light haptic tick whenever the selection changes.
    var hapticReady by remember { mutableStateOf(false) }
    LaunchedEffect(nearCat, centerItem) {
        if (hapticReady) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        hapticReady = true
    }

    val snap = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)

    // Back never leaves to another launcher: close overlays first, then return to the first tab.
    BackHandler {
        when {
            searchOpen -> searchOpen = false
            bubble != null -> bubble = null
            else -> scope.launch {
                if (catPos.value.roundToInt() != 0) {
                    catPos.animateTo(0f, snap)
                } else if (itemPos.value.roundToInt() != 0) {
                    itemPos.animateTo(0f, snap)
                }
            }
        }
    }

    fun activate(item: XmbItem) {
        when (item) {
            is XmbItem.AppEntry -> viewModel.onAppLaunchRequest(item.app)
            is XmbItem.ActionEntry -> item.onClick()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        XmbBackground(enableWallpaper = state.enableWallpaper)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
        ) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val geo = remember(wPx, hPx, density) { XmbGeometry(wPx, hPx, density) }
            val rowWidth: Dp = with(density) {
                (geo.w - (geo.selX - geo.iconPx / 2f) - 36.dp.toPx())
                    .coerceAtLeast(120.dp.toPx())
                    .toDp()
            }

            val onDoubleTap: ((Offset) -> Unit)? =
                if (state.doubleTapToLock) ({ _: Offset -> context.lockScreen() }) else null

            fun appBubble(app: AppInfo, index: Int): BubbleState {
                val y = geo.itemY(index, itemPos.value)
                val below = y < geo.h / 2f
                val entries = buildList {
                    if (!app.isHidden && app.folderId == null) {
                        add(
                            BubbleEntry(
                                context.getString(
                                    if (app.isFavourite) R.string.remove_favourite
                                    else R.string.add_favourite
                                )
                            ) { viewModel.onToggleFavouriteAppClick(app) }
                        )
                    }
                    if (!app.isHidden) {
                        if (app.folderId == null) {
                            add(BubbleEntry(context.getString(R.string.add_to_folder)) {
                                viewModel.showFolderPicker(app, fromHome = false)
                            })
                        } else {
                            add(BubbleEntry(context.getString(R.string.remove_from_folder)) {
                                viewModel.removeFromFolder(app)
                            })
                        }
                    }
                    add(BubbleEntry(context.getString(R.string.rename)) {
                        viewModel.onRenameAppClick(app)
                    })
                    add(
                        BubbleEntry(
                            context.getString(if (app.isHidden) R.string.unhide_app else R.string.hide_app)
                        ) { viewModel.onToggleHideClick(app) }
                    )
                    add(BubbleEntry(context.getString(R.string.launch_delay)) {
                        viewModel.onLaunchDelayClick(app)
                    })
                    if (app.isShortcut) {
                        add(BubbleEntry(context.getString(R.string.delete_shortcut)) {
                            onDeleteShortcut(app)
                        })
                    } else {
                        add(BubbleEntry(context.getString(R.string.app_info)) {
                            context.launchAppInfo(app)
                        })
                        add(BubbleEntry(context.getString(R.string.uninstall)) {
                            context.uninstallApp(app)
                        })
                    }
                }
                return BubbleState(
                    anchorX = geo.selX,
                    anchorY = if (below) y + geo.rowHeight / 2f else y - geo.rowHeight / 2f,
                    below = below,
                    entries = entries
                )
            }

            fun folderBubble(info: FolderInfo, catIndex: Int): BubbleState = BubbleState(
                anchorX = geo.selX + (catIndex - catPos.value) * geo.catSpacing,
                anchorY = geo.barY + geo.catIconPx * 1.1f,
                below = true,
                entries = listOf(
                    BubbleEntry(context.getString(R.string.rename)) {
                        viewModel.showRenameFolder(info.folder)
                    },
                    BubbleEntry(context.getString(R.string.delete)) {
                        viewModel.showDeleteFolder(info.folder)
                    }
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(geo, state.doubleTapToLock) {
                        detectTapGestures(
                            onDoubleTap = onDoubleTap,
                            onLongPress = { p ->
                                val cats = categoriesNow
                                val ci = geo.hitCategory(p, catPos.value, cats.size)
                                val items = cats.getOrNull(nearCatNow)?.items.orEmpty()
                                val j = if (ci == null) geo.hitItem(p, itemPos.value, items.size) else null
                                val folder = if (ci != null) cats[ci].folder else null
                                val app = if (j != null) (items[j] as? XmbItem.AppEntry)?.app else null
                                when {
                                    ci != null && folder != null -> bubble = folderBubble(folder, ci)
                                    j != null && app != null -> bubble = appBubble(app, j)
                                    else -> searchOpen = true
                                }
                            },
                            onTap = { p ->
                                val cats = categoriesNow
                                val ci = geo.hitCategory(p, catPos.value, cats.size)
                                if (ci != null) {
                                    scope.launch { catPos.animateTo(ci.toFloat(), snap) }
                                } else {
                                    val items = cats.getOrNull(nearCatNow)?.items.orEmpty()
                                    val j = geo.hitItem(p, itemPos.value, items.size)
                                    if (j != null) {
                                        if (j == itemPos.value.roundToInt()) {
                                            activate(items[j])
                                        } else {
                                            scope.launch { itemPos.animateTo(j.toFloat(), snap) }
                                        }
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(geo) {
                        var axis = 0 // 0 = undecided, 1 = horizontal, 2 = vertical
                        var dx = 0f
                        var dy = 0f
                        var startCat = 0f
                        var startItem = 0f
                        val tracker = VelocityTracker()

                        detectDragGestures(
                            onDragStart = {
                                axis = 0
                                dx = 0f
                                dy = 0f
                                startCat = catPos.value
                                startItem = itemPos.value
                                tracker.resetTracking()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                dx += amount.x
                                dy += amount.y
                                if (axis == 0 && max(abs(dx), abs(dy)) > viewConfiguration.touchSlop) {
                                    axis = if (abs(dx) > abs(dy)) 1 else 2
                                }
                                val lastCat = (categoriesNow.size - 1).coerceAtLeast(0)
                                val lastItem = ((categoriesNow.getOrNull(nearCatNow)?.items?.size ?: 0) - 1)
                                    .coerceAtLeast(0)
                                when (axis) {
                                    1 -> scope.launch {
                                        catPos.snapTo(
                                            (startCat - dx / geo.catSpacing)
                                                .coerceIn(-0.3f, lastCat + 0.3f)
                                        )
                                    }

                                    2 -> scope.launch {
                                        itemPos.snapTo(
                                            (startItem - dy / geo.itemSpacing)
                                                .coerceIn(-0.9f, lastItem + 0.9f)
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                val velocity = tracker.calculateVelocity()
                                val lastCat = (categoriesNow.size - 1).coerceAtLeast(0)
                                val lastItem = ((categoriesNow.getOrNull(nearCatNow)?.items?.size ?: 0) - 1)
                                    .coerceAtLeast(0)
                                when (axis) {
                                    1 -> scope.launch {
                                        val projected = catPos.value - (velocity.x / geo.catSpacing) * 0.12f
                                        val target = projected.roundToInt()
                                            .coerceIn(catPos.value.roundToInt() - 2, catPos.value.roundToInt() + 2)
                                            .coerceIn(0, lastCat)
                                        catPos.animateTo(target.toFloat(), snap)
                                    }

                                    2 -> scope.launch {
                                        val v = itemPos.value
                                        when {
                                            v > lastItem + 0.6f -> {
                                                itemPos.animateTo(lastItem.toFloat(), snap)
                                            }

                                            v < -0.6f -> {
                                                itemPos.animateTo(0f, snap)
                                                context.showNotificationDrawer()
                                            }

                                            else -> {
                                                // Fast fling: let it coast, then settle on the nearest item.
                                                val itemsPerSecond = -velocity.y / geo.itemSpacing
                                                itemPos.updateBounds(0f, lastItem.toFloat())
                                                itemPos.animateDecay(
                                                    itemsPerSecond,
                                                    exponentialDecay<Float>(frictionMultiplier = 0.45f)
                                                )
                                                itemPos.updateBounds(null, null)
                                                itemPos.animateTo(
                                                    itemPos.value.roundToInt().coerceIn(0, lastItem).toFloat(),
                                                    snap
                                                )
                                            }
                                        }
                                    }
                                }
                                axis = 0
                            },
                            onDragCancel = {
                                axis = 0
                                scope.launch {
                                    catPos.animateTo(catPos.value.roundToInt().toFloat(), snap)
                                    val lastItem = ((categoriesNow.getOrNull(nearCatNow)?.items?.size ?: 0) - 1)
                                        .coerceAtLeast(0)
                                    itemPos.animateTo(
                                        itemPos.value.roundToInt().coerceIn(0, lastItem).toFloat(),
                                        snap
                                    )
                                }
                            }
                        )
                    }
            ) {
                // Clock (top right, like the PS3)
                XmbClock(
                    twentyFourHour = state.twentyFourHourFormat,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 20.dp)
                )

                // Category bar
                categories.forEachIndexed { index, cat ->
                    key(cat.key) {
                        XmbCategoryIcon(
                            category = cat,
                            index = index,
                            geo = geo,
                            catPosProvider = catPosProvider
                        )
                    }
                }

                // Items of the nearest category (only a window around the selection is composed)
                val currentCat = categories.getOrNull(nearCat)
                val items = currentCat?.items.orEmpty()
                if (items.isEmpty()) {
                    Text(
                        text = currentCat?.emptyText ?: "Nothing here yet",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (geo.selX - geo.iconPx / 2f).roundToInt(),
                                    (geo.selY - geo.rowHeight / 2f).roundToInt()
                                )
                            }
                            .width(rowWidth)
                            .graphicsLayer { alpha = 0.7f * catFadeProvider() },
                        style = LocalTextStyle.current.copy(shadow = TextShadow)
                    )
                } else {
                    val lo = (centerItem - 6).coerceAtLeast(0)
                    val hi = (centerItem + 8).coerceAtMost(items.lastIndex)
                    for (j in lo..hi) {
                        val item = items[j]
                        key(item.key) {
                            XmbItemRow(
                                item = item,
                                index = j,
                                geo = geo,
                                rowWidth = rowWidth,
                                itemPosProvider = itemPosProvider,
                                catFadeProvider = catFadeProvider,
                                viewModel = viewModel,
                                iconCacheRevision = iconCacheRevision
                            )
                        }
                    }
                }
            }

            // A-Z bar beside the list (only for alphabetical lists)
            val currentCategory = categories.getOrNull(nearCat)
            val showAlphabet = currentCategory != null &&
                    currentCategory.key != KEY_RECENT &&
                    currentCategory.key != KEY_SETTINGS &&
                    currentCategory.key != KEY_FAVOURITES &&
                    currentCategory.items.size >= 6
            if (showAlphabet) {
                val alphaItems = currentCategory?.items.orEmpty()
                XmbAlphabetBar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(y = 24.dp)
                        .padding(end = 2.dp)
                        .width(26.dp)
                        .height(with(density) { min(hPx * 0.56f, 27 * 20.dp.toPx()).toDp() }),
                    activeLetter = {
                        alphaItems.getOrNull(itemPos.value.roundToInt())?.let { letterOf(it.title) }
                    },
                    onLetterSelected = { letter ->
                        dragLetter = letter
                        scope.launch {
                            itemPos.snapTo(indexForLetter(alphaItems, letter).toFloat())
                        }
                    },
                    onFinished = {
                        dragLetter = null
                        scope.launch {
                            itemPos.animateTo(
                                itemPos.value.roundToInt()
                                    .coerceIn(0, (alphaItems.size - 1).coerceAtLeast(0)).toFloat(),
                                snap
                            )
                        }
                    }
                )
            }

            dragLetter?.let { letter ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(88.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter.toString(),
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            bubble?.let { b ->
                XmbBubble(state = b, onDismiss = { bubble = null })
            }
        }

        if (searchOpen) {
            XmbSearchOverlay(
                apps = categories.firstOrNull { it.key == KEY_APPS }
                    ?.items.orEmpty().mapNotNull { (it as? XmbItem.AppEntry)?.app },
                autoOpenSingle = state.autoOpenApp,
                viewModel = viewModel,
                iconCacheRevision = iconCacheRevision,
                onLaunch = { app ->
                    searchOpen = false
                    viewModel.onAppLaunchRequest(app)
                },
                onDismiss = { searchOpen = false }
            )
        }
    }

    if (showNewCategory) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewCategory = false },
            title = { Text("New Category") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) viewModel.createCategory(trimmed)
                        showNewCategory = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategory = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun XmbCategoryIcon(
    category: XmbCategory,
    index: Int,
    geo: XmbGeometry,
    catPosProvider: () -> Float
) {
    val iconDp = 48.dp
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (geo.selX + (index - catPosProvider()) * geo.catSpacing - geo.catIconPx / 2f)
                        .roundToInt(),
                    (geo.barY - geo.catIconPx / 2f).roundToInt()
                )
            }
            .size(iconDp)
            .graphicsLayer {
                val sel = (1f - abs(index - catPosProvider())).coerceIn(0f, 1f)
                val s = 1f + 0.30f * sel
                scaleX = s
                scaleY = s
                alpha = 0.5f + 0.5f * sel
            },
        contentAlignment = Alignment.Center
    ) {
        XmbGlyphIcon(glyph = category.glyph, size = iconDp)
    }

    Text(
        text = category.title,
        color = Color.White,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        style = LocalTextStyle.current.copy(shadow = TextShadow),
        modifier = Modifier
            .offset {
                IntOffset(
                    (geo.selX + (index - catPosProvider()) * geo.catSpacing - geo.catSpacing * 0.62f)
                        .roundToInt(),
                    (geo.barY + geo.catIconPx * 0.82f).roundToInt()
                )
            }
            .width(96.dp * 1.24f)
            .graphicsLayer {
                alpha = (1f - abs(index - catPosProvider()) * 1.6f).coerceIn(0f, 1f)
            }
    )
}

@Composable
private fun XmbAppIcon(
    app: AppInfo,
    size: Dp,
    sizePx: Int,
    viewModel: HomeViewModel,
    iconCacheRevision: Int
) {
    val icon by produceState<ImageBitmap?>(null, app.id, sizePx to iconCacheRevision) {
        value = viewModel.loadAppIcon(app, sizePx)
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(
            Modifier
                .size(size)
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
        )
    }
}

@Composable
private fun XmbItemRow(
    item: XmbItem,
    index: Int,
    geo: XmbGeometry,
    rowWidth: Dp,
    itemPosProvider: () -> Float,
    catFadeProvider: () -> Float,
    viewModel: HomeViewModel,
    iconCacheRevision: Int
) {
    val isSelected by remember(index) {
        derivedStateOf { abs(index - itemPosProvider()) < 0.5f }
    }

    Row(
        modifier = Modifier
            .offset {
                val y = geo.itemY(index, itemPosProvider()) - geo.rowHeight / 2f
                IntOffset((geo.selX - geo.iconPx / 2f).roundToInt(), y.roundToInt())
            }
            .width(rowWidth)
            .height(56.dp)
            .graphicsLayer {
                val d = index - itemPosProvider()
                val ad = abs(d)
                val sel = (1f - ad).coerceIn(0f, 1f)
                val s = 1f + 0.22f * sel
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0.5f)
                val y = geo.selY + d * geo.itemSpacing
                val topFade =
                    ((y - geo.barY - geo.catIconPx * 1.1f) / (geo.itemSpacing * 0.9f)).coerceIn(0f, 1f)
                val bottomFade = (1f - (d - 4.5f) / 2f).coerceIn(0f, 1f)
                alpha = (1f - 0.4f * min(1f, ad)) * topFade * bottomFade * catFadeProvider()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            when (item) {
                is XmbItem.AppEntry -> {
                    XmbAppIcon(
                        app = item.app,
                        size = 44.dp,
                        sizePx = geo.iconPx.roundToInt(),
                        viewModel = viewModel,
                        iconCacheRevision = iconCacheRevision
                    )
                    if (item.app.showNotificationDot) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(10.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }

                is XmbItem.ActionEntry -> {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        XmbGlyphIcon(glyph = item.glyph, size = 28.dp)
                    }
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = item.title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.copy(shadow = TextShadow)
        )
    }
}

@Composable
private fun XmbAlphabetBar(
    modifier: Modifier,
    activeLetter: () -> Char?,
    onLetterSelected: (Char) -> Unit,
    onFinished: () -> Unit
) {
    val active by remember { derivedStateOf { activeLetter() } }

    fun letterAt(y: Float, height: Int): Char {
        val index = ((y / height.coerceAtLeast(1)) * ALPHABET.size).toInt()
        return ALPHABET[index.coerceIn(0, ALPHABET.lastIndex)]
    }

    Column(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { p ->
                    onLetterSelected(letterAt(p.y, size.height))
                    onFinished()
                })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { p -> onLetterSelected(letterAt(p.y, size.height)) },
                    onDrag = { change, _ ->
                        change.consume()
                        onLetterSelected(letterAt(change.position.y, size.height))
                    },
                    onDragEnd = { onFinished() },
                    onDragCancel = { onFinished() }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        ALPHABET.forEach { ch ->
            Text(
                text = ch.toString(),
                color = Color.White.copy(alpha = if (active == ch) 1f else 0.55f),
                fontSize = 10.sp,
                fontWeight = if (active == ch) FontWeight.Bold else FontWeight.Normal,
                style = LocalTextStyle.current.copy(shadow = TextShadow)
            )
        }
    }
}

/**
 * Floating menu. It opens below the pressed item when that item is in the upper half of the
 * screen and above it when the item is in the lower half.
 */
@Composable
private fun XmbBubble(state: BubbleState, onDismiss: () -> Unit) {
    Layout(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        content = {
            Column(
                modifier = Modifier
                    .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                    .background(Color(0xF01A2033), RoundedCornerShape(18.dp))
                    .padding(vertical = 6.dp)
            ) {
                state.entries.forEach { entry ->
                    Text(
                        text = entry.label,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                entry.onClick()
                            }
                            .padding(horizontal = 20.dp, vertical = 11.dp)
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val margin = 8.dp.roundToPx()
        val gap = 10.dp.roundToPx()
        val placeable = measurables.first().measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = (constraints.maxWidth * 0.72f).toInt()
            )
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val minX = margin
            val maxX = max(minX, constraints.maxWidth - placeable.width - margin)
            val x = (state.anchorX - 28.dp.roundToPx()).roundToInt().coerceIn(minX, maxX)
            val rawY = if (state.below) {
                state.anchorY + gap
            } else {
                state.anchorY - gap - placeable.height
            }
            val maxY = max(0, constraints.maxHeight - placeable.height)
            placeable.place(x, rawY.roundToInt().coerceIn(0, maxY))
        }
    }
}

@Composable
private fun XmbSearchOverlay(
    apps: List<AppInfo>,
    autoOpenSingle: Boolean,
    viewModel: HomeViewModel,
    iconCacheRevision: Int,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val iconPx = with(density) { 40.dp.roundToPx() }
    val matches = remember(query, apps) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.name.contains(q, ignoreCase = true) }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(query) {
        if (autoOpenSingle && query.isNotBlank() && matches.size == 1) onLaunch(matches[0])
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { matches.firstOrNull()?.let(onLaunch) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search apps",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 20.sp
                                )
                            }
                            inner()
                        }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(matches, key = { it.id }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLaunch(app) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        XmbAppIcon(
                            app = app,
                            size = 40.dp,
                            sizePx = iconPx,
                            viewModel = viewModel,
                            iconCacheRevision = iconCacheRevision
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = app.name,
                            color = Color.White,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun XmbClock(twentyFourHour: Boolean, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L) + 50L)
        }
    }
    val timeFormat = remember(twentyFourHour) {
        SimpleDateFormat(if (twentyFourHour) "HH:mm" else "h:mm a", Locale.getDefault())
    }
    val dateFormat = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = timeFormat.format(Date(now)),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            style = LocalTextStyle.current.copy(shadow = TextShadow)
        )
        Text(
            text = dateFormat.format(Date(now)),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            style = LocalTextStyle.current.copy(shadow = TextShadow)
        )
    }
}
