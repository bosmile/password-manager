package com.example.passwordmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

// Data Models
data class PasswordEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val username: String,
    val password: String,
    val website: String = "",
    val notes: String = ""
)

// Encryption Helper
object CryptoHelper {
    fun encrypt(data: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(hashKey(key), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(data.toByteArray())
        return Base64.encodeToString(encrypted, Base64.DEFAULT)
    }

    fun decrypt(data: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(hashKey(key), "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        val decoded = Base64.decode(data, Base64.DEFAULT)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted)
    }

    private fun hashKey(key: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(key.toByteArray()).copyOf(16)
    }
}

// ViewModel
class PasswordViewModel : ViewModel() {
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked

    private val _passwords = MutableStateFlow<List<PasswordEntry>>(emptyList())
    val passwords: StateFlow<List<PasswordEntry>> = _passwords

    private val _masterPasswordHash = MutableStateFlow<String?>(null)
    private var masterPassword = ""

    fun setMasterPassword(password: String) {
        val hash = hashPassword(password)
        _masterPasswordHash.value = hash
        masterPassword = password
        _isLocked.value = false
    }

    fun unlock(password: String): Boolean {
        val hash = hashPassword(password)
        return if (_masterPasswordHash.value == null) {
            setMasterPassword(password)
            true
        } else if (_masterPasswordHash.value == hash) {
            masterPassword = password
            _isLocked.value = false
            true
        } else {
            false
        }
    }

    fun lock() {
        _isLocked.value = true
        masterPassword = ""
    }

    fun addPassword(entry: PasswordEntry) {
        val encrypted = entry.copy(
            password = CryptoHelper.encrypt(entry.password, masterPassword)
        )
        _passwords.value = _passwords.value + encrypted
    }

    fun updatePassword(entry: PasswordEntry) {
        val encrypted = entry.copy(
            password = CryptoHelper.encrypt(entry.password, masterPassword)
        )
        _passwords.value = _passwords.value.map {
            if (it.id == encrypted.id) encrypted else it
        }
    }

    fun deletePassword(id: String) {
        _passwords.value = _passwords.value.filter { it.id != id }
    }

    fun getDecryptedPassword(entry: PasswordEntry): String {
        return try {
            CryptoHelper.decrypt(entry.password, masterPassword)
        } catch (e: Exception) {
            "***"
        }
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray())
        return Base64.encodeToString(hash, Base64.DEFAULT)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PasswordManagerTheme {
                PasswordManagerApp()
            }
        }
    }
}

@Composable
fun PasswordManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6200EE),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordManagerApp() {
    val viewModel: PasswordViewModel = viewModel()
    val isLocked by viewModel.isLocked.collectAsState()

    if (isLocked) {
        LockScreen(onUnlock = { password ->
            viewModel.unlock(password)
        })
    } else {
        MainScreen(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreen(onUnlock: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Password Manager",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Nhập mật khẩu chính",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        showError = false
                    },
                    label = { Text("Mật khẩu chính") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("Mật khẩu không đúng") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (password.isNotEmpty()) {
                            val success = onUnlock(password)
                            if (!success) showError = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mở khóa")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: PasswordViewModel) {
    val passwords by viewModel.passwords.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var editingEntry by remember { mutableStateOf<PasswordEntry?>(null) }

    val filteredPasswords = passwords.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
        it.username.contains(searchQuery, ignoreCase = true) ||
        it.website.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản lý mật khẩu") },
                actions = {
                    IconButton(onClick = { viewModel.lock() }) {
                        Icon(Icons.Default.Lock, contentDescription = "Khóa")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Thêm")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Tìm kiếm") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            if (filteredPasswords.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (passwords.isEmpty()) "Chưa có mật khẩu nào" else "Không tìm thấy",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPasswords) { entry ->
                        PasswordCard(
                            entry = entry,
                            viewModel = viewModel,
                            onEdit = { editingEntry = it },
                            onDelete = { viewModel.deletePassword(it) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PasswordDialog(
            onDismiss = { showAddDialog = false },
            onSave = { entry ->
                viewModel.addPassword(entry)
                showAddDialog = false
            }
        )
    }

    editingEntry?.let { entry ->
        PasswordDialog(
            entry = entry,
            decryptedPassword = viewModel.getDecryptedPassword(entry),
            onDismiss = { editingEntry = null },
            onSave = { updated ->
                viewModel.updatePassword(updated)
                editingEntry = null
            }
        )
    }
}

@Composable
fun PasswordCard(
    entry: PasswordEntry,
    viewModel: PasswordViewModel,
    onEdit: (PasswordEntry) -> Unit,
    onDelete: (String) -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = entry.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry.website.isNotEmpty()) {
                        Text(
                            text = entry.website,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }

            if (expanded) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showPassword) viewModel.getDecryptedPassword(entry) else "••••••••",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.Delete else Icons.Default.Face,
                                contentDescription = if (showPassword) "Ẩn" else "Hiện"
                            )
                        }
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(viewModel.getDecryptedPassword(entry)))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Sao chép")
                        }
                    }
                }

                if (entry.notes.isNotEmpty()) {
                    Text(
                        text = "Ghi chú: ${entry.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onEdit(entry) }) {
                        Text("Sửa")
                    }
                    TextButton(onClick = { onDelete(entry.id) }) {
                        Text("Xóa", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordDialog(
    entry: PasswordEntry? = null,
    decryptedPassword: String = "",
    onDismiss: () -> Unit,
    onSave: (PasswordEntry) -> Unit
) {
    var title by remember { mutableStateOf(entry?.title ?: "") }
    var username by remember { mutableStateOf(entry?.username ?: "") }
    var password by remember { mutableStateOf(decryptedPassword) }
    var website by remember { mutableStateOf(entry?.website ?: "") }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "Thêm mật khẩu" else "Sửa mật khẩu") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tiêu đề *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Tên đăng nhập *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mật khẩu *") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.Delete else Icons.Default.Face,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Ghi chú") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
                        val newEntry = PasswordEntry(
                            id = entry?.id ?: java.util.UUID.randomUUID().toString(),
                            title = title,
                            username = username,
                            password = password,
                            website = website,
                            notes = notes
                        )
                        onSave(newEntry)
                    }
                },
                enabled = title.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
