package com.jametulhoda.app.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jametulhoda.app.data.ApiException
import com.jametulhoda.app.data.AppRepository
import com.jametulhoda.app.util.PersianDate
import kotlinx.coroutines.launch

/**
 * حساب کاربری: ورود، ثبت‌نام، پروفایل، تغییر رمز، خروج امن —
 * توکن در AndroidKeyStore رمزنگاری می‌شود و فقط سرور آن را می‌پذیرد.
 */
@Composable
fun AccountScreen(
    repo: AppRepository,
    snackbar: SnackbarHostState,
) {
    if (repo.session == null) {
        AuthForms(repo = repo, snackbar = snackbar, onLoggedIn = { /* recompose via state */ })
    } else {
        ProfilePanel(repo = repo, snackbar = snackbar, onLoggedOut = { /* recompose via state */ })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthForms(
    repo: AppRepository,
    snackbar: SnackbarHostState,
    onLoggedIn: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("ایران") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(false) }

    if (loggedIn) {
        ProfilePanel(repo = repo, snackbar = snackbar, onLoggedOut = { loggedIn = false })
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("ورود") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("ثبت‌نام") })
        }

        if (tab == 1) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("نام کامل") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("کشور") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("تلفن") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(if (tab == 0) "ایمیل / نام کاربری / تلفن" else "ایمیل") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("رمز عبور") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (tab == 1) {
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("تکرار رمز عبور") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showPassword, onCheckedChange = { showPassword = it })
            Spacer(Modifier.width(4.dp))
            Text("نمایش رمز عبور", style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = {
                if (busy) return@Button
                if (tab == 1 && password != confirm) {
                    scope.launch { snackbar.showSnackbar("رمز و تکرار آن یکسان نیستند.") }
                    return@Button
                }
                busy = true
                scope.launch {
                    try {
                        if (tab == 0) {
                            repo.login(email.trim(), password)
                            snackbar.showSnackbar("با موفقیت وارد شدید.")
                        } else {
                            repo.register(name.trim(), country.trim(), phone.trim(), email.trim(), password, confirm)
                            snackbar.showSnackbar("حساب شما ساخته شد.")
                        }
                        loggedIn = true
                        onLoggedIn()
                    } catch (e: ApiException) {
                        snackbar.showSnackbar(e.message?.takeIf { it.isNotBlank() } ?: "درخواست نامعتبر بود.")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("اتصال به سرور برقرار نشد؛ بعداً تلاش کنید.")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && email.isNotBlank() && password.length >= 6 &&
                (tab == 0 || (name.isNotBlank() && phone.isNotBlank() && confirm.length >= 6)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (tab == 0) "ورود به حساب" else "ساخت حساب کاربری")
        }

        Text(
            "رمز عبور فقط روی سرور به‌صورت امن نگهداری می‌شود و در برنامه ذخیره نمی‌گردد.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfilePanel(
    repo: AppRepository,
    snackbar: SnackbarHostState,
    onLoggedOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val session = repo.session
    if (session == null) {
        AuthForms(repo = repo, snackbar = snackbar, onLoggedIn = {})
        return
    }
    var newName by remember { mutableStateOf(session.user.fullName) }
    var newEmail by remember { mutableStateOf(session.user.email) }
    var newPhone by remember { mutableStateOf(session.user.phone) }
    var newPassword by remember { mutableStateOf("") }
    var oldPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loggedOut by remember { mutableStateOf(false) }

    if (loggedOut) {
        AuthForms(repo = repo, snackbar = snackbar, onLoggedIn = { loggedOut = false })
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(session.user.fullName, style = MaterialTheme.typography.titleLarge)
                Text(
                    session.user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider()
        Text("ویرایش پروفایل", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(newName, { newName = it }, label = { Text("نام کامل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(newEmail, { newEmail = it }, label = { Text("ایمیل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(newPhone, { newPhone = it }, label = { Text("تلفن") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                busy = true
                scope.launch {
                    try {
                        repo.updateProfile(newName.trim(), newEmail.trim(), newPhone.trim())
                        snackbar.showSnackbar("پروفایل به‌روزرسانی شد.")
                    } catch (e: Exception) {
                        snackbar.showSnackbar(e.message ?: "به‌روزرسانی ناموفق بود.")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && newName.isNotBlank(),
        ) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("ذخیرهٔ تغییرات")
        }

        HorizontalDivider()
        Text("تغییر رمز عبور", style = MaterialTheme.typography.titleMedium)
        Text(
            "پس از تغییر رمز، همهٔ نشست‌ها (وب و اپ) باطل می‌شوند و باید دوباره وارد شوید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            oldPassword,
            { oldPassword = it },
            label = { Text("رمز عبور فعلی") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            newPassword,
            { newPassword = it },
            label = { Text("رمز عبور جدید (حداقل ۸ نویسه)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                busy = true
                scope.launch {
                    try {
                        repo.changePassword(oldPassword, newPassword, newPassword)
                        snackbar.showSnackbar("رمز عبور تغییر کرد؛ لطفاً دوباره وارد شوید.")
                        loggedOut = true
                        onLoggedOut()
                    } catch (e: Exception) {
                        snackbar.showSnackbar(e.message ?: "تغییر رمز ناموفق بود.")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && oldPassword.length >= 6 && newPassword.length >= 8,
        ) { Text("تغییر رمز") }

        HorizontalDivider()
        OutlinedButton(
            onClick = {
                busy = true
                scope.launch {
                    repo.logout()
                    snackbar.showSnackbar("از حساب خارج شدید.")
                    loggedOut = true
                    onLoggedOut()
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text("خروج امن از حساب", color = MaterialTheme.colorScheme.error)
        }

        if (session.user.createdAt.isNotBlank()) {
            Text(
                "تاریخ عضویت: ${PersianDate.format(session.user.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
