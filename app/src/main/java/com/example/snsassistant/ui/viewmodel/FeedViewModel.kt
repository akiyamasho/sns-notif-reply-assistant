package com.example.snsassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snsassistant.data.db.PostWithReplies
import com.example.snsassistant.data.repo.SnsRepository
import com.example.snsassistant.util.DebugStore
import com.example.snsassistant.util.PendingIntentStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FeedViewModel(private val repo: SnsRepository) : ViewModel() {
    enum class Filter { Todo, Done }

    private val filter = MutableStateFlow(Filter.Todo)

    val currentFilter: StateFlow<Filter> = filter

    val feed: StateFlow<List<PostWithReplies>> = repo.observeFeed()
        .combine(filter) { list, f ->
            when (f) {
                Filter.Todo -> list.filter { !it.post.isDone }
                Filter.Done -> list.filter { it.post.isDone }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retryFor(postId: Long) {
        viewModelScope.launch {
            setGenerating(postId, true)
            try {
                repo.generateRepliesForPost(postId)
            } finally {
                setGenerating(postId, false)
            }
        }
    }

    fun markDone(postId: Long) { viewModelScope.launch { repo.markDone(postId) } }
    fun markUndone(postId: Long) { viewModelScope.launch { repo.markUndone(postId) } }
    fun setFilter(f: Filter) { filter.value = f }

    // UI-only loading state for (re)generation
    private val _generating = MutableStateFlow<Set<Long>>(emptySet())
    val generating: StateFlow<Set<Long>> = _generating

    private fun setGenerating(postId: Long, value: Boolean) {
        val current = _generating.value.toMutableSet()
        if (value) current.add(postId) else current.remove(postId)
        _generating.value = current
    }

    fun deleteReplies(postId: Long) {
        viewModelScope.launch {
            repo.deleteRepliesForPost(postId)
        }
    }

    fun resendToDiscord(postId: Long) {
        viewModelScope.launch { repo.resendToDiscord(postId) }
    }

    fun debugLog(postId: Long) {
        DebugStore.logFor(postId)
        PendingIntentStore.logInfo(postId)
    }

    // Selection state for bulk actions on Done items
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected

    fun toggleSelected(postId: Long) {
        val cur = _selected.value.toMutableSet()
        if (!cur.add(postId)) cur.remove(postId)
        _selected.value = cur
    }

    fun clearSelection() { _selected.value = emptySet() }

    fun deleteSelected() {
        val ids = _selected.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repo.deletePosts(ids)
            clearSelection()
        }
    }

    fun selectAll(ids: Collection<Long>) {
        _selected.value = ids.toSet()
    }
}
