package com.jametulhoda.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jametulhoda.app.data.AppRepository
import com.jametulhoda.app.data.ContentItem
import com.jametulhoda.app.ui.ContentCard
import com.jametulhoda.app.ui.EmptyBox
import com.jametulhoda.app.ui.ErrorBox
import com.jametulhoda.app.ui.LoadingBox
import kotlinx.coroutines.launch

/** جستجوی پیشرفته در همهٔ بخش‌ها (همان `searchAll` سایت) با فیلتر نوع. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repo: AppRepository,
    onItem: (ContentItem) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        submitted = q
        searched = true
        loading = true
        error = null
        scope.launch {
            try {
                val result = repo.search(q, type, 1)
                hits = result.items
                hasMore = result.hasMore
                loading = false
            } catch (e: Exception) {
                error = e.message ?: "خطا در جستجو."
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("جستجو در جامعه‌الهدی…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type.isEmpty(), onClick = { type = "" }, label = { Text("همه") })
            FilterChip(selected = type == "post", onClick = { type = "post" }, label = { Text("مطالب") })
            FilterChip(selected = type == "book", onClick = { type = "book" }, label = { Text("کتاب") })
            FilterChip(selected = type == "lesson", onClick = { type = "lesson" }, label = { Text("درس") })
            FilterChip(selected = type == "media", onClick = { type = "media" }, label = { Text("رسانه") })
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = ::search, enabled = query.isNotBlank()) {
            Text("جستجو")
        }
        Spacer(Modifier.height(12.dp))

        when {
            loading -> LoadingBox()
            error != null -> ErrorBox(message = error ?: "", onRetry = ::search)
            searched && hits.isEmpty() -> EmptyBox("نتیجه‌ای برای «$submitted» یافت نشد.")
            hits.isNotEmpty() -> LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(hits, key = { it.key }) { hit ->
                    ContentCard(item = hit, onClick = { onItem(hit) })
                }
            }
        }
    }
}
