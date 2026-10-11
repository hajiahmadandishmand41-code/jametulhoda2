package com.jametulhoda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jametulhoda.app.JametulHodaApp
import com.jametulhoda.app.data.AppRepository
import com.jametulhoda.app.data.ContentItem
import com.jametulhoda.app.ui.screens.AboutScreen
import com.jametulhoda.app.ui.screens.AccountScreen
import com.jametulhoda.app.ui.screens.BookListScreen
import com.jametulhoda.app.ui.screens.ContactScreen
import com.jametulhoda.app.ui.screens.DetailScreen
import com.jametulhoda.app.ui.screens.HomeScreen
import com.jametulhoda.app.ui.screens.LessonsScreen
import com.jametulhoda.app.ui.screens.MediaTabsScreen
import com.jametulhoda.app.ui.screens.PlaybackScreen
import com.jametulhoda.app.ui.screens.PostListScreen
import com.jametulhoda.app.ui.screens.SearchScreen
import com.jametulhoda.app.ui.screens.SettingsScreen
import com.jametulhoda.app.ui.screens.TopicsListScreen
import com.jametulhoda.app.ui.theme.JametulHodaTheme
import com.jametulhoda.app.util.Downloader

/** مسیرهای پشتهٔ ناوبری دست‌ساز (بدون وابستگی navigation-compose). */
sealed class Route {
    data object Home : Route()
    data object More : Route()
    data class PostList(val type: String, val title: String) : Route()
    data object Books : Route()
    data object Lessons : Route()
    data object MediaTabs : Route()
    data class Detail(val kind: String, val id: Int, val slug: String, val title: String) : Route()
    data class Playback(val playerKind: String, val url: String, val title: String) : Route()
    data object TopicsList : Route()
    data object Search : Route()
    data object About : Route()
    data object Contact : Route()
    data object Account : Route()
    data object Settings : Route()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as JametulHodaApp
    val repo: AppRepository = app.repository
    val snackbar = remember { SnackbarHostState() }

    val backStack = remember { mutableStateListOf<Route>(Route.Home) }
    var darkMode by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val systemDark = isSystemInDarkTheme()
    val useDark = darkMode ?: systemDark

    // RTL سراسری (فارسی) مانند وب‌سایت.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        JametulHodaTheme(darkTheme = useDark) {
            val current = backStack.last()

            BackHandler(enabled = backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }

            fun push(route: Route) {
                backStack.add(route)
            }

            fun pop() {
                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            }

            fun selectTab(route: Route) {
                backStack.clear()
                backStack.add(route)
            }

