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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.OpenInNew
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
import com.example.snsassistant.data.db.PostWithReplies
import com.example.snsassistant.util.ServiceLocator
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
                    IconButton(onClick = {
                        if (currentFilter == FeedViewModel.Filter.Incomplete) vm.setFilter(FeedViewModel.Filter.All)
                        else vm.setFilter(FeedViewModel.Filter.Incomplete)
                    }) {
                        if (currentFilter == FeedViewModel.Filter.Incomplete) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Show all")
                        } else {
                            Icon(Icons.Default.FilterList, contentDescription = "Show incomplete")
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
            FilterRow(currentFilter = currentFilter, onSelect = { vm.setFilter(it) })
            LazyColumn(Modifier.fillMaxSize()) {
                items(feed, key = { it.post.id }) { item ->
                    val isGenerating = generating.contains(item.post.id) || (item.replies.isEmpty() && item.post.lastError == null)
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
                                onOpen = {
                                    val link = item.post.link
                                    if (link.isNullOrBlank()) {
                                        scope.launch { snack.showSnackbar("No link available") }
                                    } else {
                                        openLinkPreferNative(context, item.post.platform, link)
                                    }
                                },
                                isGenerating = isGenerating
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(currentFilter: FeedViewModel.Filter, onSelect: (FeedViewModel.Filter) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Filter:")
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = currentFilter == FeedViewModel.Filter.Incomplete,
            onClick = { onSelect(FeedViewModel.Filter.Incomplete) },
            label = { Text("Incomplete") }
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = currentFilter == FeedViewModel.Filter.All,
            onClick = { onSelect(FeedViewModel.Filter.All) },
            label = { Text("All") }
        )
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
    onOpen: () -> Unit,
    isGenerating: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val ts = sdf.format(Date(item.post.timestamp))
    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = platformEmoji(item.post.platform), fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(text = item.post.platform, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (!item.post.link.isNullOrBlank()) {
                    IconButton(onClick = onOpen) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open link")
                    }
                }
                Text(text = ts, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(text = item.post.text, maxLines = if (expanded) Int.MAX_VALUE else 3)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (expanded) "Show less" else "Show replies",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = !expanded }
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
                        TextButton(onClick = onRegenerate, enabled = !isGenerating) { Text("Regenerate") }
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
                        TextButton(onClick = onRegenerate, enabled = !isGenerating) { Text("Regenerate") }
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
    val pkg = when (platform) {
        "LinkedIn" -> "com.linkedin.android"
        "X" -> "com.twitter.android"
        "Instagram" -> "com.instagram.android"
        else -> null
    }
    if (pkg != null) {
        val appIntent = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(appIntent); true }.getOrDefault(false)) return
    }
    val webIntent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(webIntent) }
}
