package com.loaderapp.ui.dispatcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loaderapp.data.model.Order
import com.loaderapp.data.model.OrderStatus
import com.loaderapp.data.model.User
import com.loaderapp.data.repository.AppRepository
import com.loaderapp.notification.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для DispatcherScreen из версии 9.4
 * Адаптирован для работы с Hilt и архитектурой 1.5
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class DispatcherViewModel @Inject constructor(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    private val notificationHelper = NotificationHelper(application)
    private var dispatcherId: Long = 0

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _workerCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val workerCounts: StateFlow<Map<Long, Int>> = _workerCounts.asStateFlow()

    // Статистика как StateFlow
    val completedCount: StateFlow<Int> get() = _completedCount.asStateFlow()
    private val _completedCount = MutableStateFlow(0)

    val activeCount: StateFlow<Int> get() = _activeCount.asStateFlow()
    private val _activeCount = MutableStateFlow(0)

    fun initialize(dispatcherId: Long) {
        this.dispatcherId = dispatcherId
        observeOrdersWithSearch()
        loadCurrentUser()
        loadStats()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            repository.getUserByIdFlow(dispatcherId).collect { _currentUser.value = it }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            repository.getDispatcherCompletedCount(dispatcherId).collect { count ->
                _completedCount.value = count
            }
        }
        viewModelScope.launch {
            repository.getDispatcherActiveCount(dispatcherId).collect { count ->
                _activeCount.value = count
            }
        }
    }

    private fun observeOrdersWithSearch() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _searchQuery.debounce(300).flatMapLatest { query ->
                    if (query.isBlank()) repository.getOrdersByDispatcher(dispatcherId)
                    else repository.searchOrdersByDispatcher(dispatcherId, query)
                }.collect { orders ->
                    _orders.value = orders
                    _isLoading.value = false
                    val counts = mutableMapOf<Long, Int>()
                    orders.forEach { counts[it.id] = repository.getWorkerCountSync(it.id) }
                    _workerCounts.value = counts
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка загрузки заказов: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) _searchQuery.value = ""
    }

    suspend fun createOrder(order: Order): Boolean {
        return try {
            _isLoading.value = true
            repository.createOrder(order)
            _snackbarMessage.value = "✅ Заказ создан!"
            true
        } catch (e: Exception) {
            _errorMessage.value = "Ошибка создания заказа: ${e.message}"
            false
        } finally {
            _isLoading.value = false
        }
    }

    fun updateOrderStatus(orderId: Long, status: OrderStatus) {
        viewModelScope.launch {
            try {
                val order = repository.getOrderById(orderId)
                if (order != null) {
                    repository.updateOrder(order.copy(status = status))
                    _snackbarMessage.value = "✅ Статус обновлён"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка обновления статуса: ${e.message}"
            }
        }
    }

    fun cancelOrder(orderId: Long) {
        viewModelScope.launch {
            try {
                repository.cancelOrder(orderId)
                _snackbarMessage.value = "❌ Заказ отменён"
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка отмены заказа: ${e.message}"
            }
        }
    }

    suspend fun getUserById(id: Long): User? = repository.getUserById(id)

    suspend fun getLoaders(): List<User> = repository.getLoaders()

    fun clearSnackbar() { _snackbarMessage.value = null }
    fun clearError() { _errorMessage.value = null }
}
