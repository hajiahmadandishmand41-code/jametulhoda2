package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.example.network.MobileApiException
import com.example.network.MobileAuthRepository

private enum class AccountMode { CHECKING, LOGIN, REGISTER, PROFILE, PASSWORD }

@Composable
fun MobileAccountDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { MobileAuthRepository(context) }
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(if (repository.hasToken()) AccountMode.CHECKING else AccountMode.LOGIN) }
    var user by remember { mutableStateOf<JSONObject?>(null) }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("AF") }
    var countryMenu by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordConfirm by remember { mutableStateOf("") }
    var agreedTerms by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun applyUser(value: JSONObject?) {
        user = value
        fullName = value?.optString("full_name").orEmpty()
        email = value?.optString("email").orEmpty()
    }

    LaunchedEffect(repository) {
        if (mode == AccountMode.CHECKING) {
            val result = runCatching { withContext(Dispatchers.IO) { repository.currentUser() } }
            result.onSuccess {
                applyUser(it)
                mode = AccountMode.PROFILE
            }.onFailure {
                repository.clearLocalToken()
                mode = AccountMode.LOGIN
                message = "نشست قبلی معتبر نیست؛ دوباره وارد شوید."
                isError = true
            }
        }
    }

    fun submit(action: suspend () -> JSONObject, onSuccess: (JSONObject) -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            message = null
            isError = false
            try {
                val result = withContext(Dispatchers.IO) { action() }
                onSuccess(result)
            } catch (error: Throwable) {
                message = accountError(error)
                isError = true
            } finally {
                busy = false
            }
        }
    }

    val countryNames = linkedMapOf(
        "AF" to "افغانستان",
        "IR" to "ایران",
        "PK" to "پاکستان",
        "IQ" to "عراق",
        "TR" to "ترکیه",
        "TJ" to "تاجیکستان",
        "OTHER" to "سایر کشورها"
    )
    val title = when (mode) {
        AccountMode.CHECKING -> "بررسی حساب کاربری"
        AccountMode.LOGIN -> "ورود به حساب"
        AccountMode.REGISTER -> "ایجاد حساب کاربری"
        AccountMode.PROFILE -> "حساب کاربری"
        AccountMode.PASSWORD -> "تغییر رمز عبور"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (mode) {
                    AccountMode.CHECKING -> {
                        CircularProgressIndicator()
                        Text("در حال بررسی نشست امن…")
                    }
                    AccountMode.LOGIN -> {
                        OutlinedTextField(
                            value = identifier, onValueChange = { identifier = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("ایمیل، نام کاربری یا شمارهٔ تلفن") }
                        )
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("رمز عبور") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Button(
                            onClick = {
                                submit(
                                    { repository.login(identifier, password) },
                                    { result ->
                                        applyUser(result.optJSONObject("user"))
                                        mode = AccountMode.PROFILE
                                        password = ""
                                        message = "ورود موفق بود."
                                    }
                                )
                            },
                            enabled = !busy && identifier.isNotBlank() && password.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "در حال بررسی…" else "ورود") }
                        TextButton(onClick = { mode = AccountMode.REGISTER; message = null; isError = false }) {
                            Text("حساب ندارید؟ ثبت‌نام کنید")
                        }
                    }
                    AccountMode.REGISTER -> {
                        OutlinedTextField(
                            value = fullName, onValueChange = { fullName = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("نام کامل") }
                        )
                        Box {
                            OutlinedButton(onClick = { countryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("کشور: " + (countryNames[country] ?: country))
                            }
                            DropdownMenu(expanded = countryMenu, onDismissRequest = { countryMenu = false }) {
                                countryNames.forEach { entry ->
                                    DropdownMenuItem(
                                        text = { Text(entry.value) },
                                        onClick = { country = entry.key; countryMenu = false }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = phone, onValueChange = { phone = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("شمارهٔ تلفن") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                        OutlinedTextField(
                            value = email, onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("ایمیل (اختیاری)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("رمز عبور؛ حداقل ۸ نویسه") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        OutlinedTextField(
                            value = passwordConfirm, onValueChange = { passwordConfirm = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("تکرار رمز عبور") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = agreedTerms, onCheckedChange = { agreedTerms = it })
                            Text("قوانین استفاده از حساب کاربری را می‌پذیرم.")
                        }
                        Button(
                            onClick = {
                                submit(
                                    { repository.register(fullName, country, phone, email, password, passwordConfirm) },
                                    { result ->
                                        applyUser(result.optJSONObject("user"))
                                        mode = AccountMode.PROFILE
                                        password = ""
                                        passwordConfirm = ""
                                        message = "حساب کاربری ایجاد شد."
                                    }
                                )
                            },
                            enabled = !busy && agreedTerms && fullName.trim().length >= 2 && phone.isNotBlank() &&
                                password.length >= 8 && password == passwordConfirm,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "در حال ثبت‌نام…" else "ثبت‌نام") }
                        TextButton(onClick = { mode = AccountMode.LOGIN; message = null }) {
                            Text("قبلاً حساب ساخته‌اید؟ ورود")
                        }
                    }
                    AccountMode.PROFILE -> {
                        Text("حساب: " + (user?.optString("full_name").orEmpty().ifBlank { "کاربر" }))
                        OutlinedTextField(
                            value = fullName, onValueChange = { fullName = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("نام کامل") }
                        )
                        OutlinedTextField(
                            value = email, onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("ایمیل") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        Button(
                            onClick = {
                                submit(
                                    { repository.updateProfile(fullName, email) },
                                    { result ->
                                        applyUser(result.optJSONObject("user") ?: user)
                                        message = "پروفایل به‌روزرسانی شد."
                                    }
                                )
                            },
                            enabled = !busy && fullName.trim().length >= 2 &&
                                (email != (user?.optString("email").orEmpty()) ||
                                    fullName != (user?.optString("full_name").orEmpty())),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ذخیرهٔ تغییرات") }
                        TextButton(onClick = {
                            currentPassword = ""
                            newPassword = ""
                            newPasswordConfirm = ""
                            mode = AccountMode.PASSWORD
                            message = null
                        }) { Text("تغییر رمز عبور") }
                        TextButton(
                            onClick = {
                                submit(
                                    { repository.logout() },
                                    {
                                        applyUser(null)
                                        mode = AccountMode.LOGIN
                                        password = ""
                                        message = "خروج از حساب انجام شد."
                                    }
                                )
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("خروج امن") }
                    }
                    AccountMode.PASSWORD -> {
                        OutlinedTextField(
                            value = currentPassword, onValueChange = { currentPassword = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("رمز فعلی") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        OutlinedTextField(
                            value = newPassword, onValueChange = { newPassword = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("رمز جدید؛ حداقل ۸ نویسه") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        OutlinedTextField(
                            value = newPasswordConfirm, onValueChange = { newPasswordConfirm = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("تکرار رمز جدید") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Button(
                            onClick = {
                                submit(
                                    { repository.changePassword(currentPassword, newPassword, newPasswordConfirm) },
                                    {
                                        applyUser(null)
                                        mode = AccountMode.LOGIN
                                        password = ""
                                        currentPassword = ""
                                        newPassword = ""
                                        newPasswordConfirm = ""
                                        message = "رمز تغییر کرد؛ برای امنیت دوباره وارد شوید."
                                    }
                                )
                            },
                            enabled = !busy && currentPassword.isNotEmpty() &&
                                newPassword.length >= 8 && newPassword == newPasswordConfirm,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "در حال ذخیره…" else "تغییر رمز") }
                        TextButton(onClick = { mode = AccountMode.PROFILE; message = null }) {
                            Text("بازگشت به حساب")
                        }
                    }
                }
                message?.let {
                    Text(
                        text = it,
                        color = if (isError) androidx.compose.material3.MaterialTheme.colorScheme.error
                        else androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

private fun accountError(error: Throwable): String {
    return when ((error as? MobileApiException)?.code) {
        "invalid_credentials" -> "اطلاعات ورود درست نیست."
        "unauthorized" -> "نشست حساب معتبر نیست؛ دوباره وارد شوید."
        "rate_limited" -> "تلاش‌های زیاد ثبت شده است. کمی بعد دوباره امتحان کنید."
        "invalid_country" -> "کشور انتخاب‌شده معتبر نیست."
        "invalid_name" -> "نام کامل را درست وارد کنید."
        "terms_required" -> "پذیرش قوانین برای ثبت‌نام الزامی است."
        "invalid_password" -> "رمزها باید یکسان و دست‌کم ۸ نویسه باشند."
        "invalid_email" -> "نشانی ایمیل معتبر نیست."
        "invalid_phone" -> "شمارهٔ تلفن معتبر نیست."
        "registration_failed" -> "ثبت‌نام انجام نشد؛ اطلاعات را بررسی کنید."
        "profile_update_failed" -> "ذخیرهٔ پروفایل انجام نشد."
        "password_change_failed" -> "تغییر رمز انجام نشد؛ رمز فعلی را بررسی کنید."
        "service_unavailable" -> "سرویس موقتاً در دسترس نیست."
        "invalid_token_response" -> "پاسخ احراز هویت سرور معتبر نبود."
        "invalid_server_response" -> "پاسخ سرور قابل پردازش نیست."
        "request_too_large" -> "درخواست از حد مجاز بزرگ‌تر است."
        else -> "ارتباط با سرویس برقرار نشد. اینترنت و دسترسی API را بررسی کنید."
    }
}
