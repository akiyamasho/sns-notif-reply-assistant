package com.example.snsassistant.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import com.example.snsassistant.data.db.PostWithReplies
import com.example.snsassistant.util.ServiceLocator
import com.example.snsassistant.util.PendingIntentStore
import com.example.snsassistant.ui.viewmodel.FeedViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun FeedScreen(onOpenSettings: () -> Unit) {
    val repo = ServiceLocator.repository
    val vm: FeedViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = FeedViewModel(repo) as T
    })

    val feed by vm.feed.collectAsState()
    val generating by vm.generating.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "SNS Reply Assistant") },
                actions = {
                    val currentFilter by vm.currentFilter.collectAsState()
                    val selected by vm.selected.collectAsState()
                    if (currentFilter == FeedViewModel.Filter.Done && selected.isNotEmpty()) {
                        IconButton(onClick = { vm.deleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                    IconButton(onClick = {
                        if (currentFilter == FeedViewModel.Filter.Todo) vm.setFilter(FeedViewModel.Filter.Done)
                        else vm.setFilter(FeedViewModel.Filter.Todo)
                    }) {
                        if (currentFilter == FeedViewModel.Filter.Todo) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Show done")
                        } else {
                            Icon(Icons.Default.FilterList, contentDescription = "Show to-do")
                        }
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = null) }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!isNotificationAccessEnabled(context)) {
                NotificationAccessBanner(onOpen = { openNotificationAccess(context) })
            }
            val currentFilter by vm.currentFilter.collectAsState()
            val selectedIds by vm.selected.collectAsState()
            val inDone = currentFilter == FeedViewModel.Filter.Done
            val visibleDoneIds: Set<Long> = if (inDone) feed.map { it.post.id }.toSet() else emptySet()
            val allSelected = inDone && visibleDoneIds.isNotEmpty() && visibleDoneIds.all { selectedIds.contains(it) }
            FilterRow(
                currentFilter = currentFilter,
                onSelect = { vm.setFilter(it) },
                showSelectAll = inDone,
                allSelected = allSelected,
                onToggleSelectAll = {
                    if (allSelected) vm.clearSelection() else vm.selectAll(visibleDoneIds)
                }
            )
            if (feed.isEmpty()) {
                val label = if (currentFilter == FeedViewModel.Filter.Done) "No done notifications" else "No to-do notifications"
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(label)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                items(feed, key = { it.post.id }) { item ->
                    val isGenerating = generating.contains(item.post.id)
                    val dismissState = rememberDismissState(
                        confirmStateChange = { value ->
                            if (value == DismissValue.DismissedToEnd || value == DismissValue.DismissedToStart) {
                                vm.markDone(item.post.id)
                                scope.launch {
                                    val res = snack.showSnackbar(
                                        message = "Marked done",
                                        actionLabel = "Undo",
                                        withDismissAction = false
                                    )
                                    if (res == SnackbarResult.ActionPerformed) {
                                        vm.markUndone(item.post.id)
                                    }
                                }
                            }
                            // Prevent built-in removal; list updates via Flow
                            false
                        }
                    )

                    SwipeToDismiss(
                        state = dismissState,
                        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
                        background = {
                            val target = if (dismissState.targetValue == DismissValue.Default) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
                            val bg by animateColorAsState(target, label = "dismissBg")
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(bg)
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Done, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.width(8.dp))
                                Text("Done", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        },
                        dismissContent = {
                            PostCard(
                                item,
                                onCopy = { reply -> clipboard.setText(AnnotatedString(reply)) },
                                onRegenerate = { vm.retryFor(item.post.id) },
                                onDeleteReplies = { vm.deleteReplies(item.post.id) },
                                onOpen = {
                                    val link = item.post.link
                                    Log.i(
                                        "FeedScreen",
                                        "OpenInApp clicked: id=${item.post.id} platform=${item.post.platform} hasLink=${!link.isNullOrBlank()} link=${link ?: ""}"
                                    )
                                    if (!link.isNullOrBlank()) {
                                        openLinkPreferNative(context, item.post.platform, link)
                                    } else {
                                        // First, try to send the original notification PendingIntent (deepest link)
                                        val piSent = PendingIntentStore.sendFor(item.post.id)
                                        val opened = piSent || openPlatformApp(context, item.post.platform)
                                        if (!opened) {
                                            scope.launch { snack.showSnackbar("Could not open app") }
                                        }
                                    }
                                },
                                isGenerating = isGenerating,
                                showCheckbox = currentFilter == FeedViewModel.Filter.Done,
                                checked = if (currentFilter == FeedViewModel.Filter.Done) selectedIds.contains(item.post.id) else false,
                                onCheckedChange = { vm.toggleSelected(item.post.id) }
                            )
                        }
                    )
                }
            }
        }
    }
}
}

@Composable
private fun FilterRow(
    currentFilter: FeedViewModel.Filter,
    onSelect: (FeedViewModel.Filter) -> Unit,
    showSelectAll: Boolean,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Filter:")
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = currentFilter == FeedViewModel.Filter.Todo,
            onClick = { onSelect(FeedViewModel.Filter.Todo) },
            label = { Text("To-do") }
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = currentFilter == FeedViewModel.Filter.Done,
            onClick = { onSelect(FeedViewModel.Filter.Done) },
            label = { Text("Done") }
        )
        Spacer(Modifier.weight(1f))
        if (showSelectAll) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allSelected, onCheckedChange = { onToggleSelectAll() })
                Text("✅")
            }
        }
    }
}

