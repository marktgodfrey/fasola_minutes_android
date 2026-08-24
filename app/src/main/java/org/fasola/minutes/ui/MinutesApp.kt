package org.fasola.minutes.ui

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import org.fasola.minutes.R
import org.fasola.minutes.data.LeaderSummary
import org.fasola.minutes.data.LeaderDetail
import org.fasola.minutes.data.LeaderLesson
import org.fasola.minutes.data.BookSummary
import org.fasola.minutes.data.MinutesRepository
import org.fasola.minutes.data.SingingDetail
import org.fasola.minutes.data.SingingSummary
import org.fasola.minutes.data.SongDetail
import org.fasola.minutes.data.SongLeader
import org.fasola.minutes.data.SongRecording
import org.fasola.minutes.data.SongSummary
import org.fasola.minutes.data.YearCount
import org.fasola.minutes.ui.theme.BarColor
import org.fasola.minutes.ui.theme.BookColor
import org.fasola.minutes.ui.theme.FontColor
import org.fasola.minutes.ui.theme.NavigationColor
import org.fasola.minutes.ui.theme.PaperColor
import org.fasola.minutes.ui.theme.SearchBarColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Section(val label: String, val icon: Int) {
    Leaders("Singers", R.drawable.fa),
    Songs("Songs", R.drawable.sol),
    Singings("Singings", R.drawable.la),
}

private sealed interface Destination {
    data object List : Destination
    data object Help : Destination
    data class Singing(val id: Int) : Destination
    data class SingingText(val id: Int) : Destination
    data class Song(val id: Int) : Destination
    data class Leader(val id: Int) : Destination
    data class LeaderSong(val leaderId: Int, val leaderName: String, val songId: Int, val songTitle: String) : Destination
    data class LeaderLessons(val id: Int, val name: String) : Destination
    data class LocationsMap(val title: String, val singingId: Int? = null, val leaderId: Int? = null) : Destination
    data class SongMap(val id: Int, val title: String) : Destination
}

