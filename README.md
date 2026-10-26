# 記帳與借貸管家 (MoneyTracker Android App)

一款 Android 原生 App，提供個人記帳、多帳戶管理、收支統計、借貸及分次還款管理，以及 Google Drive 雲端加密備份與還原。

## 一、系統特色與技術架構

* **程式語言 (Language)**: Kotlin
* **UI 框架**: Jetpack Compose + Material 3 (繁體中文介面)
* **架構設計**: MVVM + Repository (Offline First 設計)
* **本機資料庫**: Room Database (使用 Long 儲存 TWD 金額最小單位，避免浮點誤差)
* **背景任務**: WorkManager (定期與連線自動備份)
* **備份與還原**: Google Drive API (`appDataFolder` 專屬目錄)、PBKDF2 + AES 加密保護、SHA-256 Checksum 校驗碼
* **最低 Android 版本**: Android 10 (API 29)

---

## 二、功能說明

### 1. 記帳與多帳戶管理
* 支援「收入」、「支出」與「帳戶間轉帳」。
* 預設多個帳戶 (現金、銀行帳戶、電子支付、信用卡)，可隨時新增與編輯。
* 帳戶間轉帳不計入一般收支。
* 內建數字小鍵盤與分類篩選、關鍵字搜尋功能。

### 2. 借貸與分次還款管理
* 完整支援「我借給別人 (借出)」與「我向別人借 (借入)」。
* 自動計算原始借款、累計已還與剩餘欠款。
* 同一個人支援多筆借貸，可同時存在借入與借出（分開呈現，不自動抵銷）。
* **還款保護機制**: 限制累積還款金額不得超過借貸款項本金。
* 借貸本金與還款金流自動反映至指定帳戶餘額，但標記隔離以防止失真一般收支報表。

### 3. 雲端備份與還原
* 支援將本機資料備份至 Google Drive 專屬 `appDataFolder`。
* 支援可選密碼加密備份。
* **交易式還原保護**: 還原過程採 Room Transaction，若解密或還原失敗不會損壞本機資料。

---

## 三、Google OAuth Client ID 與 Drive API 設定指南

* **Android Package Name**: `com.moneytracker.app`

在實際實機環境啟用 Google 帳號登入與 Google Drive 雲端備份時，請依照下列步驟於 Google Cloud Console 與 GitHub 設定：