@Composable
private fun NotificationAccessBanner(onOpen: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Grant notification access to start capturing posts.", Modifier.weight(1f))
            TextButton(onClick = onOpen) { Text("Open") }
        }
    }
}

@Composable
private fun PostCard(
    item: PostWithReplies,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onDeleteReplies: () -> Unit,
    onOpen: () -> Unit,
    isGenerating: Boolean,
    showCheckbox: Boolean,
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val ts = sdf.format(Date(item.post.timestamp))
    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showCheckbox) {
                    Checkbox(checked = checked, onCheckedChange = { onCheckedChange() })
                    Spacer(Modifier.width(4.dp))
                }
                Text(text = platformEmoji(item.post.platform), fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(text = item.post.platform, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (!item.post.link.isNullOrBlank()) {
                    IconButton(onClick = onOpen) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open link")
                    }
                }
                Text(text = ts, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(text = item.post.text, maxLines = if (expanded) Int.MAX_VALUE else 3)
            Spacer(Modifier.height(8.dp))
            // Link to open the original post or just the app
            TextButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Open in app")
            }
            Spacer(Modifier.height(4.dp))
            val hasReplies = item.replies.isNotEmpty()
            val collapsedLabel = if (hasReplies) "Show replies" else "Generate Replies"
            val collapsedColor = if (hasReplies) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            Text(
                text = if (expanded) "Show less" else collapsedLabel,
                color = collapsedColor,
                modifier = Modifier.clickable {
                    if (expanded) {
                        expanded = false
                    } else {
                        if (hasReplies) {
                            expanded = true
                        } else {
                            if (!isGenerating) onRegenerate()
                            expanded = true
                        }
                    }
                }
            )
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (isGenerating) {
                    // Show a small progress indicator while generating
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                if (item.replies.isEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (isGenerating) "Generating…" else "No replies yet or generation failed.")
                            val err = item.post.lastError
                            if (!err.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Error: $err",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        val actionLabel = if (item.replies.isEmpty()) "Generate" else "Regenerate"
                        TextButton(onClick = onRegenerate, enabled = !isGenerating) { Text(actionLabel) }
                    }
                } else {
                    // Show existing replies
                    item.replies.forEach { reply ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "• ${reply.replyText}", Modifier.weight(1f))
                            IconButton(onClick = { onCopy(reply.replyText) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        if (item.post.isDone && item.replies.isNotEmpty()) {
                            TextButton(onClick = onDeleteReplies, enabled = !isGenerating) { Text("Delete replies") }
                            Spacer(Modifier.width(8.dp))
                        }
                        val actionLabel = if (item.replies.isEmpty()) "Generate" else "Regenerate"
                        TextButton(onClick = onRegenerate, enabled = !isGenerating) { Text(actionLabel) }
                    }
                }
            }
        }
    }
}

private fun platformEmoji(platform: String): String = when (platform) {
    "LinkedIn" -> "🔗"
    "X" -> "✖️"
    "Instagram" -> "📸"
    else -> "💬"
}

private fun isNotificationAccessEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val pkgName = context.packageName
    return enabled?.contains(pkgName) == true
}

private fun openNotificationAccess(context: Context) {
    context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openLinkPreferNative(context: Context, platform: String, link: String) {
    val uri = Uri.parse(link)
    val pkg = platformPackage(platform)
    if (pkg != null) {
        val appIntent = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Log.i("FeedScreen", "OpenInApp intent (native): action=${appIntent.action} data=$uri pkg=$pkg flags=${appIntent.flags}")
        val started = runCatching { context.startActivity(appIntent); true }.getOrDefault(false)
        if (started) {
            Log.i("FeedScreen", "OpenInApp: started native app for platform=$platform")
            return
        } else {
            Log.w("FeedScreen", "OpenInApp: native app launch failed; falling back to web for platform=$platform")
        }
    }
    val webIntent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    Log.i("FeedScreen", "OpenInApp intent (web): action=${webIntent.action} data=$uri flags=${webIntent.flags}")
    runCatching { context.startActivity(webIntent) }
}

private fun openPlatformApp(context: Context, platform: String): Boolean {
    val pkg = platformPackage(platform) ?: return false
    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ?: run {
            Log.w("FeedScreen", "OpenInApp: no launch intent for package=$pkg (platform=$platform)")
            return false
        }
    Log.i("FeedScreen", "OpenInApp intent (launch app): component=${launch.component} pkg=$pkg flags=${launch.flags}")
    return runCatching { context.startActivity(launch); true }
        .onFailure { Log.e("FeedScreen", "OpenInApp: failed to launch package=$pkg: ${it.message}") }
        .getOrDefault(false)
}

private fun platformPackage(platform: String): String? = when (platform) {
    "LinkedIn" -> "com.linkedin.android"
    "X" -> "com.twitter.android"
    "Instagram" -> "com.instagram.android"
    else -> null
}