@Composable
fun MinutesApp(repository: MinutesRepository, initialLeaders: List<LeaderSummary> = emptyList()) {
    val context = LocalContext.current
    val audioPlayer = remember { AudioPlayerController(context) }
    DisposableEffect(audioPlayer) { onDispose { audioPlayer.close() } }
    var section by remember { mutableStateOf(Section.Leaders) }
    val tabStateHolder = rememberSaveableStateHolder()
    val backStack = remember { mutableStateListOf<Destination>(Destination.List) }
    val destination = backStack.last()
    val open: (Destination) -> Unit = { backStack.add(it) }
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    BackHandler(enabled = backStack.size > 1, onBack = back)

    Box(Modifier.fillMaxSize()) {
        // Keep the selected browse screen composed while a detail destination is open. Removing
        // it from composition would reset its remembered query, filters, sorting, and scroll
        // position when the user navigates back.
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(containerColor = PaperColor, contentColor = FontColor) {
                    Section.entries.forEach { item ->
                        NavigationBarItem(
                            selected = section == item,
                            onClick = { section = item },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BookColor,
                                selectedTextColor = FontColor,
                                indicatorColor = PaperColor,
                                unselectedIconColor = FontColor,
                                unselectedTextColor = FontColor,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            when (section) {
                Section.Singings -> tabStateHolder.SaveableStateProvider(Section.Singings.name) {
                    SingingsScreen(
                        repository,
                        Modifier.padding(padding),
                        open = { open(Destination.Singing(it)) },
                        openMap = { open(Destination.LocationsMap("Singing Locations")) },
                        openHelp = { open(Destination.Help) },
                    )
                }
                Section.Songs -> tabStateHolder.SaveableStateProvider(Section.Songs.name) {
                    SongsScreen(
                        repository,
                        Modifier.padding(padding),
                        open = { open(Destination.Song(it)) },
                        openHelp = { open(Destination.Help) },
                    )
                }
                Section.Leaders -> tabStateHolder.SaveableStateProvider(Section.Leaders.name) {
                    LeadersScreen(
                        repository,
                        initialLeaders,
                        Modifier.padding(padding),
                        open = { open(Destination.Leader(it)) },
                        openHelp = { open(Destination.Help) },
                    )
                }
            }
        }

        when (val page = destination) {
            Destination.List -> Unit
            Destination.Help -> HelpScreen(back)
            is Destination.Singing -> SingingScreen(
                repository, page.id, back, audioPlayer,
                openSong = { open(Destination.Song(it)) },
                openText = { open(Destination.SingingText(page.id)) },
                openMap = { title -> open(Destination.LocationsMap(title, singingId = page.id)) },
            )
            is Destination.SingingText -> SingingTextScreen(repository, page.id, back)
            is Destination.Song -> SongScreen(
                repository,
                page.id,
                back = back,
                audioPlayer = audioPlayer,
                openSinging = { open(Destination.Singing(it)) },
                openLeader = { open(Destination.Leader(it)) },
                openSong = { open(Destination.Song(it)) },
                openMap = { title -> open(Destination.SongMap(page.id, title)) },
            )
            is Destination.Leader -> LeaderScreen(
                repository, page.id, back,
                openSong = { songId, songTitle ->
                    open(Destination.LeaderSong(page.id, "", songId, songTitle))
                },
                openAll = { name -> open(Destination.LeaderLessons(page.id, name)) },
                openMap = { title -> open(Destination.LocationsMap(title, leaderId = page.id)) },
            )
            is Destination.LeaderSong -> LeaderSongScreen(
                repository = repository,
                leaderId = page.leaderId,
                leaderName = page.leaderName,
                songId = page.songId,
                songTitle = page.songTitle,
                back = back,
                openSong = { open(Destination.Song(page.songId)) },
                openSinging = { open(Destination.Singing(it)) },
            )
            is Destination.LeaderLessons -> LeaderLessonsScreen(
                repository, page.id, page.name, back, audioPlayer,
                openSinging = { open(Destination.Singing(it)) },
            )
            is Destination.LocationsMap -> LocationsMapScreen(
                repository, page.title, back,
                openSinging = { open(Destination.Singing(it)) },
                singingId = page.singingId,
                leaderId = page.leaderId,
            )
            is Destination.SongMap -> SongHeatmapScreen(repository, page.id, page.title, back)
        }
        audioPlayer.currentTrack?.let {
            FloatingAudioPlayer(
                audioPlayer,
                Modifier.align(Alignment.BottomCenter).padding(
                    start = 8.dp,
                    end = 8.dp,
                    bottom = if (destination == Destination.List) 82.dp else 8.dp,
                ),
            )
        }
    }
}

@Composable
private fun SingingsScreen(
    repository: MinutesRepository,
    modifier: Modifier,
    open: (Int) -> Unit,
    openMap: () -> Unit,
    openHelp: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val data = load(query) { repository.singings(query) }
    val listState = rememberLazyListState()
    val groups = data.value?.groupBy(SingingSummary::year)?.toList().orEmpty()
    val indexEntries = groups.runningFold(0) { itemIndex, (_, singings) ->
        itemIndex + 1 + singings.size
    }.dropLast(1).zip(groups) { itemIndex, group -> ListIndexEntry(group.first.toString(), itemIndex) }
    BrowseScreen("Singings", query, { query = it }, modifier, listState, indexEntries, openHelp, actions = {
        IconButton(openMap) { Icon(Icons.Default.Map, "Map", tint = NavigationColor) }
    }) {
        groups.forEach { (year, singings) ->
            item(key = "singing-header-$year") { SingerSectionHeader(year.toString()) }
            items(singings, key = { it.id }) { SingingRow(it) { open(it.id) } }
        }
    }
}

@Composable
private fun SongsScreen(
    repository: MinutesRepository,
    modifier: Modifier,
    open: (Int) -> Unit,
    openHelp: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable(stateSaver = SongSortModeSaver) { mutableStateOf(SongSortMode.PageNumber) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var filter by rememberSaveable(stateSaver = SongFilterSaver) { mutableStateOf(SongFilter()) }
    val books = load(Unit) { repository.books() }
    var selectedBookId by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(books.value) {
        if (selectedBookId == null) selectedBookId = books.value?.firstOrNull()?.id
    }
    val data = load(selectedBookId to query) {
        selectedBookId?.let { repository.songs(it, query) } ?: emptyList()
    }
    val selectedBook = books.value?.firstOrNull { it.id == selectedBookId }
    val maximumPage = if (selectedBook?.year == 1991) SongFilter.MAX_1991_PAGE else SongFilter.MAX_2025_PAGE
    LaunchedEffect(maximumPage) { filter = filter.forMaximumPage(maximumPage) }
    val songs = data.value.orEmpty().filter(filter::matches).sortedWith(sortMode.comparator)
    val listState = rememberLazyListState()
    LaunchedEffect(sortMode, filter) { listState.scrollToItem(0) }
    BrowseScreen(
        "Songs",
        query,
        { query = it },
        modifier,
        listState = listState,
        openHelp = openHelp,
        header = {
            books.value?.let { availableBooks ->
                BookSelector(availableBooks, selectedBookId) { selectedBookId = it }
            }
        },
        actions = {
            IconButton({ showFilter = true }) {
                Box {
                    Icon(
                        Icons.Default.FilterAlt,
                        if (filter.isActive(maximumPage)) "Filter songs (active)" else "Filter songs",
                        tint = NavigationColor,
                    )
                    if (filter.isActive(maximumPage)) {
                        Text(
                            "•",
                            color = NavigationColor,
                            fontSize = 18.sp,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, "Sort songs", tint = NavigationColor)
                }
                DropdownMenu(sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    SongSortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = { sortMode = mode; sortMenuExpanded = false },
                        )
                    }
                }
            }
        },
    ) {
        if (data.value != null && songs.isEmpty()) {
            item(key = "no-matching-songs") {
                Text(
                    "No songs match the filter provided!",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(songs, key = { it.id }) { SongRow(it, sortMode) { open(it.id) } }
        }
    }
    if (showFilter) {
        SongFilterDialog(filter, maximumPage, dismiss = { showFilter = false }, apply = { filter = it })
    }
}

private enum class SongSortMode(val label: String, val comparator: Comparator<SongSummary>) {
    PageNumber("Page Number", compareBy<SongSummary> { it.id }),
    TotalLessons("Total Lesson Count", compareByDescending<SongSummary> { it.lessonCount }.thenBy { it.id }),
}

private val SongSortModeSaver = Saver<SongSortMode, String>(
    save = { it.name },
    restore = SongSortMode::valueOf,
)

private val SongFilterSaver = listSaver<SongFilter, Any>(
    save = {
        listOf(
            it.pageLow, it.pageHigh,
            ArrayList(it.positions), ArrayList(it.sides), ArrayList(it.times),
            ArrayList(it.modes), ArrayList(it.keys), ArrayList(it.meters),
        )
    },
    restore = {
        SongFilter(
            pageLow = it[0] as Int,
            pageHigh = it[1] as Int,
            positions = (it[2] as ArrayList<*>).filterIsInstance<String>().toSet(),
            sides = (it[3] as ArrayList<*>).filterIsInstance<String>().toSet(),
            times = (it[4] as ArrayList<*>).filterIsInstance<String>().toSet(),
            modes = (it[5] as ArrayList<*>).filterIsInstance<String>().toSet(),
            keys = (it[6] as ArrayList<*>).filterIsInstance<String>().toSet(),
            meters = (it[7] as ArrayList<*>).filterIsInstance<String>().toSet(),
        )
    },
)

@Composable
private fun LeadersScreen(
    repository: MinutesRepository,
    initialLeaders: List<LeaderSummary>,
    modifier: Modifier,
    open: (Int) -> Unit,
    openHelp: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable(stateSaver = LeaderSortModeSaver) { mutableStateOf(LeaderSortMode.Name) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val data = load(query, initialValue = initialLeaders.takeIf { query.isEmpty() }) { repository.leaders(query) }
    val listState = rememberLazyListState()
    val leaders = data.value.orEmpty().sortedWith(sortMode.comparator)
    val groups = if (sortMode == LeaderSortMode.Name) {
        leaders.groupBy(LeaderSummary::lastNameInitial).toList()
    } else {
        emptyList()
    }
    val indexEntries = groups.runningFold(0) { itemIndex, (_, leaders) ->
        itemIndex + 1 + leaders.size
    }.dropLast(1).zip(groups) { itemIndex, group -> ListIndexEntry(group.first, itemIndex) }
    LaunchedEffect(sortMode) { listState.scrollToItem(0) }
    BrowseScreen(
        "Singers", query, { query = it }, modifier, listState, indexEntries, openHelp,
        actions = {
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, "Sort singers", tint = NavigationColor)
                }
                DropdownMenu(sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    LeaderSortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                sortMode = mode
                                sortMenuExpanded = false
                            },
                        )
                    }
                }
            }
        },
    ) {
        if (sortMode == LeaderSortMode.Name) {
            groups.forEach { (initial, groupedLeaders) ->
                item(key = "singer-header-$initial") { SingerSectionHeader(initial) }
                items(groupedLeaders, key = { it.id }) { LeaderRow(it, null) { open(it.id) } }
            }
        } else {
            items(leaders, key = { it.id }) { leader ->
                LeaderRow(leader, sortMode.count(leader)) { open(leader.id) }
            }
        }
    }
}

