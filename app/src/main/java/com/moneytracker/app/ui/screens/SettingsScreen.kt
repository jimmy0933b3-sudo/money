@file:Suppress("DEPRECATION")
package com.moneytracker.app.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.moneytracker.app.data.model.AccountType
import com.moneytracker.app.data.model.TransactionType
import com.moneytracker.app.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val googleAccountEmail by viewModel.googleAccountEmail.collectAsState()
    val googleAuthState by viewModel.googleAuthState.collectAsState()
    val googleAuthErrorMessage by viewModel.googleAuthErrorMessage.collectAsState()
    val driveAuthIntent by viewModel.driveAuthIntent.collectAsState()
    val lastBackupTime by viewModel.lastBackupTime.collectAsState()
    val cloudBackups by viewModel.cloudBackups.collectAsState()
    val cloudBackupListState by viewModel.cloudBackupListState.collectAsState()
    val restoreUiState by viewModel.restoreUiState.collectAsState()
    val backupUiState by viewModel.backupUiState.collectAsState()
    val accountSaveState by viewModel.accountSaveState.collectAsState()
    val isOperationInProgress by viewModel.isOperationInProgress.collectAsState()
    val isRestorePending by viewModel.isRestorePending.collectAsState()

    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var showAccountDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreTargetFileId by remember { mutableStateOf<String?>(null) }
    var pendingAuthEmail by remember { mutableStateOf<String?>(null) }
    var showOAuthHelp by remember { mutableStateOf(false) }
    val installedSha1 = remember(context) { installedApkSha1(context) }

    val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.TAIWAN)

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val email = account?.email
            val hasDrivePermission = GoogleSignIn.hasPermissions(
                account,
                Scope(DriveScopes.DRIVE_APPDATA),
                Scope(DriveScopes.DRIVE_FILE)
            )

            if (!email.isNullOrBlank() && hasDrivePermission) {
                viewModel.onGoogleSignInSuccess(email)
            } else if (!email.isNullOrBlank()) {
                pendingAuthEmail = email
                viewModel.onGoogleSignInNeedsAuth(email, "尚未授予 Google Drive 存取權限，請確認授權後重試")
            } else {
                viewModel.onGoogleSignInFailure(null, "未取得 Google 帳號 Email")
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            viewModel.onGoogleSignInFailure(e.statusCode, e.message)
        } catch (e: Exception) {
            viewModel.onGoogleSignInFailure(null, e.message)
        }
    }

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val email = pendingAuthEmail ?: googleAccountEmail
            if (email != null) {
                viewModel.onGoogleSignInSuccess(email)
            } else {
                viewModel.onGoogleSignInFailure(null, "授權後無法取得 Email")
            }
        } else {
            val email = pendingAuthEmail ?: googleAccountEmail
            if (email != null) {
                viewModel.onGoogleSignInNeedsAuth(email, "未授權 Google Drive 存取權限，請重新授權")
            } else {
                viewModel.onGoogleSignInFailure(null, "未授權 Google Drive 存取權限")
            }
        }
    }

    LaunchedEffect(driveAuthIntent) {
        driveAuthIntent?.let { intent ->
            driveAuthLauncher.launch(intent)
        }
    }

    fun launchGoogleSignIn() {
        if (googleAuthState == MainViewModel.GoogleAuthState.LOGGING_IN) return
        viewModel.startGoogleSignIn()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA), Scope(DriveScopes.DRIVE_FILE))
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("設定與雲端備份", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "連結雲端、管理帳戶，安心保存每一筆記帳。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 重裝/切換帳號 待還原提示卡片
        if (isRestorePending && !googleAccountEmail.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("尚未完成雲端資料還原選擇", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "檢測到此 Google 帳號 ($googleAccountEmail) 在此裝置尚未選擇是否還原既有備份。在您完成選擇前，已自動暫停上傳自動備份，防止預設初始資料覆蓋雲端歷史備份。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.resetRestoreUiState()
                                    viewModel.fetchCloudBackups()
                                    showRestoreDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("還原既有資料")
                            }

                            Button(
                                onClick = { viewModel.dismissRestorePendingAndStartFresh() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Text("以目前資料開始")
                            }
                        }
                    }
                }
            }
        }

        // Google Drive account, backup actions, and actionable OAuth diagnostics.
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp).size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Google Drive 備份", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (googleAuthState == MainViewModel.GoogleAuthState.LOGGED_IN)
                                    "已連結 Google 帳號" else "安全備份你的帳務資料",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                if (googleAccountEmail.isNullOrBlank()) "尚未連結 Google 帳號"
                                else googleAccountEmail!!,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(5.dp))
                            val lastBackupLabel = lastBackupTime?.let { dateFormat.format(Date(it)) } ?: "尚無備份"
                            Text(
                                "上次成功備份：$lastBackupLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (googleAuthState == MainViewModel.GoogleAuthState.LOGGED_IN) {
                        OutlinedButton(
                            onClick = {
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                GoogleSignIn.getClient(context, gso).signOut()
                                viewModel.signOutGoogleAccount()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("切換 / 登出 Google 帳號")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (googleAuthState == MainViewModel.GoogleAuthState.NEEDS_AUTH && driveAuthIntent != null) {
                                    driveAuthLauncher.launch(driveAuthIntent!!)
                                } else if (googleAuthState == MainViewModel.GoogleAuthState.NEEDS_AUTH) {
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                    GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
                                        launchGoogleSignIn()
                                    }
                                } else {
                                    launchGoogleSignIn()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = googleAuthState != MainViewModel.GoogleAuthState.LOGGING_IN,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                when (googleAuthState) {
                                    MainViewModel.GoogleAuthState.LOGGING_IN -> "正在登入..."
                                    MainViewModel.GoogleAuthState.NEEDS_AUTH -> "重新授權 Google Drive"
                                    else -> "連結 Google 帳號"
                                }
                            )
                        }
                    }

                    if (googleAuthState == MainViewModel.GoogleAuthState.NEEDS_AUTH) {
                        Text(
                            "Google Drive 授權已過期或遭撤銷，請重新授權。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (!googleAuthErrorMessage.isNullOrBlank()) {
                        val developerError = googleAuthErrorMessage!!.contains("DEVELOPER_ERROR") ||
                            googleAuthErrorMessage!!.contains("錯誤碼: 10")
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (developerError) "Google 登入設定不符（錯誤碼 10）" else "登入或授權失敗",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        if (developerError)
                                            "請檢查 Google Cloud 的 Android OAuth 憑證，確認套件名稱及 SHA-1 與此手機安裝的 APK 完全相同。"
                                        else googleAuthErrorMessage!!,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    TextButton(onClick = { showOAuthHelp = !showOAuthHelp }) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (showOAuthHelp) "收起 Google 登入診斷" else "Google 登入診斷 / 查看 SHA-1")
                    }

                    if (showOAuthHelp) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("目前安裝的 APK 憑證", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "這是從手機已安裝 App 直接讀取的簽章指紋，不是 APK 檔案校驗碼。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OAuthDiagnosticValue(
                                    label = "Package Name",
                                    value = context.packageName,
                                    onCopy = { copyOAuthText(context, context.packageName) }
                                )
                                OAuthDiagnosticValue(
                                    label = "SHA-1",
                                    value = installedSha1 ?: "無法取得，請從 GitHub Actions 簽章報告查看",
                                    onCopy = if (installedSha1 == null) null else {
                                        { copyOAuthText(context, installedSha1) }
                                    }
                                )
                                HorizontalDivider()
                                Text(
                                    "到 Google Cloud Console → API 與服務 → 憑證，建立或修改「Android」OAuth 用戶端，填入上述資訊。並確認 Drive API 已啟用、OAuth 同意畫面及測試使用者設定正確。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    when (val bState = backupUiState) {
                        is MainViewModel.BackupUiState.BackingUp -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在備份到 Google Drive...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is MainViewModel.BackupUiState.Success -> {
                            val details = bState.details
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("備份完成", style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "時間：${dateFormat.format(Date(details.backupTime))} · ${details.transactionCount} 筆交易",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    TextButton(onClick = {
                                        viewModel.resetRestoreUiState()
                                        viewModel.fetchCloudBackups()
                                        restoreTargetFileId = details.driveFileId
                                        showRestoreDialog = true
                                    }) { Text("查看並還原此備份") }
                                }
                            }
                        }
                        is MainViewModel.BackupUiState.Error -> {
                            Text(
                                "備份失敗：${bState.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showBackupPasswordDialog = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            enabled = !googleAccountEmail.isNullOrBlank() && !isOperationInProgress && !isRestorePending,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("立即備份", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.resetRestoreUiState()
                                viewModel.fetchCloudBackups()
                                restoreTargetFileId = null
                                showRestoreDialog = true
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            enabled = !googleAccountEmail.isNullOrBlank() && !isOperationInProgress,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("雲端還原", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // 帳戶管理
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("帳戶管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    viewModel.resetAccountSaveState()
                    showAccountDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "新增帳戶")
                }
            }
        }

        items(accounts) { account ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${account.name} (${account.type.label})",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Text("初始: NT$ ${account.initialBalance}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 分類管理
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("收支分類管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showCategoryDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新增分類")
                }
            }
        }

        items(categories) { category ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Category, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${category.name} (${category.type.label})")
                    }
                    if (category.isDefault) {
                        Text("預設", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showBackupPasswordDialog) {
        var pwdInput by remember { mutableStateOf("") }
        var saveAsAutoBackup by remember { mutableStateOf(false) }
        val isCurrentlyEncrypted = com.moneytracker.app.data.backup.SecureStorageManager.isAutoBackupEncrypted(context)
        AlertDialog(
            onDismissRequest = { showBackupPasswordDialog = false },
            title = { Text("自訂備份密碼 (可不填)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("設定自訂密碼以 AES 加密備份檔案。如設定密碼，還原時必須輸入該密碼；若忘記密碼將無法還原！", fontSize = 12.sp, color = Color.Red)
                    if (isCurrentlyEncrypted) {
                        Text("目前狀態：自動備份【已啟用加密】", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    } else {
                        Text("目前狀態：自動備份【未啟用加密】", fontSize = 12.sp, color = Color.DarkGray)
                    }
                    OutlinedTextField(
                        value = pwdInput,
                        onValueChange = { pwdInput = it },
                        label = { Text("備份加密密碼 (可留空)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = saveAsAutoBackup,
                            onCheckedChange = { saveAsAutoBackup = it }
                        )
                        Text("同時將此密碼設定為自動備份加密密碼 (留空則取消自動備份加密)", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.performBackup(pwdInput.ifBlank { null }, saveAutoBackupPassword = saveAsAutoBackup)
                        showBackupPasswordDialog = false
                    }
                ) {
                    Text("開始備份")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupPasswordDialog = false }) { Text("取消") }
            }
        )
    }

    if (showRestoreDialog) {
        var pwdInput by remember { mutableStateOf("") }
        var selectedFileId by remember { mutableStateOf<String?>(restoreTargetFileId) }
        val selectedFile = cloudBackups.find { it.fileId == selectedFileId }

        AlertDialog(
            onDismissRequest = {
                if (!isOperationInProgress) {
                    viewModel.resetRestoreUiState()
                    showRestoreDialog = false
                }
            },
            title = { Text("選擇 Google Drive 備份還原") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("警告：還原雲端資料將完全覆蓋本機目前的記帳與借貸資料！", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = pwdInput,
                        onValueChange = { pwdInput = it },
                        label = { Text("備份解密密碼 (如未設定請留空)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isOperationInProgress,
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("雲端備份歷史列表：", fontWeight = FontWeight.Bold)

                    when (val state = cloudBackupListState) {
                        is MainViewModel.CloudBackupListState.Loading -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在讀取雲端備份列表中...", fontSize = 13.sp)
                            }
                        }
                        is MainViewModel.CloudBackupListState.Empty -> {
                            Text("未在 Google Drive 找到任何備份檔", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                        is MainViewModel.CloudBackupListState.Error -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("載入失敗: ${state.message}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(onClick = { viewModel.fetchCloudBackups() }) {
                                    Text("重新載入列表")
                                }
                            }
                        }
                        is MainViewModel.CloudBackupListState.Success -> {
                            LazyColumn(modifier = Modifier.height(150.dp)) {
                                items(state.backups) { file ->
                                    val isSelected = selectedFileId == file.fileId
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable(enabled = !isOperationInProgress) {
                                                selectedFileId = file.fileId
                                                viewModel.inspectBackupForRestore(file.fileId, pwdInput.ifBlank { null })
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(file.fileName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                if (file.isDefaultOnly) {
                                                    Text("【預設初始資料】", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                            }
                                            Text("日期: ${dateFormat.format(Date(file.createdTimeMillis))}", fontSize = 11.sp)
                                            if (file.summaryText.isNotBlank()) {
                                                Text("摘要: ${file.summaryText}", fontSize = 11.sp, color = if (file.isDefaultOnly) Color.Red else Color.DarkGray)
                                            }
                                            Text("File ID: ${file.fileId}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            Text("尚未載入備份列表", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }

                    if (selectedFile != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("已選取備份檔：", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("檔名: ${selectedFile.fileName}", fontSize = 12.sp)
                                Text("時間: ${dateFormat.format(Date(selectedFile.createdTimeMillis))}", fontSize = 12.sp)
                                Text("File ID: ${selectedFile.fileId}", fontSize = 11.sp, color = Color.DarkGray)
                            }
                        }
                    } else {
                        Text("請明確在上方列表中點選要還原的備份檔", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }

                    // 還原執行階段與預覽狀態展示
                    when (val restoreState = restoreUiState) {
                        is MainViewModel.RestoreUiState.Downloading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在下載並驗證備份內容...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        is MainViewModel.RestoreUiState.Preview -> {
                            val payload = restoreState.payload
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("備份檔驗證通過（預覽資訊）：", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("狀況：【尚未寫入資料庫】", color = Color(0xFFD84315), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("備份時間: ${dateFormat.format(Date(payload.createdAt))}", fontSize = 11.sp)
                                    Text("帳戶: ${payload.accounts.size} 筆 | 分類: ${payload.categories.size} 筆", fontSize = 11.sp)
                                    Text("交易: ${payload.transactions.size} 筆 | 借貸: ${payload.loans.size} 筆", fontSize = 11.sp)
                                    Text("聯絡人: ${payload.contacts.size} 筆 | 還款: ${payload.loanRepayments.size} 筆", fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("提示：目前尚未還原！點擊下方「確認覆蓋還原」按鈕才會將資料覆蓋寫入本機資料庫。", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        is MainViewModel.RestoreUiState.Restoring -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在以交易機制覆蓋資料庫並讀回核對...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        is MainViewModel.RestoreUiState.Success -> {
                            val s = restoreState.summary
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("還原成功且已自資料庫讀回核對無誤！", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("資料庫實際筆數：帳戶 ${s.accountCount} 筆, 分類 ${s.categoryCount} 筆, 交易 ${s.transactionCount} 筆, 聯絡人 ${s.contactCount} 筆, 借貸 ${s.loanCount} 筆, 還款 ${s.repaymentCount} 筆", fontSize = 11.sp, color = Color(0xFF1B5E20))
                                }
                            }
                        }
                        is MainViewModel.RestoreUiState.Error -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("還原失敗：", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(restoreState.message, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                                }
                            }
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                if (restoreUiState is MainViewModel.RestoreUiState.Success) {
                    Button(
                        onClick = {
                            viewModel.resetRestoreUiState()
                            showRestoreDialog = false
                        }
                    ) {
                        Text("完成並關閉")
                    }
                } else if (restoreUiState is MainViewModel.RestoreUiState.Preview) {
                    Button(
                        onClick = {
                            val fileId = selectedFileId
                            if (fileId != null) {
                                viewModel.confirmAndRestoreCloudBackup(fileId, pwdInput.ifBlank { null })
                            }
                        },
                        enabled = !isOperationInProgress
                    ) {
                        Text("確認覆蓋還原")
                    }
                } else {
                    Button(
                        onClick = {
                            val fileId = selectedFileId
                            if (fileId != null) {
                                viewModel.inspectBackupForRestore(fileId, pwdInput.ifBlank { null })
                            }
                        },
                        enabled = selectedFileId != null && !isOperationInProgress
                    ) {
                        Text(if (restoreUiState is MainViewModel.RestoreUiState.Error) "重試驗證" else "檢驗備份內容")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.resetRestoreUiState()
                        showRestoreDialog = false
                    },
                    enabled = !isOperationInProgress
                ) {
                    Text(if (restoreUiState is MainViewModel.RestoreUiState.Success) "關閉" else "取消")
                }
            }
        )
    }

    if (showAccountDialog) {
        var accName by remember { mutableStateOf("") }
        var accType by remember { mutableStateOf(AccountType.CASH) }
        var initBalText by remember { mutableStateOf("") }

        LaunchedEffect(accountSaveState) {
            if (accountSaveState is MainViewModel.AccountSaveUiState.Success) {
                showAccountDialog = false
                viewModel.resetAccountSaveState()
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (accountSaveState !is MainViewModel.AccountSaveUiState.Saving) {
                    viewModel.resetAccountSaveState()
                    showAccountDialog = false
                }
            },
            title = { Text("新增帳戶") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = accName,
                        onValueChange = { accName = it },
                        label = { Text("帳戶名稱") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = accountSaveState !is MainViewModel.AccountSaveUiState.Saving,
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = initBalText,
                        onValueChange = { if (it.all { char -> char.isDigit() }) initBalText = it },
                        label = { Text("初始餘額 (TWD)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = accountSaveState !is MainViewModel.AccountSaveUiState.Saving,
                        singleLine = true
                    )

                    if (accountSaveState is MainViewModel.AccountSaveUiState.Saving) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在儲存帳戶至資料庫...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (accountSaveState is MainViewModel.AccountSaveUiState.Error) {
                        val err = (accountSaveState as MainViewModel.AccountSaveUiState.Error).message
                        Text("儲存失敗: $err", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bal = initBalText.toLongOrNull() ?: 0L
                        viewModel.addAccount(accName.trim(), accType, bal)
                    },
                    enabled = accountSaveState !is MainViewModel.AccountSaveUiState.Saving && accName.isNotBlank()
                ) {
                    Text(if (accountSaveState is MainViewModel.AccountSaveUiState.Saving) "儲存中..." else "儲存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAccountSaveState()
                        showAccountDialog = false
                    },
                    enabled = accountSaveState !is MainViewModel.AccountSaveUiState.Saving
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showCategoryDialog) {
        var catName by remember { mutableStateOf("") }
        var catType by remember { mutableStateOf(TransactionType.EXPENSE) }

        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("新增分類") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        label = { Text("分類名稱") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catName.isNotBlank()) {
                            viewModel.addCategory(catName.trim(), catType)
                            showCategoryDialog = false
                        }
                    }
                ) {
                    Text("儲存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun OAuthDiagnosticValue(
    label: String,
    value: String,
    onCopy: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (onCopy != null) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "複製$label")
                }
            }
        }
    }
}

private fun copyOAuthText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("MoneyTracker OAuth", text))
}

private fun installedApkSha1(context: Context): String? = runCatching {
    val packageInfo = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_SIGNING_CERTIFICATES
    )
    val signature = packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()
        ?: return@runCatching null
    MessageDigest.getInstance("SHA-1")
        .digest(signature.toByteArray())
        .joinToString(":") { "%02X".format(it) }
}.getOrNull()
