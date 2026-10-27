package com.moneytracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneytracker.app.data.entity.TransactionEntity
import com.moneytracker.app.data.model.AccountType
import com.moneytracker.app.data.model.TransactionType
import com.moneytracker.app.data.repository.AccountWithBalance
import com.moneytracker.app.ui.theme.BorrowPurple
import com.moneytracker.app.ui.theme.BrandDeep
import com.moneytracker.app.ui.theme.BrandJade
import com.moneytracker.app.ui.theme.ExpenseRed
import com.moneytracker.app.ui.theme.IncomeGreen
import com.moneytracker.app.ui.theme.LendAmber
import com.moneytracker.app.ui.theme.TransferBlue
import com.moneytracker.app.ui.viewmodel.MainViewModel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun Long.toFormattedString(): String = DecimalFormat("#,###").format(this)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenTransactions: () -> Unit = {},
    onOpenLoans: () -> Unit = {}
) {
    val monthlyIncome by viewModel.monthlyIncome.collectAsState()
    val monthlyExpense by viewModel.monthlyExpense.collectAsState()
    val accountBalance by viewModel.totalAccountBalance.collectAsState()
    val accountsWithBalance by viewModel.accountsWithBalance.collectAsState()
    val outstandingLend by viewModel.outstandingLendTotal.collectAsState()
    val outstandingBorrow by viewModel.outstandingBorrowTotal.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val loansWithDetails by viewModel.loansWithDetails.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    val monthLabel = remember { SimpleDateFormat("yyyy 年 M 月", Locale.TAIWAN).format(Date()) }
    val currentMonthRange = remember {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val first = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        first to calendar.timeInMillis
    }
    val spendingByCategory = remember(transactions, categories) {
        val names = categories.associate { it.id to it.name }
        transactions.asSequence()
            .filter {
                it.type == TransactionType.EXPENSE &&
                    it.loanId == null && it.repaymentId == null &&
                    it.dateMillis >= currentMonthRange.first &&
                    it.dateMillis < currentMonthRange.second
            }
            .groupBy { it.categoryId }
            .map { (categoryId, group) ->
                (names[categoryId] ?: "未分類") to group.sumOf { it.amount }
            }
            .sortedByDescending { it.second }
            .take(4)
    }
    val recentTransactions = remember(transactions) {
        transactions.sortedByDescending { it.dateMillis }.take(5)
    }
    val overdueCount = loansWithDetails.count {
        it.loan.dueDateMillis?.let { date -> date < System.currentTimeMillis() } == true &&
            it.remainingAmount > 0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 22.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "財務總覽",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        monthLabel + " · 每一天都把金流看得更清楚",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    BalanceHeroCard(
                        totalBalance = accountBalance,
                        monthlyIncome = monthlyIncome,
                        monthlyExpense = monthlyExpense
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    HomeSectionTitle("我的帳戶", "各帳戶目前餘額")
                }
                Spacer(Modifier.height(12.dp))
                if (accountsWithBalance.isEmpty()) {
                    HomeEmptyPanel(
                        "還沒有帳戶資料",
                        "新增帳戶後，就能在這裡快速掌握餘額。",
                        Modifier.padding(horizontal = 20.dp)
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(accountsWithBalance, key = { it.account.id }) { account ->
                            AccountBalanceCard(account)
                        }
                    }
                }
            }

            if (overdueCount > 0) {
                item {
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .clickable(onClick = onOpenLoans),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("借貸到期提醒", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text(
                                    "有 " + overdueCount + " 筆借貸已超過預計還款日",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    HomeSectionTitle("借貸進度", "待收與待還，一眼看懂", onAction = onOpenLoans)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LoanSummaryCard(
                            title = "待收回",
                            amount = outstandingLend,
                            tint = LendAmber,
                            modifier = Modifier.weight(1f)
                        )
                        LoanSummaryCard(
                            title = "待償還",
                            amount = outstandingBorrow,
                            tint = BorrowPurple,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    HomeSectionTitle("本月支出排行", "錢都花在哪裡？")
                    Spacer(Modifier.height(12.dp))
                    if (spendingByCategory.isEmpty()) {
                        HomeEmptyPanel("還沒有本月支出", "記下第一筆支出，就會顯示分類排行。")
                    } else {
                        val maxSpend = spendingByCategory.first().second.coerceAtLeast(1L)
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                spendingByCategory.forEachIndexed { index, (name, amount) ->
                                    ExpenseProgressRow(
                                        title = name,
                                        amount = amount,
                                        fraction = (amount.toFloat() / maxSpend.toFloat()).coerceIn(0f, 1f),
                                        tint = listOf(IncomeGreen, TransferBlue, LendAmber, BorrowPurple)[index]
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    HomeSectionTitle("最近交易", "最近 5 筆收支紀錄", onAction = onOpenTransactions)
                    Spacer(Modifier.height(12.dp))
                    if (recentTransactions.isEmpty()) {
                        HomeEmptyPanel("開始記下第一筆收支", "點擊右下角的「快速記帳」就能開始。")
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                recentTransactions.forEachIndexed { index, transaction ->
                                    TransactionPreviewRow(transaction, onOpenTransactions)
                                    if (index != recentTransactions.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 18.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("快速記帳", fontWeight = FontWeight.Bold) }
        )
    }

    if (showAddDialog) {
        AddTransactionDialog(viewModel = viewModel, onDismiss = { showAddDialog = false })
    }
}

@Composable
private fun BalanceHeroCard(totalBalance: Long, monthlyIncome: Long, monthlyExpense: Long) {
    val net = monthlyIncome - monthlyExpense
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(BrandDeep, BrandJade)))
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).background(Color.White.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("目前帳戶總餘額", color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "NT$ " + totalBalance.toFormattedString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = if (totalBalance.toFormattedString().length > 12) 25.sp else 33.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "本月淨收支  " + (if (net >= 0L) "+" else "") + "NT$ " + net.toFormattedString(),
                color = if (net >= 0L) Color(0xFFB7F0D4) else Color(0xFFFFD0C7),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroMetric("本月收入", monthlyIncome, Icons.Default.ArrowDownward, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(45.dp).background(Color.White.copy(alpha = 0.22f)))
                HeroMetric("本月支出", monthlyExpense, Icons.Default.ArrowUpward, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, amount: Long, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "NT$ " + amount.toFormattedString(),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeSectionTitle(title: String, subtitle: String, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onAction != null) {
            TextButton(onClick = onAction) {
                Text("查看全部")
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AccountBalanceCard(accountBalance: AccountWithBalance) {
    val account = accountBalance.account
    val icon = when (account.type) {
        AccountType.CASH -> Icons.Default.Payments
        AccountType.BANK -> Icons.Default.AccountBalance
        AccountType.E_PAYMENT -> Icons.Default.AccountBalanceWallet
        AccountType.CREDIT_CARD -> Icons.Default.CreditCard
    }
    Surface(
        modifier = Modifier.width(184.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                account.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "NT$ " + accountBalance.currentBalance.toFormattedString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LoanSummaryCard(title: String, amount: Long, tint: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(8.dp).background(tint, CircleShape))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(5.dp))
            Text(
                "NT$ " + amount.toFormattedString(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpenseProgressRow(title: String, amount: Long, fraction: Float, tint: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.dp))
            Text("NT$ " + amount.toFormattedString(), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(7.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier.fillMaxWidth(fraction).fillMaxHeight()
                    .background(tint, RoundedCornerShape(100.dp))
            )
        }
    }
}

@Composable
private fun TransactionPreviewRow(tx: TransactionEntity, onClick: () -> Unit) {
    val tint = when (tx.type) {
        TransactionType.INCOME -> IncomeGreen
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.TRANSFER -> TransferBlue
    }
    val icon = when (tx.type) {
        TransactionType.INCOME -> Icons.Default.ArrowDownward
        TransactionType.EXPENSE -> Icons.Default.ArrowUpward
        TransactionType.TRANSFER -> Icons.Default.SwapHoriz
    }
    val sign = when (tx.type) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE -> "-"
        TransactionType.TRANSFER -> ""
    }
    val dateLabel = remember(tx.dateMillis) {
        SimpleDateFormat("M/d HH:mm", Locale.TAIWAN).format(Date(tx.dateMillis))
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (tx.note.isNotBlank()) tx.note else tx.type.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                tx.type.label + " · " + dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            sign + "NT$ " + tx.amount.toFormattedString(),
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeEmptyPanel(title: String, message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ReceiptLong, null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                modifier = Modifier.size(29.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(5.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
