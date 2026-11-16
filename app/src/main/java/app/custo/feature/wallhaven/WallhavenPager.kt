package app.custo.feature.wallhaven

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a paged wallhaven grid shows. */
data class PagerState(
    val query: String? = null,
    val filters: WhFilters? = null,
    val items: List<WhWallpaper> = emptyList(),
    val page: Int = 0,
    val seed: String? = null,
    val loading: Boolean = false,
    val error: WhError? = null,
    val endReached: Boolean = false,
) {
    val canLoadMore: Boolean get() = !loading && !endReached && error == null && filters != null

    /** Adds the next page; ids already shown are dropped (random/toplist pages can overlap). */
    fun plus(p: WhPage): PagerState {
        val seen = items.mapTo(HashSet()) { it.id }
        return copy(
            items = items + p.items.filter { seen.add(it.id) },
            page = p.page,
            seed = p.seed ?: seed,
            loading = false,
            error = null,
            endReached = !p.hasMore,
        )
    }
}

/**
 * Infinite paging over [WallhavenRepository.search]: call [reset] with a search, then [loadMore]
 * when the grid nears its end. One request at a time; errors stop paging until [retry].
 */
class WallhavenPager(private val repo: WallhavenRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(PagerState())
    val state: StateFlow<PagerState> = _state
    private var job: Job? = null

    /** Grid position to restore when the browser comes back. */
    var firstVisible = 0
    var firstVisibleOffset = 0

    /** The browser's search box, chip and portrait toggle, kept while a detail screen is open. */
    var searchText = ""
    var chip: BrowseChip? = BrowseChip.TOP
    var portrait = true

    /** Starts a new search unless it's the one already shown. */
    fun reset(query: String?, filters: WhFilters, force: Boolean = false) {
        val q = query?.trim()?.takeIf { it.isNotEmpty() }
        val cur = _state.value
        if (!force && cur.query == q && cur.filters == filters && (cur.items.isNotEmpty() || cur.loading)) return
        job?.cancel()
        firstVisible = 0
        firstVisibleOffset = 0
        _state.value = PagerState(query = q, filters = filters)
        load()
    }

    fun loadMore() {
        if (_state.value.canLoadMore) load()
    }

    fun retry() {
        val s = _state.value
        if (s.filters == null || s.loading) return
        _state.update { it.copy(error = null) }
        load()
    }

    private fun load() {
        val s = _state.value
        val filters = s.filters ?: return
        _state.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            try {
                val p = repo.search(s.query, filters, s.page + 1, s.seed)
                _state.update { cur -> if (cur.query == s.query && cur.filters == filters) cur.plus(p) else cur }
            } catch (e: CancellationException) {
                throw e
            } catch (e: WallhavenException) {
                _state.update { it.copy(loading = false, error = e.error) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = WhError.SERVER) }
            }
        }
    }
}