            fun openItem(item: ContentItem) {
                when (item.kind) {
                    "media" -> push(
                        Route.Playback(
                            playerKind = if (item.type == "video") "video" else "audio",
                            url = item.mediaUrl,
                            title = item.title,
                        ),
                    )
                    "topic" -> push(Route.Detail("topic", item.id, item.slug, item.title))
                    "book" -> push(Route.Detail("book", item.id, item.slug, item.title))
                    "lesson" -> push(Route.Detail("lesson", item.id, item.slug, item.title))
                    else -> push(Route.Detail("post", item.id, item.slug, item.title))
                }
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    TopAppBar(
                        title = { Text(routeTitle(current), style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = {
                            if (backStack.size > 1) {
                                IconButton(onClick = ::pop) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                                }
                            }
                        },
                        actions = {
                            if (current !is Route.Search) {
                                IconButton(onClick = { push(Route.Search) }) {
                                    Icon(Icons.Default.Search, contentDescription = "جستجو")
                                }
                            }
                            IconButton(onClick = { push(Route.Account) }) {
                                Icon(Icons.Default.AccountCircle, contentDescription = "حساب کاربری")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                },
                bottomBar = {
                    if (backStack.size == 1) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            NavigationBarItem(
                                selected = current is Route.Home,
                                onClick = { selectTab(Route.Home) },
                                icon = { Icon(Icons.Default.Home, contentDescription = "خانه") },
                                label = { Text("خانه") },
                            )
                            NavigationBarItem(
                                selected = current is Route.PostList || current is Route.Detail,
                                onClick = { selectTab(Route.PostList("news", "اخبار")) },
                                icon = { Icon(Icons.Default.Menu, contentDescription = "اخبار") },
                                label = { Text("اخبار") },
                            )
                            NavigationBarItem(
                                selected = current is Route.Books,
                                onClick = { selectTab(Route.Books) },
                                icon = { Icon(Icons.Default.List, contentDescription = "کتابخانه") },
                                label = { Text("کتابخانه") },
                            )
                            NavigationBarItem(
                                selected = current is Route.MediaTabs || current is Route.Playback,
                                onClick = { selectTab(Route.MediaTabs) },
                                icon = { Icon(Icons.Default.PlayArrow, contentDescription = "رسانه") },
                                label = { Text("رسانه") },
                            )
                            NavigationBarItem(
                                selected = current is Route.More || current is Route.TopicsList ||
                                    current is Route.Search || current is Route.About ||
                                    current is Route.Contact || current is Route.Account ||
                                    current is Route.Settings,
                                onClick = { selectTab(Route.More) },
                                icon = { Icon(Icons.Default.Menu, contentDescription = "بیشتر") },
                                label = { Text("بیشتر") },
                            )
                        }
                    }
                },
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (val route = current) {
                        is Route.Home -> HomeScreen(
                            repo = repo,
                            onItem = ::openItem,
                            onRoute = ::push,
                        )

                        is Route.More -> MoreMenu(
                            onTopics = { push(Route.TopicsList) },
                            onSearch = { push(Route.Search) },
                            onAbout = { push(Route.About) },
                            onContact = { push(Route.Contact) },
                            onAccount = { push(Route.Account) },
                            onSettings = { push(Route.Settings) },
                            onLessons = { push(Route.Lessons) },
                            onBooks = { push(Route.Books) },
                            onMedia = { push(Route.MediaTabs) },
                            onNews = { push(Route.PostList("news", "اخبار")) },
                        )

                        is Route.PostList -> PostListScreen(
                            repo = repo,
                            type = route.type,
                            onItem = ::openItem,
                        )

                        is Route.Books -> BookListScreen(
                            repo = repo,
                            onItem = ::openItem,
                        )

                        is Route.Lessons -> LessonsScreen(
                            repo = repo,
                            onItem = ::openItem,
                        )

                        is Route.MediaTabs -> MediaTabsScreen(
                            repo = repo,
                            onPlay = { item ->
                                push(
                                    Route.Playback(
                                        playerKind = if (item.type == "video") "video" else "audio",
                                        url = item.mediaUrl,
                                        title = item.title,
                                    ),
                                )
                            },
                        )

                        is Route.Detail -> DetailScreen(
                            repo = repo,
                            kind = route.kind,
                            id = route.id,
                            slug = route.slug,
                            onPlayback = { playerKind, url, title -> push(Route.Playback(playerKind, url, title)) },
                            onItem = ::openItem,
                            snackbar = snackbar,
                        )

                        is Route.Playback -> PlaybackScreen(
                            playerKind = route.playerKind,
                            url = route.url,
                            title = route.title,
                            snackbar = snackbar,
                        )

                        is Route.TopicsList -> TopicsListScreen(
                            repo = repo,
                            onTopic = { item -> push(Route.Detail("topic", item.id, item.slug, item.title)) },
                        )

                        is Route.Search -> SearchScreen(
                            repo = repo,
                            onItem = ::openItem,
                        )

                        is Route.About -> AboutScreen(repo = repo)
                        is Route.Contact -> ContactScreen(repo = repo, snackbar = snackbar)
                        is Route.Account -> AccountScreen(repo = repo, snackbar = snackbar)
                        is Route.Settings -> SettingsScreen(
                            darkMode = darkMode,
                            onDarkMode = { darkMode = it },
                        )
                    }
                }
            }
        }
    }
}

private fun routeTitle(route: Route): String = when (route) {
    is Route.Home -> "جامعه‌الهدی"
    is Route.More -> "بیشتر"
    is Route.PostList -> route.title
    is Route.Books -> "کتابخانه"
    is Route.Lessons -> "درس‌ها"
    is Route.MediaTabs -> "رسانه"
    is Route.Detail -> route.title
    is Route.Playback -> route.title
    is Route.TopicsList -> "موضوعات"
    is Route.Search -> "جستجو"
    is Route.About -> "درباره ما"
    is Route.Contact -> "تماس با ما"
    is Route.Account -> "حساب کاربری"
    is Route.Settings -> "تنظیمات"
}

@Composable
private fun MoreMenu(
    onTopics: () -> Unit,
    onSearch: () -> Unit,
    onAbout: () -> Unit,
    onContact: () -> Unit,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
    onBooks: () -> Unit,
    onLessons: () -> Unit,
    onMedia: () -> Unit,
    onNews: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MenuRow(Icons.Default.List, "موضوعات", "دسته‌بندی محتوای سایت", onTopics)
        HorizontalDivider()
        MenuRow(Icons.Default.Search, "جستجوی پیشرفته", "جستجو در همهٔ بخش‌ها", onSearch)
        HorizontalDivider()
        MenuRow(Icons.Default.Home, "اخبار و اطلاعیه‌ها", "خبرها، اطلاعیه‌ها و رویدادها", onNews)
        HorizontalDivider()
        MenuRow(Icons.Default.List, "کتابخانه", "کتاب‌ها", onBooks)
        HorizontalDivider()
        MenuRow(Icons.Default.List, "درس‌ها", "مجموعه‌درس‌های جامعه‌الهدی", onLessons)
        HorizontalDivider()
        MenuRow(Icons.Default.PlayArrow, "رسانه", "صوت‌ها و ویدیوها", onMedia)
        HorizontalDivider()
        MenuRow(Icons.Default.Info, "درباره ما", "معرفی جامعه‌الهدی", onAbout)
        HorizontalDivider()
        MenuRow(Icons.Default.Email, "تماس با ما", "ارسال پیام به مسئولان", onContact)
        HorizontalDivider()
        MenuRow(Icons.Default.AccountCircle, "حساب کاربری", "ورود، ثبت‌نام و پروفایل", onAccount)
        HorizontalDivider()
        MenuRow(Icons.Default.Settings, "تنظیمات", "تم روشن/تاریک و دربارهٔ برنامه", onSettings)
        HorizontalDivider()
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
