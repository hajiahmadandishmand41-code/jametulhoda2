package com.jametulhoda.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jametulhoda.app.BuildConfig
import com.jametulhoda.app.data.AboutPage
import com.jametulhoda.app.data.AppRepository
import kotlinx.coroutines.launch

/** درباره ما — معرفی جامعه‌الهدی از `/api/mobile/pages?page=about`. */
@Composable
fun AboutScreen(repo: AppRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var about by remember { mutableStateOf<AboutPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        try {
            about = repo.about()
            loading = false
        } catch (e: Exception) {
            error = e.message ?: "صفحه دریافت نشد."
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    when {
        loading -> com.jametulhoda.app.ui.LoadingBox()
        error != null -> com.jametulhoda.app.ui.ErrorBox(message = error ?: "", onRetry = { scope.launch { load() } })
        else -> {
            val page = about
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(page?.title?.ifBlank { "درباره جامعه‌الهدی" } ?: "درباره جامعه‌الهدی", style = MaterialTheme.typography.headlineSmall)
                if (page != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (page.name.isNotBlank()) {
                                Text(page.name, style = MaterialTheme.typography.titleLarge)
                            }
                            if (page.slogan.isNotBlank()) {
                                Text(page.slogan, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(page.description, style = MaterialTheme.typography.bodyLarge)
                            if (page.founder.isNotBlank()) {
                                Text("بنیان‌گذار: ${page.founder}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (page.contact.email.isNotBlank() || page.contact.phone.isNotBlank()) {
                        HorizontalDivider()
                        Text("اطلاعات تماس", style = MaterialTheme.typography.titleMedium)
                        if (page.contact.email.isNotBlank()) {
                            Text("ایمیل: ${page.contact.email}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (page.contact.phone.isNotBlank()) {
                            Text("تلفن: ${page.contact.phone}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (page.contact.address.isNotBlank()) {
                            Text("نشانی: ${page.contact.address}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("نشانی وب‌سایت:", style = MaterialTheme.typography.titleSmall)
                }
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.API_BASE_URL))
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(BuildConfig.API_BASE_URL)
                }
                Text(
                    "منبع همهٔ محتواها پایگاه‌دادهٔ رسمی سایت است؛ برنامه هیچ داده‌ای را حذف یا بازنشانی نمی‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** تماس با ما — فرم پیام با محدودیت نرخ سرور و اعتبارسنجی کامل. */
@Composable
fun ContactScreen(
    repo: AppRepository,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val session = repo.session
    var name by remember { mutableStateOf(session?.user?.fullName ?: "") }
    var email by remember { mutableStateOf(session?.user?.email ?: "") }
    var phone by remember { mutableStateOf(session?.user?.phone ?: "") }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("تماس با ما", style = MaterialTheme.typography.headlineSmall)
        Text(
            "پیام شما پس از اعتبارسنجی در سرور ثبت می‌شود. برای هر نشانی اینترنتی سهمیهٔ محدودی در نظر گرفته شده است.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(name, { name = it }, label = { Text("نام") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            email,
            { email = it },
            label = { Text("ایمیل") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            phone,
            { phone = it },
            label = { Text("تلفن (اختیاری)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(subject, { subject = it }, label = { Text("موضوع") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            message,
            { message = it },
            label = { Text("متن پیام") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                busy = true
                scope.launch {
                    try {
                        repo.sendContact(name.trim(), email.trim(), phone.trim(), subject.trim(), message.trim())
                        sent = true
                        snackbar.showSnackbar("پیام شما ثبت شد؛ با تشکر.")
                        message = ""
                        subject = ""
                    } catch (e: Exception) {
                        snackbar.showSnackbar(e.message ?: "ارسال پیام ناموفق بود.")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && name.isNotBlank() && email.isNotBlank() && message.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("ارسال پیام")
        }
        if (sent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("پیام شما با موفقیت ارسال شد.", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** تنظیمات: تم روشن/تاریک/سیستم + دربارهٔ برنامه. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    darkMode: Boolean?,
    onDarkMode: (Boolean?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("تم برنامه", style = MaterialTheme.typography.titleLarge)
        Text(
            "پیش‌فرض حالت روشن (همان هویت بصری سایت) است؛ حالت تاریک اختیاری است.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val index = when (darkMode) {
            false -> 0
            true -> 1
            null -> 2
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("روشن", "تاریک", "سیستم").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = index == i,
                    onClick = { onDarkMode(listOf(false, true, null)[i]) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                ) { Text(label) }
            }
        }
        HorizontalDivider()
        Text("دربارهٔ برنامه", style = MaterialTheme.typography.titleLarge)
        Text("نسخهٔ ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
        Text(
            "این برنامه متن‌باز است و همهٔ درخواست‌های آن از مسیرهای اعتبارسنجی‌شدهٔ سرور می‌گذرد؛ " +
                "هیچ رمز، کلید یا دسترسی مدیریتی داخل برنامه قرار ندارد.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