private enum class LeaderSortMode(val label: String, val comparator: Comparator<LeaderSummary>) {
    Name("Name", org.fasola.minutes.data.leaderNameComparator),
    TotalLessons(
        "Total Lesson Count",
        compareByDescending<LeaderSummary> { it.lessonCount }
            .then(org.fasola.minutes.data.leaderNameComparator),
    ),
    Top20(
        "Top 20 Leader Count",
        compareByDescending<LeaderSummary> { it.top20Count }
            .then(org.fasola.minutes.data.leaderNameComparator),
    );

    fun count(leader: LeaderSummary): Int? = when (this) {
        Name -> null
        TotalLessons -> leader.lessonCount
        Top20 -> leader.top20Count
    }
}

private val LeaderSortModeSaver = Saver<LeaderSortMode, String>(
    save = { it.name },
    restore = LeaderSortMode::valueOf,
)

@Composable
private fun SingerSectionHeader(initial: String) {
    Text(
        text = initial,
        color = FontColor,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        style = MaterialTheme.typography.titleSmall,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseScreen(
    title: String,
    query: String,
    setQuery: (String) -> Unit,
    modifier: Modifier,
    listState: LazyListState = rememberLazyListState(),
    indexEntries: List<ListIndexEntry> = emptyList(),
    openHelp: () -> Unit,
    header: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    rows: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(openHelp) {
                    Icon(Icons.Default.Info, "Help and credits", tint = NavigationColor)
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BookColor,
                titleContentColor = NavigationColor,
            ),
        )
        header()
        TextField(
            value = query,
            onValueChange = setQuery,
            label = { Text("Search $title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SearchBarColor,
                unfocusedContainerColor = SearchBarColor,
                focusedTextColor = NavigationColor,
                unfocusedTextColor = NavigationColor,
                focusedLabelColor = NavigationColor,
                unfocusedLabelColor = NavigationColor.copy(alpha = 0.7f),
                cursorColor = NavigationColor,
                focusedIndicatorColor = NavigationColor,
                unfocusedIndicatorColor = NavigationColor.copy(alpha = 0.6f),
            ),
        )
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = if (indexEntries.isEmpty()) 0.dp else 36.dp),
                content = rows,
            )
            if (indexEntries.isNotEmpty()) {
                SideIndex(indexEntries, listState, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

private data class ListIndexEntry(val label: String, val itemIndex: Int)

@Composable
private fun SideIndex(entries: List<ListIndexEntry>, listState: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var height by remember { mutableStateOf(1) }
    val jumpAt: (Float) -> Unit = { y ->
        val index = ((y.coerceIn(0f, height.toFloat()) / height) * entries.size)
            .toInt().coerceIn(entries.indices)
        scope.launch { listState.scrollToItem(entries[index].itemIndex) }
    }
    Column(
        modifier
            .width(36.dp)
            .fillMaxSize()
            .padding(vertical = 4.dp)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(entries) { detectTapGestures { jumpAt(it.y) } }
            .pointerInput(entries) {
                detectDragGestures(
                    onDragStart = { jumpAt(it.y) },
                    onDrag = { change, _ -> jumpAt(change.position.y) },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        entries.forEach { entry ->
            Text(
                text = entry.label,
                color = FontColor,
                fontSize = if (entry.label.length > 1) 9.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 9.sp,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookSelector(books: List<BookSummary>, selectedBookId: Int?, select: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        books.forEachIndexed { index, book ->
            SegmentedButton(
                selected = selectedBookId == book.id,
                onClick = { select(book.id) },
                shape = SegmentedButtonDefaults.itemShape(index, books.size),
                label = { Text("'${book.year.toString().takeLast(2)}") },
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = BookColor,
                    activeContentColor = NavigationColor,
                    inactiveContainerColor = PaperColor,
                    inactiveContentColor = BookColor,
                    activeBorderColor = BookColor,
                    inactiveBorderColor = BookColor,
                ),
            )
        }
    }
}

@Composable
private fun SingingRow(item: SingingSummary, open: () -> Unit) {
    ListRow(open) {
        Text(item.name, fontWeight = FontWeight.SemiBold)
        Text(item.date, style = MaterialTheme.typography.bodySmall)
        Text(item.location, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SongRow(item: SongSummary, sortMode: SongSortMode, open: () -> Unit) {
    ListRow(open) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val leading = if (sortMode == SongSortMode.PageNumber) item.page else item.lessonCount.toString()
            val title = if (sortMode == SongSortMode.PageNumber) item.title else "${item.title}, ${item.page}"
            Text(leading, modifier = Modifier.size(width = 48.dp, height = 24.dp))
            Text(title, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun LeaderRow(item: LeaderSummary, count: Int?, open: () -> Unit) {
    ListRow(open) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            count?.let { Text(it.toString(), modifier = Modifier.width(48.dp)) }
            Text(item.name, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun LeaderScreen(
    repository: MinutesRepository,
    id: Int,
    back: () -> Unit,
    openSong: (Int, String) -> Unit,
    openAll: (String) -> Unit,
    openMap: (String) -> Unit,
) {
    val detail = load(id) { repository.leader(id) }
    val hasMappedLocation = load(id) { repository.leaderHasMappedLocation(id) }
    val title = detail.value?.summary?.name ?: "Singer"
    DetailScaffold(title, back, actions = {
        IconButton({ openAll(title) }) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, "All lessons", tint = NavigationColor)
        }
        if (hasMappedLocation.value == true) {
            IconButton({ openMap("$title Locations") }) {
                Icon(Icons.Default.Map, "Map", tint = NavigationColor)
            }
        }
    }) {
        detail.value?.let { leaderDetail ->
            LeaderContent(leaderDetail) { songId, songTitle ->
                openSong(songId, songTitle)
            }
        } ?: Loading()
    }
}

@Composable
private fun LeaderLessonsScreen(
    repository: MinutesRepository,
    id: Int,
    name: String,
    back: () -> Unit,
    audioPlayer: AudioPlayerController,
    openSinging: (Int) -> Unit,
) {
    val lessons = load(id) { repository.leaderLessons(id) }
    DetailScaffold(name, back) {
        lessons.value?.let {
            LeaderLessonsContent(it, audioPlayer, openSinging)
        } ?: Loading()
    }
}

@Composable
private fun LeaderLessonsContent(
    lessons: List<LeaderLesson>,
    audioPlayer: AudioPlayerController,
    openSinging: (Int) -> Unit,
) {
    val byYear = lessons.groupBy { it.singing.year }.toSortedMap()
    LazyColumn(Modifier.fillMaxSize()) {
        byYear.forEach { (year, yearLessons) ->
            item(key = "leader-lessons-year-$year") { SingerSectionHeader(year.toString()) }
            itemsIndexed(
                yearLessons,
                key = { _, item -> "${item.singing.id}-${item.lesson.id}-${item.lesson.songId}" },
            ) { index, item ->
                ListRow(
                    open = { openSinging(item.singing.id) },
                    topPadding = if (index == 0) 4.dp else 12.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${item.lesson.page}  ${item.lesson.title}", fontWeight = FontWeight.SemiBold)
                            Text(item.singing.name)
                        }
                        if (item.lesson.audioUrl != null) {
                            IconButton(onClick = {
                                val recordings = lessons.map { SongRecording(it.lesson, it.singing) }
                                val recordingIndex = lessons.indexOf(item)
                                audioPlayer.playRecordings(recordings, recordingIndex)
                            }) { Icon(Icons.Default.PlayArrow, "Play recording") }
                        } else {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderContent(detail: LeaderDetail, openSong: (Int, String) -> Unit) {
    val topSongCount = detail.songs.count { it.isTopTwentyLeader }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (detail.aliases.isNotEmpty()) {
                    Text("also known as: ${detail.aliases.joinToString()}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "led ${detail.songs.size} different ${if (detail.songs.size == 1) "song" else "songs"}, " +
                        "${detail.summary.lessonCount} ${if (detail.summary.lessonCount == 1) "time" else "times"}",
                    textAlign = TextAlign.Center,
                )
                if (topSongCount > 0) {
                    Text(
                        "a top 20 leader of $topSongCount ${if (topSongCount == 1) "song" else "songs"}",
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text("Singings Per Year", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall)
                YearChart(detail.yearlySingings, Modifier.fillMaxWidth().height(392.dp))
                Text(
                    if (topSongCount > 0) "Songs Led By Frequency (⭐ for top 20 leader)" else "Songs Led By Frequency",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        items(detail.songs, key = { it.id }) { song ->
            ListRow({ openSong(song.id, song.title) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(song.title + if (song.isTopTwentyLeader) " ⭐" else "", modifier = Modifier.weight(1f), maxLines = 1)
                    Text(song.lessonCount.toString(), style = MaterialTheme.typography.bodySmall)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun LeaderSongScreen(
    repository: MinutesRepository,
    leaderId: Int,
    leaderName: String,
    songId: Int,
    songTitle: String,
    back: () -> Unit,
    openSong: () -> Unit,
    openSinging: (Int) -> Unit,
) {
    val detail = load(leaderId) { repository.leader(leaderId) }
    val lessons = load(leaderId to songId) { repository.leaderSongLessons(leaderId, songId) }
    val resolvedLeaderName = leaderName.ifBlank { detail.value?.summary?.name.orEmpty() }
    val title = listOf(resolvedLeaderName, songTitle).filter(String::isNotBlank).joinToString(" - ")
    DetailScaffold(title.ifBlank { "Lessons" }, back, actions = {
        TextButton(onClick = openSong) { Text("Song", color = NavigationColor) }
    }) {
        lessons.value?.let { singerLessons ->
            val byYear = singerLessons.groupBy { it.singing.year }.toSortedMap()
            LazyColumn(Modifier.fillMaxSize()) {
                byYear.forEach { (year, yearLessons) ->
                    item(key = "leader-song-year-$year") { SingerSectionHeader(year.toString()) }
                    itemsIndexed(
                        yearLessons,
                        key = { index, item -> "${item.singing.id}-${item.lessonId}-$index" },
                    ) { _, item ->
                        ListRow({ openSinging(item.singing.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.singing.name, modifier = Modifier.weight(1f), maxLines = 1)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        }
                    }
                }
            }
        } ?: Loading()
    }
}

@Composable
private fun ListRow(
    open: () -> Unit,
    topPadding: Dp = 12.dp,
    bottomPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = open)
            .padding(start = 18.dp, top = topPadding, end = 18.dp, bottom = bottomPadding),
        content = content,
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingingScreen(
    repository: MinutesRepository,
    id: Int,
    back: () -> Unit,
    audioPlayer: AudioPlayerController,
    openSong: (Int) -> Unit,
    openText: () -> Unit,
    openMap: (String) -> Unit,
) {
    val detail = load(id) { repository.singing(id) }
    val hasMappedLocation = load(id) { repository.singingHasMappedLocation(id) }
    val title = detail.value?.summary?.name ?: "Singing"
    DetailScaffold(title, back, actions = {
        IconButton(openText) {
            Icon(Icons.AutoMirrored.Filled.Subject, "Text", tint = NavigationColor)
        }
        if (hasMappedLocation.value == true) IconButton({ openMap("$title Location") }) {
            Icon(Icons.Default.Map, "Map", tint = NavigationColor)
        }
    }) {
        detail.value?.let { singing -> SingingContent(singing, audioPlayer, openSong) } ?: Loading()
    }
}

@Composable
private fun SingingContent(detail: SingingDetail, audioPlayer: AudioPlayerController, openSong: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(
                Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    detail.summary.location,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    detail.summary.date,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Text("Lessons", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        itemsIndexed(detail.lessons, key = { _, lesson -> lesson.id }) { index, lesson ->
            ListRow(
                open = { openSong(lesson.songId) },
                topPadding = if (index == 0) 4.dp else 12.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${lesson.page}  ${lesson.title}", fontWeight = FontWeight.SemiBold)
                        Text(lesson.leader)
                    }
                    if (lesson.audioUrl != null) {
                        IconButton(onClick = {
                            val index = detail.lessons.indexOfFirst { it.id == lesson.id }
                            audioPlayer.playLessons(detail.lessons, index, detail.summary)
                        }) { Icon(Icons.Default.PlayArrow, "Play recording") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingingTextScreen(repository: MinutesRepository, id: Int, back: () -> Unit) {
    val detail = load(id) { repository.singing(id) }
    val title = detail.value?.summary?.name ?: "Singing"
    DetailScaffold(title, back) {
        detail.value?.let { singing ->
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(
                            buildString {
                                appendLine(singing.summary.location)
                                append(singing.summary.date)
                                if (singing.leaderCount > 0 && singing.songCount > 0) {
                                    append("\n${singing.leaderCount} leaders, ${singing.songCount} songs")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(formattedMinutesText(singing.minutes), fontSize = 14.sp)
                    }
                }
            }
        } ?: Loading()
    }
}

private val pageReference = Regex("[\\[{](\\d{2,3}[tbTB]?)-(?:1991|2025)[\\]}]")

internal fun formattedMinutesText(minutes: String): AnnotatedString {
    val text = minutes.replace("\u000B", "\n\n")
    return buildAnnotatedString {
        var position = 0
        pageReference.findAll(text).forEach { match ->
            append(text.substring(position, match.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
            position = match.range.last + 1
        }
        append(text.substring(position))
    }
}

@Composable
private fun FloatingAudioPlayer(player: AudioPlayerController, modifier: Modifier = Modifier) {
    val track = player.currentTrack ?: return
    var draggedPosition by remember(track) { mutableStateOf<Int?>(null) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PaperColor,
        contentColor = FontColor,
        shadowElevation = 10.dp,
        tonalElevation = 3.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${track.lesson.title}, ${track.lesson.page}", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        "${track.lesson.leader} — ${track.singingName}, ${track.singingDate}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                if (player.isLoading) {
                    CircularProgressIndicator(Modifier.padding(10.dp).size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(player::toggle) {
                        Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (player.isPlaying) "Pause" else "Play")
                    }
                }
                if (player.hasNext) {
                    IconButton(player::next) { Icon(Icons.Default.SkipNext, "Next recording") }
                }
                IconButton(player::close) { Icon(Icons.Default.Close, "Close player") }
            }

            if (player.durationMillis > 0) {
                val displayedPosition = draggedPosition ?: player.positionMillis
                Slider(
                    value = displayedPosition.toFloat(),
                    onValueChange = { draggedPosition = it.toInt() },
                    onValueChangeFinished = {
                        draggedPosition?.let(player::seekTo)
                        draggedPosition = null
                    },
                    valueRange = 0f..player.durationMillis.toFloat(),
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                )
                Row(Modifier.fillMaxWidth().padding(end = 10.dp)) {
                    Text(formatPlaybackTime(displayedPosition), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text(formatPlaybackTime(player.durationMillis), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun formatPlaybackTime(milliseconds: Int): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongScreen(
    repository: MinutesRepository,
    id: Int,
    back: () -> Unit,
    audioPlayer: AudioPlayerController,
    openSinging: (Int) -> Unit,
    openLeader: (Int) -> Unit,
    openSong: (Int) -> Unit,
    openMap: (String) -> Unit,
) {
    val detail = load(id) { repository.song(id) }
    val hasMappedLocation = load(id) { repository.songHasMappedLocation(id) }
    val title = detail.value?.let { "${it.summary.title}, ${it.summary.page}" } ?: "Song"
    DetailScaffold(title, back, actions = {
        if (hasMappedLocation.value == true) IconButton({ openMap("$title Map") }) {
            Icon(Icons.Default.Map, "Map", tint = NavigationColor)
        }
    }) {
        detail.value?.let { SongContent(it, audioPlayer, openSinging, openLeader, openSong) } ?: Loading()
    }
}

@Composable
private fun SongContent(
    detail: SongDetail,
    audioPlayer: AudioPlayerController,
    openSinging: (Int) -> Unit,
    openLeader: (Int) -> Unit,
    openSong: (Int) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                SongMetadata(detail)
                Spacer(Modifier.height(18.dp))
                if (detail.text.isNotBlank()) {
                    Text("Words", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        detail.text.replace("[chorus]", "Chorus:"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(18.dp))
                }
                Text("Lessons Per Year", style = MaterialTheme.typography.titleSmall)
                YearChart(detail.yearlyUse, Modifier.fillMaxWidth().height(392.dp))
            }
        }
        item { SectionHeader("Recordings") }
        if (detail.recordings.isEmpty()) {
            item {
                Text(
                    "No recordings found!",
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                )
                HorizontalDivider()
            }
        } else {
            items(detail.recordings, key = { "recording-${it.singing.id}-${it.lesson.id}" }) { recording ->
                ListRow({ openSinging(recording.singing.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(recording.lesson.leader, fontWeight = FontWeight.SemiBold)
                            Text(recording.singing.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text(recording.singing.date, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            val index = detail.recordings.indexOfFirst {
                                it.singing.id == recording.singing.id && it.lesson.id == recording.lesson.id
                            }
                            audioPlayer.playRecordings(detail.recordings, index)
                        }) { Icon(Icons.Default.PlayArrow, "Play recording") }
                    }
                }
            }
        }
        if (detail.topLeaders.isNotEmpty()) {
            item { SectionHeader("Top 20 Leaders") }
            items(topTwentyLeaders(detail.topLeaders), key = { "leader-${it.id}" }) { leader ->
                ListRow({ openLeader(leader.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(leader.name, Modifier.weight(1f), maxLines = 1)
                        Text(leader.lessonCount.toString(), style = MaterialTheme.typography.bodySmall)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
        if (detail.neighbors.isNotEmpty()) {
            item { SectionHeader("Leaders Also Lead") }
            items(detail.neighbors, key = { "neighbor-${it.id}" }) { neighbor ->
                ListRow({ openSong(neighbor.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(neighbor.page, Modifier.size(width = 48.dp, height = 24.dp))
                        Text(neighbor.title, Modifier.weight(1f), maxLines = 1)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

private fun topTwentyLeaders(leaders: List<SongLeader>): List<SongLeader> {
    var rank = 1
    var lastCount = -1
    return buildList {
        for ((index, leader) in leaders.withIndex()) {
            if (leader.lessonCount != lastCount) rank = index + 1
            lastCount = leader.lessonCount
            if (rank > 20 || index > 30) break
            add(leader)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun SongMetadata(detail: SongDetail) {
    val times = detail.times.split(',').joinToString(",") { it.trim() }
    val firstYear = detail.yearlyUse.minOfOrNull { it.year } ?: 0
    Text(
        text = buildAnnotatedString {
            append("${detail.summary.meter}; ${detail.keys}; $times")
            if (detail.musicAttribution.isNotBlank()) {
                append("\n")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Tune: ") }
                append(detail.musicAttribution)
            }
            if (detail.wordsAttribution.isNotBlank()) {
                append("\n")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Words: ") }
                append(detail.wordsAttribution)
            }
            append("\nLed ${detail.summary.lessonCount} times since $firstYear")
        },
        modifier = Modifier.fillMaxWidth(),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        textAlign = TextAlign.Center,
    )
}

private fun tickInterval(maxCount: Int): Int = when {
    maxCount > 50 -> 25
    maxCount > 10 -> 10
    else -> 5
}

@Composable
private fun YearChart(points: List<YearCount>, modifier: Modifier = Modifier) {
    val barColor = BarColor
    val labelColor = MaterialTheme.colorScheme.onSurface
    if (points.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No yearly data") }
        return
    }
    Canvas(
        modifier.layout { measurable, constraints ->
            val sideInset = 18.dp.roundToPx()
            val expandedWidth = constraints.maxWidth + sideInset * 2
            val placeable = measurable.measure(
                constraints.copy(minWidth = expandedWidth, maxWidth = expandedWidth),
            )
            layout(constraints.maxWidth, placeable.height) {
                placeable.placeRelative(-sideInset, 0)
            }
        },
    ) {
        val plotLeft = 34.dp.toPx()
        val plotTop = 8.dp.toPx()
        val plotRight = size.width - 10.dp.toPx()
        val plotBottom = size.height - 52.dp.toPx()
        val minYear = points.firstOrNull { it.count > 0 }?.year ?: points.minOf { it.year }
        val maxYear = points.maxOf { it.year }
        val countByYear = points.associate { it.year to it.count }
        val yearCount = (maxYear - minYear + 1).coerceAtLeast(1)
        val maxCount = points.maxOf { it.count }.coerceAtLeast(1)
        val yRange = maxCount * 1.1f
        val yearStep = (plotRight - plotLeft) / yearCount
        val barWidth = yearStep * .72f

        for (year in minYear..maxYear) {
            val count = countByYear[year] ?: 0
            val left = plotLeft + (year - minYear + .5f) * yearStep - barWidth / 2f
            val top = plotBottom - (count / yRange) * (plotBottom - plotTop)
            drawRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, plotBottom - top),
            )
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = 12.sp.toPx()
            textAlign = Paint.Align.RIGHT
        }
        val interval = tickInterval(maxCount)
        var tick = 0
        while (tick <= maxCount) {
            val y = plotBottom - (tick / yRange) * (plotBottom - plotTop)
            drawContext.canvas.nativeCanvas.drawText(tick.toString(), plotLeft - 4.dp.toPx(), y + paint.textSize / 3f, paint)
            tick += interval
        }

        paint.textAlign = Paint.Align.LEFT
        for (year in minYear..maxYear step 5) {
            val x = plotLeft + (year - minYear + .5f) * yearStep
            val y = plotBottom + 18.dp.toPx()
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(45f, x, y)
            drawContext.canvas.nativeCanvas.drawText(year.toString(), x, y, paint)
            drawContext.canvas.nativeCanvas.restore()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailScaffold(
    title: String,
    back: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title, maxLines = 1) },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BookColor,
                navigationIconContentColor = NavigationColor,
                titleContentColor = NavigationColor,
            ),
        )
    }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
}

@Composable
private fun HelpScreen(back: () -> Unit) {
    DetailScaffold("Help / Credits", back) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { HelpHeading("HELP") }
            item {
                Text(
                    "“FaSoLa Minutes” lets you browse and search the Minutes of Sacred Harp Singings. " +
                        "Tap one of the shape notes labeled “Singers,” “Songs,” or “Singings” at the bottom " +
                        "of the screen to browse within that category. Use the search field to search the " +
                        "category you are viewing. Tap the arrow in the top left to return to the previous page."
                )
            }
            item { HelpSection("Singers") }
            item {
                Text(
                    "Tap “Singers” (fa) to see singers who have led songs since 1995, sorted alphabetically " +
                        "by last name. Use the button in the top right to sort by total lesson count or top 20 " +
                        "leader count. Search for any part of a singer’s first or last name.\n\n" +
                        "Tap a singer to see the number of songs and lessons they have led, a graph of their " +
                        "activity by year, and their songs in order of frequency. Tap a song to see the times " +
                        "they led it. The list button shows all their lessons chronologically."
                )
            }
            item { HelpSection("Songs") }
            item {
                Text(
                    "Tap “Songs” (sol) to see songs in page-number order. Select a Sacred Harp edition above " +
                        "the search field. Use the sort button to order songs by frequency, and the filter button " +
                        "to narrow the list. Search by song title or a word in the song text.\n\n" +
                        "Tap a song to read its hymn text, see a graph of its use since 1995, view its top " +
                        "leaders, and play available recordings. Tap a singer to see the times they led the song."
                )
            }
            item { HelpSection("Singings") }
            item {
                Text(
                    "Tap “Singings” (la) to browse singings chronologically with their dates and locations. " +
                        "Search for any part of a singing’s name or location.\n\n" +
                        "Tap a singing to see its leaders and songs in order. Available recordings can be played " +
                        "from the song rows. Tap a song or leader to open its page, and tap the text button to " +
                        "read the complete minutes."
                )
            }
            item { HelpHeading("CREDITS") }
            item {
                Text(
                    "Mark Godfrey – developer\n" +
                        "Lauren Bock – designer\n" +
                        "Jesse P. Karlsberg – researcher and consultant\n\n" +
                        "Special thanks to Sacred Harp Musical Heritage Association, Judy Caudle, and Chris " +
                        "Thorman for their work making the Minutes of Sacred Harp Singings available. Thanks " +
                        "to Nathan Rees for beta testing. And thanks to you for using the app. Happy searching!\n\n" +
                        "The Sacred Harp Musical Heritage Association is a 501(c)(3) non-profit organization " +
                        "whose purpose is the preservation and perpetuation of Sacred Harp singing and its " +
                        "traditions. The developers of this app in no way personally profit from the sale of the app."
                )
            }
        }
    }
}

@Composable
private fun HelpHeading(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    )
}

@Composable
private fun HelpSection(text: String) {
    Text(text, fontWeight = FontWeight.Bold)
}

@Composable
internal fun Loading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(Modifier.size(42.dp))
}

@Composable
internal fun <K, T> load(
    key: K,
    initialValue: T? = null,
    producer: suspend () -> T,
): androidx.compose.runtime.State<T?> {
    val state = remember(key) { mutableStateOf(initialValue) }
    LaunchedEffect(key) { state.value = withContext(Dispatchers.IO) { producer() } }
    return state
}
