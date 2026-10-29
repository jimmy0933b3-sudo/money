package com.moneytracker.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moneytracker.app.data.backup.BackupPayload
import com.moneytracker.app.data.backup.BackupRestoreRepository
import com.moneytracker.app.data.backup.BackupSuccessDetails
import com.moneytracker.app.data.backup.CloudBackupInfo
import com.moneytracker.app.data.backup.DriveAuthException
import com.moneytracker.app.data.backup.DriveManager
import com.moneytracker.app.data.backup.GoogleDriveManager
import com.moneytracker.app.data.backup.VerifiedRestoreSummary
import com.moneytracker.app.data.db.AppDatabase
import com.moneytracker.app.data.db.DatabaseInitializer
import com.moneytracker.app.data.entity.AccountEntity
import com.moneytracker.app.data.entity.AppSettingsEntity
import com.moneytracker.app.data.entity.CategoryEntity
import com.moneytracker.app.data.entity.TransactionEntity
import com.moneytracker.app.data.model.LoanType
import com.moneytracker.app.data.model.TransactionType
import com.moneytracker.app.data.repository.AccountWithBalance
import com.moneytracker.app.data.repository.AppRepository
import com.moneytracker.app.data.repository.ContactSummary
import com.moneytracker.app.data.repository.LoanWithDetails
import com.moneytracker.app.worker.BackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel @JvmOverloads constructor(
    application: Application,
    database: AppDatabase? = null
) : AndroidViewModel(application) {

    val db = database ?: AppDatabase.getDatabase(application)
    val repository = AppRepository(db)

    // Current Month range
    private val calendar = Calendar.getInstance()
    private val startOfMonth: Long
    private val endOfMonth: Long

    init {
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        startOfMonth = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        endOfMonth = calendar.timeInMillis
    }

    // Home Flow States
    val monthlyIncome: StateFlow<Long> = repository.getIncomeSumBetweenFlow(startOfMonth, endOfMonth)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val monthlyExpense: StateFlow<Long> = repository.getExpenseSumBetweenFlow(startOfMonth, endOfMonth)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val accountsWithBalance: StateFlow<List<AccountWithBalance>> = repository.getAllAccountsWithBalanceFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalAccountBalance: StateFlow<Long> = accountsWithBalance.combine(accountsWithBalance) { list, _ ->
        list.sumOf { it.currentBalance }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val outstandingLendTotal: StateFlow<Long> = repository.getOutstandingLendTotalFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val outstandingBorrowTotal: StateFlow<Long> = repository.getOutstandingBorrowTotalFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val transactions: StateFlow<List<TransactionEntity>> = repository.getAllTransactionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.getAllCategoriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = repository.getAllAccountsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loansWithDetails: StateFlow<List<LoanWithDetails>> = repository.getAllLoansWithDetailsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contactSummaries: StateFlow<List<ContactSummary>> = repository.getContactSummariesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    enum class GoogleAuthState {
        LOGGED_OUT,
        LOGGING_IN,
        LOGGED_IN,
        FAILED,
        NEEDS_AUTH
    }

    sealed class AccountSaveUiState {
        object Idle : AccountSaveUiState()
        object Saving : AccountSaveUiState()
        object Success : AccountSaveUiState()
        data class Error(val message: String) : AccountSaveUiState()
    }

    sealed class CloudBackupListState {
        object Idle : CloudBackupListState()
        object Loading : CloudBackupListState()
        data class Success(val backups: List<CloudBackupInfo>) : CloudBackupListState()
        object Empty : CloudBackupListState()
        data class Error(val message: String) : CloudBackupListState()
    }

    sealed class RestoreUiState {
        object Idle : RestoreUiState()
        object Downloading : RestoreUiState()
        data class Preview(val fileId: String, val payload: BackupPayload) : RestoreUiState()
        object Restoring : RestoreUiState()
        data class Success(val summary: VerifiedRestoreSummary) : RestoreUiState()
        data class Error(val message: String) : RestoreUiState()
    }

    sealed class BackupUiState {
        object Idle : BackupUiState()
        object BackingUp : BackupUiState()
        data class Success(val details: BackupSuccessDetails) : BackupUiState()
        data class Error(val message: String) : BackupUiState()
    }

    // Settings / Backup State
    private val _googleAccountEmail = MutableStateFlow<String?>(null)
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    private val _googleAuthState = MutableStateFlow(GoogleAuthState.LOGGED_OUT)
    val googleAuthState: StateFlow<GoogleAuthState> = _googleAuthState.asStateFlow()

    private val _googleAuthErrorMessage = MutableStateFlow<String?>(null)
    val googleAuthErrorMessage: StateFlow<String?> = _googleAuthErrorMessage.asStateFlow()

    private val _driveAuthIntent = MutableStateFlow<Intent?>(null)
    val driveAuthIntent: StateFlow<Intent?> = _driveAuthIntent.asStateFlow()

    private val _lastBackupTime = MutableStateFlow<Long?>(null)
    val lastBackupTime: StateFlow<Long?> = _lastBackupTime.asStateFlow()

    private val _cloudBackups = MutableStateFlow<List<CloudBackupInfo>>(emptyList())
    val cloudBackups: StateFlow<List<CloudBackupInfo>> = _cloudBackups.asStateFlow()

    private val _cloudBackupListState = MutableStateFlow<CloudBackupListState>(CloudBackupListState.Idle)
    val cloudBackupListState: StateFlow<CloudBackupListState> = _cloudBackupListState.asStateFlow()

    private val _restoreUiState = MutableStateFlow<RestoreUiState>(RestoreUiState.Idle)
    val restoreUiState: StateFlow<RestoreUiState> = _restoreUiState.asStateFlow()

    private val _backupUiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupUiState: StateFlow<BackupUiState> = _backupUiState.asStateFlow()

    private val _accountSaveState = MutableStateFlow<AccountSaveUiState>(AccountSaveUiState.Idle)
    val accountSaveState: StateFlow<AccountSaveUiState> = _accountSaveState.asStateFlow()

    private val _isOperationInProgress = MutableStateFlow(false)
    val isOperationInProgress: StateFlow<Boolean> = _isOperationInProgress.asStateFlow()

    private val _isRestorePending = MutableStateFlow(false)
    val isRestorePending: StateFlow<Boolean> = _isRestorePending.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()
    private val _isLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isLoading: kotlinx.coroutines.flow.StateFlow<Boolean> = _isLoading.asStateFlow()

    // Add a state to trigger retry
    private var pendingOperation: (() -> Unit)? = null

    init {
        loadSettings()
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    fun setUiMessage(message: String) {
        _uiMessage.value = message
    }

    fun resetAccountSaveState() {
        _accountSaveState.value = AccountSaveUiState.Idle
    }

    fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            val email = db.settingsAndBackupDao().getSettingValue("google_account_email")
            val cleanEmail = if (email.isNullOrBlank()) null else email
            _googleAccountEmail.value = cleanEmail

            val backupRepo = BackupRestoreRepository(db)
            val pending = backupRepo.isRestorePending(cleanEmail)
            _isRestorePending.value = pending

            if (cleanEmail != null) {
                val needsUpdate = db.settingsAndBackupDao().getSettingValue("google_auth_needs_update")
                if (needsUpdate == "true") {
                    _googleAuthState.value = GoogleAuthState.NEEDS_AUTH
                    _googleAuthErrorMessage.value = "背景自動備份失敗：需要重新授權 Google Drive"
                } else {
                    _googleAuthState.value = GoogleAuthState.LOGGED_IN
                }
                if (!pending) {
                    BackupWorker.schedulePeriodicBackup(getApplication())
                }
            } else {
                _googleAuthState.value = GoogleAuthState.LOGGED_OUT
            }

            val lastTimeStr = db.settingsAndBackupDao().getSettingValue("last_backup_time")
            _lastBackupTime.value = lastTimeStr?.toLongOrNull()
        }
    }

    fun startGoogleSignIn() {
        _googleAuthState.value = GoogleAuthState.LOGGING_IN
        _googleAuthErrorMessage.value = null
        _driveAuthIntent.value = null
    }

    fun onGoogleSignInSuccess(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            val cleanEmail = email.trim()
            val backupRepo = BackupRestoreRepository(db)

            val previousEmail = db.settingsAndBackupDao().getSettingValue("google_account_email")
            val isAccountChanged = previousEmail.isNullOrBlank() || !previousEmail.equals(cleanEmail, ignoreCase = true)

            db.settingsAndBackupDao().setSettingValue(AppSettingsEntity("google_account_email", cleanEmail))
            _googleAccountEmail.value = cleanEmail
            _googleAuthState.value = GoogleAuthState.LOGGED_IN
            _googleAuthErrorMessage.value = null
            _driveAuthIntent.value = null

            db.settingsAndBackupDao().setSettingValue(AppSettingsEntity("google_auth_needs_update", "false"))

            // 若為新帳號或首次登入，預設設為 restorePending = true，防止上傳預設資料
            if (isAccountChanged && !backupRepo.isRestorePending(cleanEmail)) {
                // 檢查是否已有選擇紀錄，若無則標記Pending
                val settingKey = "restore_pending_${cleanEmail.lowercase()}"
                if (db.settingsAndBackupDao().getSettingValue(settingKey) == null) {
                    backupRepo.setRestorePending(cleanEmail, true)
                }
            }

            val pending = backupRepo.isRestorePending(cleanEmail)
            _isRestorePending.value = pending

            if (!pending) {
                BackupWorker.schedulePeriodicBackup(getApplication())
            }

            _uiMessage.value = "已成功連結 Google 帳號：$cleanEmail"

            // Resume any pending operation
            pendingOperation?.invoke()
            pendingOperation = null
        }
    }

    fun onGoogleSignInFailure(statusCode: Int?, rawErrorMsg: String?) {
        _googleAuthState.value = GoogleAuthState.FAILED
        val userMsg = when (statusCode) {
            10 -> "Google 登入設定不符（錯誤碼: 10 DEVELOPER_ERROR）。請在設定頁的登入診斷複製目前 APK 的套件名稱及 SHA-1，核對 Google Cloud Android OAuth 憑證。"
            12500 -> "Google 登入失敗 (錯誤碼: 12500)：請確認裝置已安裝且更新 Google Play 服務，並已啟用 OAuth。"
            12501, 16 -> "登入已取消"
            else -> "Google 登入失敗 (${statusCode?.let { "錯誤碼: $it" } ?: "未知錯誤"})：${rawErrorMsg ?: "存取授權失敗"}"
        }
        _googleAuthErrorMessage.value = userMsg
        _uiMessage.value = userMsg
        pendingOperation = null
    }

    fun onGoogleSignInNeedsAuth(email: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            db.settingsAndBackupDao().setSettingValue(AppSettingsEntity("google_account_email", email))
            _googleAccountEmail.value = email
            _googleAuthState.value = GoogleAuthState.NEEDS_AUTH
            _googleAuthErrorMessage.value = null
            _driveAuthIntent.value = null
            _uiMessage.value = message
        }
    }

    fun signOutGoogleAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            db.settingsAndBackupDao().setSettingValue(AppSettingsEntity("google_account_email", ""))
            BackupWorker.cancelPeriodicBackup(getApplication())
            _googleAccountEmail.value = null
            _cloudBackups.value = emptyList()
            _cloudBackupListState.value = CloudBackupListState.Idle
            _lastBackupTime.value = null
            _googleAuthState.value = GoogleAuthState.LOGGED_OUT
            _googleAuthErrorMessage.value = null
            _driveAuthIntent.value = null
            _uiMessage.value = "已登出 Google 帳號，並清空雲端備份歷史對照"
            pendingOperation = null
        }
    }

    fun setGoogleAccount(email: String?) {
        if (email.isNullOrBlank()) {
            signOutGoogleAccount()
        } else {
            onGoogleSignInSuccess(email)
        }
    }

    // Actions
    fun addTransaction(
        type: TransactionType,
        amount: Long,
        dateMillis: Long,
        sourceAccountId: Long,
        targetAccountId: Long?,
        categoryId: Long?,
        note: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DatabaseInitializer.ensureInitialized(db)
                repository.insertTransaction(
                    TransactionEntity(
                        type = type,
                        amount = amount,
                        dateMillis = dateMillis,
                        sourceAccountId = sourceAccountId,
                        targetAccountId = targetAccountId,
                        categoryId = categoryId,
                        note = note
                    )
                )
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _uiMessage.value = "新增交易成功"
                    onSuccess()
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onError(e.message ?: "儲存失敗")
                }
            }
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DatabaseInitializer.ensureInitialized(db)
                repository.softDeleteTransaction(id)
                _uiMessage.value = "已刪除交易"
            } catch (e: IllegalArgumentException) {
                _uiMessage.value = e.message
            }
        }
    }




    fun addContact(name: String, phone: String, note: String, onSuccess: ((Long) -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DatabaseInitializer.ensureInitialized(db)
                val id = repository.addContact(name, phone, note)
                _uiMessage.value = "新增對象成功"
                kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess?.invoke(id) }
            } catch (e: Exception) {
                _uiMessage.value = "新增對象失敗: ${e.message}"
                kotlinx.coroutines.withContext(Dispatchers.Main) { onError?.invoke(e.message ?: "新增失敗") }
            }
        }
    }

    fun updateContact(contact: com.moneytracker.app.data.entity.ContactEntity, onSuccess: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DatabaseInitializer.ensureInitialized(db)
                repository.updateContact(contact)
                _uiMessage.value = "更新對象成功"
                kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess?.invoke() }
            } catch (e: Exception) {
                _uiMessage.value = "更新對象失敗: ${e.message}"
                kotlinx.coroutines.withContext(Dispatchers.Main) { onError?.invoke(e.message ?: "更新失敗") }
            }
        }
    }

    fun deleteContact(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            repository.softDeleteContact(id)
            _uiMessage.value = "停用對象成功"
        }
    }

    fun addLoan(
        contactName: String,
        type: LoanType,
        amount: Long,
        dateMillis: Long,
        dueDateMillis: Long?,
        accountId: Long,
        note: String,
        contactId: Long? = null,
        onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                DatabaseInitializer.ensureInitialized(db)
                repository.createLoanTransaction(
                    contactName = contactName,
                    type = type,
                    amount = amount,
                    dateMillis = dateMillis,
                    dueDateMillis = dueDateMillis,
                    accountId = accountId,
                    note = note,
                    contactId = contactId
                )
                _uiMessage.value = "新增借貸紀錄成功"
                kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _uiMessage.value = "新增失敗: ${e.message}"
                kotlinx.coroutines.withContext(Dispatchers.Main) { onError(e.message ?: "新增失敗") }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addLoanRepayment(
        loanId: Long,
        amount: Long,
        dateMillis: Long,
        accountId: Long,
        note: String,
        onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                DatabaseInitializer.ensureInitialized(db)
                val result = repository.addRepayment(loanId, amount, dateMillis, accountId, note)
                if (result.isSuccess) {
                    _uiMessage.value = "新增還款成功"
                    kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess() }
                } else {
                    _uiMessage.value = result.exceptionOrNull()?.message ?: "還款失敗"
                    kotlinx.coroutines.withContext(Dispatchers.Main) { onError(_uiMessage.value ?: "還款失敗") }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteLoan(loanId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            repository.deleteLoan(loanId)
            _uiMessage.value = "已刪除借貸紀錄"
        }
    }

    fun deleteLoanRepayment(repaymentId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            repository.deleteRepayment(repaymentId)
            _uiMessage.value = "已刪除還款紀錄"
        }
    }

    fun dismissRestorePendingAndStartFresh() {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            val email = _googleAccountEmail.value
            if (!email.isNullOrBlank()) {
                val backupRepo = BackupRestoreRepository(db)
                backupRepo.setRestorePending(email, false)
                _isRestorePending.value = false
                BackupWorker.schedulePeriodicBackup(getApplication())
                _uiMessage.value = "已選擇以目前資料開始記帳"
            }
        }
    }

    fun performBackup(password: String?, customDriveManager: DriveManager? = null, saveAutoBackupPassword: Boolean = false) {
        if (_isOperationInProgress.value) return
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            val email = _googleAccountEmail.value
            if (saveAutoBackupPassword) {
                com.moneytracker.app.data.backup.SecureStorageManager.setAutoBackupPassword(getApplication(), password)
            }
            if (email.isNullOrEmpty() && customDriveManager == null) {
                _uiMessage.value = "請先連動 Google 帳號"
                _backupUiState.value = BackupUiState.Error("請先連動 Google 帳號")
                return@launch
            }

            val tempBackupRepo = BackupRestoreRepository(db)
            if (tempBackupRepo.isRestorePending(email)) {
                _uiMessage.value = "尚待選擇是否還原既有雲端備份，暫停自動與手動備份"
                _backupUiState.value = BackupUiState.Error("尚待選擇是否還原既有雲端備份，暫停備份")
                return@launch
            }

            _isOperationInProgress.value = true
            _backupUiState.value = BackupUiState.BackingUp

            try {
                val driveManager = customDriveManager ?: GoogleDriveManager(getApplication(), email!!)
                val backupRepo = BackupRestoreRepository(db, driveManager)

                val result = backupRepo.backupToGoogleDrive(password)
                if (result.isSuccess) {
                    val details = result.getOrThrow()
                    _lastBackupTime.value = details.backupTime
                    _backupUiState.value = BackupUiState.Success(details)
                    _uiMessage.value = "Google Drive 備份成功！"
                } else {
                    val exception = result.exceptionOrNull()
                    if (exception is DriveAuthException) {
                        _googleAuthState.value = GoogleAuthState.NEEDS_AUTH
                        _driveAuthIntent.value = exception.intent
                        pendingOperation = { performBackup(password, customDriveManager, saveAutoBackupPassword) }
                        _uiMessage.value = "需要重新授權 Google Drive"
                        _backupUiState.value = BackupUiState.Error("需要重新授權 Google Drive")
                    } else {
                        val errorMsg = exception?.message ?: "備份失敗"
                        _backupUiState.value = BackupUiState.Error(errorMsg)
                        _uiMessage.value = "備份失敗: $errorMsg"
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "備份過程發生錯誤"
                _backupUiState.value = BackupUiState.Error(errorMsg)
                _uiMessage.value = "備份失敗: $errorMsg"
            } finally {
                _isOperationInProgress.value = false
            }
        }
    }

    fun fetchCloudBackups(customDriveManager: DriveManager? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            val email = _googleAccountEmail.value
            if (email.isNullOrEmpty() && customDriveManager == null) {
                _cloudBackupListState.value = CloudBackupListState.Error("請先連結 Google 帳號")
                return@launch
            }

            _cloudBackupListState.value = CloudBackupListState.Loading
            _cloudBackups.value = emptyList()

            try {
                val driveManager = customDriveManager ?: GoogleDriveManager(getApplication(), email!!)
                val backupRepo = BackupRestoreRepository(db, driveManager)

                val result = backupRepo.listCloudBackups()
                if (result.isSuccess) {
                    val backups = result.getOrDefault(emptyList())
                    _cloudBackups.value = backups
                    if (backups.isEmpty()) {
                        _cloudBackupListState.value = CloudBackupListState.Empty
                    } else {
                        _cloudBackupListState.value = CloudBackupListState.Success(backups)
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    if (exception is DriveAuthException) {
                        _googleAuthState.value = GoogleAuthState.NEEDS_AUTH
                        _driveAuthIntent.value = exception.intent
                        pendingOperation = { fetchCloudBackups(customDriveManager) }
                        _cloudBackupListState.value = CloudBackupListState.Error("需要重新授權 Google Drive")
                        _uiMessage.value = "需要重新授權 Google Drive"
                    } else {
                        val errorMsg = exception?.message ?: "無法取得雲端備份列表"
                        _cloudBackupListState.value = CloudBackupListState.Error(errorMsg)
                        _uiMessage.value = "取得雲端備份失敗: $errorMsg"
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "發生未知錯誤"
                _cloudBackupListState.value = CloudBackupListState.Error(errorMsg)
                _uiMessage.value = "取得雲端備份失敗: $errorMsg"
            }
        }
    }

    fun inspectBackupForRestore(fileId: String, password: String?, customDriveManager: DriveManager? = null) {
        if (_isOperationInProgress.value) return
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            val email = _googleAccountEmail.value
            if (email.isNullOrEmpty() && customDriveManager == null) {
                _restoreUiState.value = RestoreUiState.Error("請先連動 Google 帳號")
                return@launch
            }

            _isOperationInProgress.value = true
            _restoreUiState.value = RestoreUiState.Downloading
            try {
                val driveManager = customDriveManager ?: GoogleDriveManager(getApplication(), email!!)
                val backupRepo = BackupRestoreRepository(db, driveManager)

                val result = backupRepo.inspectCloudBackup(fileId, password)
                if (result.isSuccess) {
                    val payload = result.getOrThrow()
                    _restoreUiState.value = RestoreUiState.Preview(fileId, payload)
                } else {
                    val exception = result.exceptionOrNull()
                    if (exception is DriveAuthException) {
                        _googleAuthState.value = GoogleAuthState.NEEDS_AUTH
                        _driveAuthIntent.value = exception.intent
                        pendingOperation = { inspectBackupForRestore(fileId, password, customDriveManager) }
                        _restoreUiState.value = RestoreUiState.Error("需要重新授權 Google Drive")
                        _uiMessage.value = "需要重新授權 Google Drive"
                    } else {
                        val errorMsg = exception?.message ?: "備份檔檢驗失敗"
                        _restoreUiState.value = RestoreUiState.Error(errorMsg)
                        _uiMessage.value = "備份檢驗失敗: $errorMsg"
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "發生未知錯誤"
                _restoreUiState.value = RestoreUiState.Error(errorMsg)
                _uiMessage.value = "備份檢驗失敗: $errorMsg"
            } finally {
                _isOperationInProgress.value = false
            }
        }
    }

    fun restoreCloudBackup(fileId: String, password: String?, customDriveManager: DriveManager? = null) {
        confirmAndRestoreCloudBackup(fileId, password, customDriveManager)
    }

    fun confirmAndRestoreCloudBackup(fileId: String, password: String?, customDriveManager: DriveManager? = null) {
        if (_isOperationInProgress.value) return
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            val email = _googleAccountEmail.value
            if (email.isNullOrEmpty() && customDriveManager == null) {
                _restoreUiState.value = RestoreUiState.Error("請先連動 Google 帳號")
                return@launch
            }

            _isOperationInProgress.value = true
            _restoreUiState.value = RestoreUiState.Restoring
            try {
                val driveManager = customDriveManager ?: GoogleDriveManager(getApplication(), email!!)
                val backupRepo = BackupRestoreRepository(db, driveManager)

                val restoreResult = backupRepo.restoreFromCloudBackup(fileId, password)
                if (restoreResult.isSuccess) {
                    val summary = restoreResult.getOrThrow()
                    if (!email.isNullOrBlank()) {
                        backupRepo.setRestorePending(email, false)
                        _isRestorePending.value = false
                        BackupWorker.schedulePeriodicBackup(getApplication())
                    }
                    loadSettings()
                    _restoreUiState.value = RestoreUiState.Success(summary)
                    _uiMessage.value = "資料還原成功！"
                } else {
                    val exception = restoreResult.exceptionOrNull()
                    if (exception is DriveAuthException) {
                        _googleAuthState.value = GoogleAuthState.NEEDS_AUTH
                        _driveAuthIntent.value = exception.intent
                        pendingOperation = { confirmAndRestoreCloudBackup(fileId, password, customDriveManager) }
                        _restoreUiState.value = RestoreUiState.Error("需要重新授權 Google Drive")
                        _uiMessage.value = "需要重新授權 Google Drive"
                    } else {
                        val errorMsg = exception?.message ?: "寫入資料庫失敗"
                        _restoreUiState.value = RestoreUiState.Error(errorMsg)
                        _uiMessage.value = "還原失敗: $errorMsg"
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "還原過程發生未知錯誤"
                _restoreUiState.value = RestoreUiState.Error(errorMsg)
                _uiMessage.value = "還原失敗: $errorMsg"
            } finally {
                _isOperationInProgress.value = false
            }
        }
    }

    fun resetRestoreUiState() {
        _restoreUiState.value = RestoreUiState.Idle
    }

    fun resetBackupUiState() {
        _backupUiState.value = BackupUiState.Idle
    }

    fun addAccount(name: String, type: com.moneytracker.app.data.model.AccountType, initialBalance: Long) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _accountSaveState.value = AccountSaveUiState.Error("帳戶名稱不可為空")
            return
        }
        _accountSaveState.value = AccountSaveUiState.Saving
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            try {
                repository.insertAccount(AccountEntity(name = cleanName, type = type, initialBalance = initialBalance))
                _accountSaveState.value = AccountSaveUiState.Success
                _uiMessage.value = "新增帳戶成功"
            } catch (e: Exception) {
                val errorMsg = e.message ?: "新增帳戶失敗"
                _accountSaveState.value = AccountSaveUiState.Error(errorMsg)
                _uiMessage.value = "新增帳戶失敗: $errorMsg"
            }
        }
    }

    fun addCategory(name: String, type: TransactionType) {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseInitializer.ensureInitialized(db)
            repository.insertCategory(CategoryEntity(name = name, type = type))
            _uiMessage.value = "新增分類成功"
        }
    }

}