1. **開啟 Google Cloud Console 專案與啟用 Drive API**:
   * 造訪 [Google Cloud Console](https://console.cloud.google.com/) 並建立或選擇專案。
   * 進入「API 與服務」 > 「程式庫」，搜尋 `Google Drive API` 並點擊「啟用 (Enable)」。

2. **設定 OAuth 同意畫面與測試使用者**:
   * 進入「API 與服務」 > 「OAuth 同意畫面」。
   * 選擇「外部 (External)」，填寫應用程式名稱與支援 Email。
   * 新增必要的 Scope 權限範圍：`https://www.googleapis.com/auth/drive.appdata` 與 `https://www.googleapis.com/auth/drive.file`。
   * 在「測試使用者 (Test users)」清單中新增進行測試的 Google 帳號。

3. **設定 GitHub Secrets (專用 Release 固定簽章庫)**:
   若要在 CI/CD (GitHub Actions) 產出具備固定簽章的測試與交付 APK，請於 GitHub Repository > Settings > Secrets and variables > Actions 新增以下 4 個 Secrets：
   * `KEYSTORE_BASE64`: 將 KeyStore / JKS 檔案轉碼為 Base64 字串（可用 `base64 -w 0 release.keystore` 或 `openssl base64 -A -in release.keystore` 生成）。
   * `KEYSTORE_PASSWORD`: KeyStore 的存取密碼。
   * `KEY_ALIAS`: 金鑰別名 (Alias)。
   * `KEY_PASSWORD`: 金鑰別名專用密碼。

   > ⚠️ `master` 發布建置**必須**設定完整的 4 個 Secrets。缺少任一項即停止建置，禁止使用臨時 Debug 簽章冒充正式 APK。PR / 非 `master` 手動建置才使用 Debug 簽章。

4. **提取 APK 簽章 SHA-1 / SHA-256 指紋**:
   * **方式 A (從 GitHub Actions CI 摘要與 Artifacts 提取 - 推薦)**:
     每次 Actions 建置完成後，會使用 Android SDK `apksigner` 自動驗證交付之 APK，並在 Actions **Job Summary 摘要** 與 Artifacts (`apk_signature_fingerprints.txt`) 產出 **SHA-1 簽章憑證指紋**。
   * **方式 B (使用 Android SDK apksigner 工具由 APK 提取)**:
     ```bash
     apksigner verify --verbose --print-certs app-release.apk
     ```
   * **方式 C (從本地 Gradle 簽章報告提取)**:
     ```bash
     ./gradlew signingReport
     ```

5. **建立 Android OAuth 2.0 Client ID**:
   * 進入 Google Cloud Console「憑證 (Credentials)」 > 「建立憑證」 > 「OAuth 用戶端 ID」。
   * **應用程式類型 (Application type)**: 選擇 **Android**。
   * **Package Name**: 必須填寫 `com.moneytracker.app` (與 APK 實際 package 完全一致)。
   * **SHA-1 憑證指紋**: 填入上述步驟取得之 **SHA-1 簽章憑證指紋** (格式如 `XX:XX:XX:...:XX`)。

6. **⚠️ 觀念釐清：APK 檔案校驗碼與簽章憑證指紋之差異**:
   * **簽章憑證指紋 (Signature Fingerprint, 如 SHA-1 / SHA-256)**：
     代表開發者/打包金鑰的數位證書身份，由簽署金鑰 (KeyStore) 決定。**Google Cloud Console 登記 OAuth 用戶端必須填寫簽章憑證 SHA-1**。同一 KeyStore 簽署的所有版本 APK 指紋皆完全相同。
   * **APK 檔案校驗碼 (File Checksum, 如 `app-*.apk.sha256`)**：
     對 APK 檔案二進位內容計算所得之 SHA-256 Checksum 雜湊值，用於驗證檔案下載完整性。每次重新編譯或調整檔案內容後均會改變，**絕對不能**當作 OAuth 的簽章 SHA-1 填寫。

> ⚠️ **重要說明 (真實帳號驗證狀態聲明)**:
> 未在 Google Cloud Console 上完成此 Android 包名 (`com.moneytracker.app`) 與 SHA-1 簽章指紋配置前，Google Play 服務會拒絕 OAuth 請求（通常拋出 `ApiException: 10 DEVELOPER_ERROR` 或 `12500`）。在缺乏實際 Google Cloud 憑證配置的環境下，**實機真實 Google 帳號登入列為待完成驗證**，本 App 已提供完整的錯誤碼揭露、診斷日誌及權限補全導引。

---

## 四、編譯與執行指令

### 執行單元測試 (Unit Tests)
在連結網際網路且能下載完整 Maven 依賴庫之環境下，執行以下指令：
```bash
./gradlew test
```

### 建置 Debug APK
在連結網際網路且能下載完整 Maven 依賴庫之環境下，執行以下指令：
```bash
./gradlew assembleDebug
```
產出的 APK 檔案將位於：
`app/build/outputs/apk/debug/app-debug.apk`

### 建置 Release APK (使用固定 KeyStore)
設定環境變數後執行：
```bash
KEYSTORE_PATH=release.keystore KEYSTORE_PASSWORD=your_pass KEY_ALIAS=your_alias KEY_PASSWORD=your_pass ./gradlew assembleRelease
```
產出的 APK 檔案將位於：
`app/build/outputs/apk/release/app-release.apk`

---

## 五、GitHub Actions CI/CD 與 APK 下載

本專案配置有 GitHub Actions 工作流程 `.github/workflows/build-apk.yml`：

### 1. 自動觸發機制
* **PR 自動驗證**: 當有 Pull Request 指向 `master` 分支時，自動觸發單元測試與 APK 建置。
* **合併/推送自動發布**: 當程式碼合併或推送到 `master` 時，先執行測試、用 Actions KeyStore 簽署正式 Release APK、驗證憑證指紋，再建立最新 GitHub Release。僅在新 Release 成功後刪除其他舊 Release 與其 Tag。
* **安全性**: PR 與其他分支不發布 Release；當建置落後於最新 master 時跳過發布，避免舊版覆蓋新版。

### 2. 手動觸發建置 (`workflow_dispatch`)
1. 前往專案的 GitHub Repository 頁面，點選 **Actions** 頁籤。
2. 在左側工作流程清單中選擇 **Build & Package Android APK**。
3. 點擊右側的 **Run workflow** 按鈕，選擇 `master` 分支並點擊確認執行。選擇 `master` 會執行正式發布；其他分支僅供 Debug 驗證。

### 3. 下載 APK Artifacts、校驗碼與簽章指紋報告
1. 進入任何一次執行的 Action Run 詳情頁面。
2. 檢視頁面頂部的 **Summary 摘要區塊**，直接檢視與複製 `com.moneytracker.app` 之 **SHA-1 簽章憑證指紋**、Package Name 與簽章模式。
3. 捲動至頁面底部的 **Artifacts** 區塊下載產物：
   * **`app-apk-<commit-sha>`**:
     * `app-<commit-sha>.apk`: 可直接安裝測試之 Android APK。
     * `app-<commit-sha>.apk.sha256`: APK 檔案下載完整性校驗碼 (File Checksum)。
     * `apk_signature_fingerprints.txt`: APK 簽章憑證 SHA-1 / SHA-256 指紋報告（提供給 Google Cloud Console 登記 OAuth 用）。
   * **`test-report-<commit-sha>`**: 包含單元測試報告（HTML / XML 格式）。
4. Artifacts 保留期限為 30 天。若單元測試、簽章驗證或憑證指紋擷取失敗，建置將直接標示失敗。

### 4. 最新 Release（推薦安裝來源）
* 固定下載網址：**[MoneyTracker 最新 Release](https://github.com/jimmy0933b3-sudo/money/releases/latest)**。
* 每次 `master` 推送或從 `master` 手動執行建置通過後，附上已使用 Actions KeyStore 簽署、可直接安裝的 APK、`.apk.sha256` 校驗檔與 `apk_signature_fingerprints.txt`。
* 僅保留**最近成功發布的一筆 GitHub Release**，舊 Release 與 Tag 會自動刪除。Actions 的歷次建置紀錄與 30 天 Artifacts 不受此規則影響。
* **注意**：如曾安裝使用不同簽章金鑰的 APK，即使 App 的套件名稱相同，也不能直接覆蓋更新。請確保每次 CI 都使用相同的 KeyStore；刪除舊 App 前先確認可還原備份。

---

## 六、預覽與畫面截圖

* **首頁 (Home)**: 收支總覽、帳戶餘額、待收/待還金額與最近交易紀錄。
* **記帳頁 (Transactions)**: 交易明細、月份/分類/關鍵字篩選與快速記帳數字鍵盤。
* **借貸頁 (Loans)**: 借出/借入/對象總覽 Tab，卡片式狀態顯示與分次還款對話框。
* **統計頁 (Statistics)**: 支出分類圓餅圖與收支長條圖比較。
* **設定頁 (Settings)**: Google Drive 連結、立即備份、雲端還原與分類/帳戶管理。
