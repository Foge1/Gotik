package com.loaderapp.ui.dispatcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loaderapp.LoaderApplication
import com.loaderapp.core.common.UiState
import com.loaderapp.data.model.Order
import com.loaderapp.domain.model.OrderModel
import com.loaderapp.domain.model.OrderStatusModel
import com.loaderapp.domain.usecase.order.DispatcherStats
import com.loaderapp.presentation.dispatcher.DispatcherViewModel
import com.loaderapp.ui.history.HistoryScreen
import com.loaderapp.ui.profile.ProfileScreen
import com.loaderapp.ui.rating.RatingScreen
import com.loaderapp.ui.settings.SettingsScreen
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private enum class DispatcherTab { AVAILABLE, IN_WORK }
private enum class BottomNavSection { ORDERS, HISTORY, RATING, PROFILE, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun DispatcherScreen(
    viewModel: DispatcherViewModel,
    onSwitchRole: () -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit,
    onOrderClick: (Long) -> Unit
) {
    val ordersState by viewModel.ordersState.collectAsState()
    val statsState by viewModel.statsState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(DispatcherTab.AVAILABLE) }
    var selectedSection by remember { mutableStateOf(BottomNavSection.ORDERS) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Данные для профиля/истории/рейтинга
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as LoaderApplication }
    var currentUser by remember { mutableStateOf<com.loaderapp.data.model.User?>(null) }
    var historyOrders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var completedCount by remember { mutableStateOf(0) }
    var activeCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        val userId = app.userPreferences.getCurrentUserId() ?: return@LaunchedEffect
        currentUser = app.repository.getUserById(userId)
        app.repository.getOrdersByDispatcher(userId).collect { orders ->
            historyOrders = orders
        }
    }

    LaunchedEffect(statsState) {
        if (statsState is UiState.Success) {
            val stats = (statsState as UiState.Success<DispatcherStats>).data
            completedCount = stats.completedOrders
            activeCount = stats.activeOrders
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main content area
            Box(modifier = Modifier.weight(1f)) {
                when (selectedSection) {
                    BottomNavSection.ORDERS -> {
                        OrdersSection(
                            ordersState = ordersState,
                            selectedTab = selectedTab,
                            onTabChange = { selectedTab = it },
                            isSearchActive = isSearchActive,
                            searchQuery = searchQuery,
                            onSearchActiveChange = { viewModel.setSearchActive(it) },
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                            onOrderClick = onOrderClick,
                            onCancelOrder = { viewModel.cancelOrder(it) },
                            onCreateClick = { showCreateDialog = true }
                        )
                    }
                    BottomNavSection.HISTORY -> {
                        HistoryScreen(
                            orders = historyOrders,
                            onMenuClick = {},
                            onBackClick = { selectedSection = BottomNavSection.ORDERS }
                        )
                    }
                    BottomNavSection.RATING -> {
                        RatingScreen(
                            userName = currentUser?.name ?: "",
                            userRating = currentUser?.rating ?: 0.0,
                            onMenuClick = {},
                            onBackClick = { selectedSection = BottomNavSection.ORDERS },
                            dispatcherCompletedCount = completedCount,
                            dispatcherActiveCount = activeCount,
                            isDispatcher = true
                        )
                    }
                    BottomNavSection.PROFILE -> {
                        currentUser?.let { user ->
                            ProfileScreen(
                                user = user,
                                dispatcherCompletedCount = completedCount,
                                dispatcherActiveCount = activeCount,
                                onMenuClick = {},
                                onSaveProfile = { name, phone, birthDate ->
                                    scope.launch {
                                        val updated = user.copy(
                                            name = name,
                                            phone = phone,
                                            birthDate = birthDate
                                        )
                                        app.repository.updateUser(updated)
                                        currentUser = updated
                                    }
                                },
                                onSwitchRole = onSwitchRole
                            )
                        }
                    }
                    BottomNavSection.SETTINGS -> {
                        SettingsScreen(
                            onMenuClick = {},
                            onBackClick = { selectedSection = BottomNavSection.ORDERS },
                            onDarkThemeChanged = onDarkThemeChanged,
                            onSwitchRole = onSwitchRole
                        )
                    }
                }
            }

            // Bottom Navigation Bar
            DispatcherBottomBar(
                selectedSection = selectedSection,
                onSectionSelected = { selectedSection = it }
            )
        }
    }

    if (showCreateDialog) {
        CreateOrderDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { order ->
                viewModel.createOrder(order) {
                    showCreateDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrdersSection(
    ordersState: UiState<List<OrderModel>>,
    selectedTab: DispatcherTab,
    onTabChange: (DispatcherTab) -> Unit,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOrderClick: (Long) -> Unit,
    onCancelOrder: (OrderModel) -> Unit,
    onCreateClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        if (isSearchActive) {
            val focusRequester = remember { FocusRequester() }
            val focusManager = LocalFocusManager.current

            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onSearch = { focusManager.clearFocus() },
                active = true,
                onActiveChange = {
                    if (!it) {
                        onSearchActiveChange(false)
                        onSearchQueryChange("")
                    }
                },
                placeholder = { Text("Поиск по адресу, грузу...") },
                leadingIcon = {
                    IconButton(onClick = {
                        onSearchActiveChange(false)
                        onSearchQueryChange("")
                    }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Очистить")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            ) {}

            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Панель диспетчера",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Поиск",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Tab switcher
        TabSwitcher(
            selectedTab = selectedTab,
            onTabChange = onTabChange,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // Orders list
        val filteredOrders = when (ordersState) {
            is UiState.Success -> when (selectedTab) {
                DispatcherTab.AVAILABLE -> ordersState.data.filter {
                    it.status == OrderStatusModel.AVAILABLE
                }
                DispatcherTab.IN_WORK -> ordersState.data.filter {
                    it.status == OrderStatusModel.TAKEN || it.status == OrderStatusModel.IN_PROGRESS
                }
            }
            else -> emptyList()
        }

        Box(modifier = Modifier.weight(1f)) {
            when (ordersState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is UiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = ordersState.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                else -> {
                    if (filteredOrders.isEmpty()) {
                        // Empty state — точно как на скрине
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(55.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inbox,
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = when (selectedTab) {
                                    DispatcherTab.AVAILABLE -> "Нет свободных заказов"
                                    DispatcherTab.IN_WORK -> "Нет заказов в работе"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Создайте первый заказ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredOrders, key = { it.id }) { order ->
                                DispatcherOrderCard(
                                    order = order,
                                    onClick = { onOrderClick(order.id) },
                                    onCancel = { onCancelOrder(order) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // FAB — "Создать заказ"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 20.dp, bottom = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onCreateClick,
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Создать заказ",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TabSwitcher(
    selectedTab: DispatcherTab,
    onTabChange: (DispatcherTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            TabButton(
                text = "Свободные",
                isSelected = selectedTab == DispatcherTab.AVAILABLE,
                onClick = { onTabChange(DispatcherTab.AVAILABLE) },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                text = "В работе",
                isSelected = selectedTab == DispatcherTab.IN_WORK,
                onClick = { onTabChange(DispatcherTab.IN_WORK) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else Color.Transparent,
        animationSpec = tween(200),
        label = "tabBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "tabText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(46.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun DispatcherBottomBar(
    selectedSection: BottomNavSection,
    onSectionSelected: (BottomNavSection) -> Unit
) {
    data class NavItem(val section: BottomNavSection, val icon: ImageVector, val label: String)

    val items = listOf(
        NavItem(BottomNavSection.ORDERS, Icons.Default.Assignment, "Заказы"),
        NavItem(BottomNavSection.HISTORY, Icons.Default.History, "История"),
        NavItem(BottomNavSection.RATING, Icons.Default.Star, "Рейтинг"),
        NavItem(BottomNavSection.PROFILE, Icons.Default.Person, "Профиль"),
        NavItem(BottomNavSection.SETTINGS, Icons.Default.Settings, "Настройки"),
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            items.forEach { item ->
                val isSelected = selectedSection == item.section
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "scale"
                )
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    label = "iconColor"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    label = "textColor"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSectionSelected(item.section) }
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    // Индикатор сверху
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .scale(scale)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = item.label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun DispatcherOrderCard(
    order: OrderModel,
    onClick: () -> Unit,
    onCancel: () -> Unit
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.address,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                OrderStatusChip(status = order.status)
            }

            Text(
                text = order.cargoDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.CalendarToday, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = SimpleDateFormat("dd MMM, HH:mm", Locale("ru")).format(Date(order.dateTime)),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OrderParamChip(Icons.Default.Person, "${order.requiredWorkers} чел")
                OrderParamChip(Icons.Default.Schedule, "${order.estimatedHours} ч")
                OrderParamChip(Icons.Default.AttachMoney, "${order.pricePerHour.toInt()}₽/ч")
                OrderParamChip(Icons.Default.Star, "≥${order.minWorkerRating}")
            }

            if (order.status == OrderStatusModel.AVAILABLE || order.status == OrderStatusModel.TAKEN) {
                OutlinedButton(
                    onClick = { showCancelDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Отменить заказ")
                }
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Отменить заказ?") },
            text = { Text("Вы уверены что хотите отменить этот заказ?") },
            confirmButton = {
                TextButton(
                    onClick = { onCancel(); showCancelDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Отменить заказ") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Назад") }
            }
        )
    }
}

@Composable
private fun OrderStatusChip(status: OrderStatusModel) {
    val (text, color) = when (status) {
        OrderStatusModel.AVAILABLE -> "Доступен" to Color(0xFF4CAF50)
        OrderStatusModel.TAKEN -> "Взят" to Color(0xFFFF9800)
        OrderStatusModel.IN_PROGRESS -> "В работе" to Color(0xFF2196F3)
        OrderStatusModel.COMPLETED -> "Завершён" to Color(0xFF9C27B0)
        OrderStatusModel.CANCELLED -> "Отменён" to Color(0xFFF44336)
    }
    Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OrderParamChip(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
