package com.minimo.launcher.ui.xmb

import android.app.Activity
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.minimo.launcher.ui.home.components.AppListBottomSheetDialog
import com.minimo.launcher.utils.Constants
import com.minimo.launcher.utils.launchAppInfo
import com.minimo.launcher.utils.lockScreen
import com.minimo.launcher.utils.showNotificationDrawer
import com.minimo.launcher.utils.uninstallApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val folder: FolderInfo? = null
)

private const val KEY_SETTINGS = "settings"
private const val KEY_FAVOURITES = "favourites"
private const val KEY_APPS = "apps"

// English labels. Move these to strings.xml if you want them translated.
private const val TITLE_SETTINGS = "Settings"
private const val TITLE_FAVOURITES = "Favourites"
private const val TITLE_APPS = "Apps"
private const val TITLE_EMPTY = "Nothing here yet"

private fun buildCategories(
    state: HomeScreenState,
    context: Context,
    openDrawer: () -> Unit,
    openSettings: () -> Unit
): List<XmbCategory> {
    val settings = XmbCategory(
        key = KEY_SETTINGS,
        title = TITLE_SETTINGS,
        glyph = XmbGlyph.Settings,
        items = listOf(
            XmbItem.ActionEntry("action:launcher", "Launcher Settings", XmbGlyph.Settings) {
                openSettings()
            },
            XmbItem.ActionEntry("action:notifications", "Notifications", XmbGlyph.Bell) {
                context.showNotificationDrawer()
            },
            XmbItem.ActionEntry("action:lock", "Lock Screen", XmbGlyph.Lock) {
                context.lockScreen()
            },
            XmbItem.ActionEntry("action:drawer", "App Drawer", XmbGlyph.Apps) {
                openDrawer()
            }
        )
    )

    val favourites = XmbCategory(
        key = KEY_FAVOURITES,
        title = TITLE_FAVOURITES,
        glyph = XmbGlyph.Star,
        items = state.favouriteApps
            .filter { it.folderId == null && !it.isHidden }
            .map { XmbItem.AppEntry(it) }
    )

    val allApps = XmbCategory(
        key = KEY_APPS,
        title = TITLE_APPS,
        glyph = XmbGlyph.Apps,
        items = state.allApps
            .filter { !it.isHidden && it.packageName != Constants.MINIMO_SETTINGS_PACKAGE }
            .map { XmbItem.AppEntry(it) }
    )

    val folders = state.folders.map { info ->
        XmbCategory(
            key = "folder:${info.folder.id}",
            title = info.folder.name,
            glyph = XmbGlyph.Folder,
            items = info.apps.map { XmbItem.AppEntry(it) },
            folder = info
        )
    }

    return listOf(settings, favourites, allApps) + folders
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
 * PS3 XrossMediaBar style home: categories run across the top, the items of the selected
 * category run down below it.
 *
 *  - swipe left/right      -> change category
 *  - swipe up/down         -> move through the items
 *  - tap                   -> select (tap a non-selected entry) / launch (tap the selected item)
 *  - long press an app     -> app options (favourite, rename, hide, folder, uninstall ...)
 *  - long press a folder   -> folder options
 *  - long press empty area -> launcher settings
 *  - pull past the last item -> app drawer, pull past the first item -> notification panel
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

    val openDrawer by rememberUpdatedState(onOpenAppDrawer)
    val openSettings by rememberUpdatedState(onSettingsClick)
    val categories = remember(state.favouriteApps, state.allApps, state.folders) {
        buildCategories(state, context, { openDrawer() }, { openSettings() })
    }
    val categoriesNow by rememberUpdatedState(categories)

    // catPos: fractional index of the selected category. itemPos: same for the item list.
    val catPos = remember { Animatable(1f) }
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

    val catFadeProvider = remember { { 1f - (abs(catPos.value - catPos.value.roundToInt()) * 2f).coerceIn(0f, 1f) } }

    // Keep the same category selected when categories are added/removed (new folder etc.)
    var currentKey by remember { mutableStateOf(KEY_FAVOURITES) }
    val keys = categories.map { it.key }
    LaunchedEffect(keys) {
        val idx = keys.indexOf(currentKey)
        val target = if (idx >= 0) idx else keys.indexOf(KEY_FAVOURITES).coerceAtLeast(0)
        catPos.snapTo(target.toFloat())
    }
    LaunchedEffect(nearCat) {
        keys.getOrNull(nearCat)?.let { currentKey = it }
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

    var optionsApp by remember { mutableStateOf<AppInfo?>(null) }
    var optionsFolder by remember { mutableStateOf<FolderInfo?>(null) }

    val snap = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)

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
                (geo.w - (geo.selX - geo.iconPx / 2f) - 12.dp.toPx())
                    .coerceAtLeast(120.dp.toPx())
                    .toDp()
            }

            val onDoubleTap: ((Offset) -> Unit)? =
                if (state.doubleTapToLock) ({ _: Offset -> context.lockScreen() }) else null

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(geo, state.doubleTapToLock) {
                        detectTapGestures(
                            onDoubleTap = onDoubleTap,
                            onLongPress = { p ->
                                val cats = categoriesNow
                                val ci = geo.hitCategory(p, catPos.value, cats.size)
                                if (ci != null) {
                                    cats[ci].folder?.let { optionsFolder = it }
                                } else {
                                    val items = cats.getOrNull(nearCatNow)?.items.orEmpty()
                                    val j = geo.hitItem(p, itemPos.value, items.size)
                                    if (j != null) {
                                        scope.launch { itemPos.animateTo(j.toFloat(), snap) }
                                        (items[j] as? XmbItem.AppEntry)?.let { optionsApp = it.app }
                                    } else {
                                        openSettings()
                                    }
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
                                            .coerceIn((catPos.value.roundToInt() - 2), (catPos.value.roundToInt() + 2))
                                            .coerceIn(0, lastCat)
                                        catPos.animateTo(target.toFloat(), snap)
                                    }

                                    2 -> scope.launch {
                                        val v = itemPos.value
                                        when {
                                            v > lastItem + 0.6f -> {
                                                itemPos.animateTo(lastItem.toFloat(), snap)
                                                openDrawer()
                                            }

                                            v < -0.6f -> {
                                                itemPos.animateTo(0f, snap)
                                                context.showNotificationDrawer()
                                            }

                                            else -> {
                                                val projected = v - (velocity.y / geo.itemSpacing) * 0.12f
                                                val target = projected.roundToInt()
                                                    .coerceIn(v.roundToInt() - 6, v.roundToInt() + 6)
                                                    .coerceIn(0, lastItem)
                                                itemPos.animateTo(target.toFloat(), snap)
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
                val items = categories.getOrNull(nearCat)?.items.orEmpty()
                if (items.isEmpty()) {
                    Text(
                        text = TITLE_EMPTY,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (geo.selX - geo.iconPx / 2f).roundToInt(),
                                    (geo.selY - geo.rowHeight / 2f).roundToInt()
                                )
                            }
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
        }
    }

    optionsApp?.let { app ->
        AppListBottomSheetDialog(
            appName = app.name,
            folderId = app.folderId,
            onFolderClick = {
                optionsApp = null
                viewModel.showFolderPicker(app, fromHome = true)
            },
            onRemoveFromFolderClick = {
                optionsApp = null
                viewModel.removeFromFolder(app)
            },
            isFavourite = app.isFavourite,
            isHidden = app.isHidden,
            isShortcut = app.isShortcut,
            onDismiss = { optionsApp = null },
            statusBarVisible = statusBarVisible,
            navigationBarVisible = navigationBarVisible,
            useDarkStatusBarIcons = useDarkStatusBarIcons,
            useDarkNavigationBarIcons = useDarkNavigationBarIcons,
            onToggleFavouriteClick = {
                optionsApp = null
                viewModel.onToggleFavouriteAppClick(app)
            },
            onRenameClick = {
                optionsApp = null
                viewModel.onRenameAppClick(app)
            },
            onToggleHideClick = {
                optionsApp = null
                viewModel.onToggleHideClick(app)
            },
            onAppInfoClick = {
                optionsApp = null
                context.launchAppInfo(app)
            },
            onUninstallClick = {
                optionsApp = null
                context.uninstallApp(app)
            },
            onDeleteShortcutClick = {
                optionsApp = null
                onDeleteShortcut(app)
            },
            onLaunchDelayClick = {
                optionsApp = null
                viewModel.onLaunchDelayClick(app)
            }
        )
    }

    optionsFolder?.let { info ->
        AlertDialog(
            onDismissRequest = { optionsFolder = null },
            title = { Text(info.folder.name) },
            text = {
                Column {
                    TextButton(onClick = {
                        optionsFolder = null
                        viewModel.toggleFolderFavourite(info.folder.id)
                    }) {
                        Text(
                            stringResource(
                                if (info.folder.isFavourite) R.string.remove_favourite
                                else R.string.add_favourite
                            )
                        )
                    }
                    TextButton(onClick = {
                        optionsFolder = null
                        viewModel.showRenameFolder(info.folder)
                    }) { Text(stringResource(R.string.rename)) }
                    TextButton(onClick = {
                        optionsFolder = null
                        viewModel.showDeleteFolder(info.folder)
                    }) { Text(stringResource(R.string.delete)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { optionsFolder = null }) {
                    Text(stringResource(R.string.cancel))
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
                val d = index - itemPosProvider()
                val y = geo.selY + d * geo.itemSpacing + geo.gap * d.coerceIn(-1f, 1f) -
                        geo.rowHeight / 2f
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
                    val px = geo.iconPx.roundToInt()
                    val icon by produceState<ImageBitmap?>(
                        null,
                        item.app.id,
                        px to iconCacheRevision
                    ) {
                        value = viewModel.loadAppIcon(item.app, px)
                    }
                    val bitmap = icon
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp)
                        )
                    } else {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        )
                    }
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
