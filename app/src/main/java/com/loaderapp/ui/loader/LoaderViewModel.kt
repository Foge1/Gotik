package com.loaderapp.ui.loader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loaderapp.data.model.Order
import com.loaderapp.data.model.OrderStatus
import com.loaderapp.data.model.User
import com.loaderapp.data.repository.AppRepository
import com.loaderapp.notification.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для LoaderScreen из версии 9.4
 * Адаптирован для работы с Hilt и архитектурой 1.5
 */
@HiltViewModel
class LoaderViewModel @Inject constructor(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    private val notificationHelper = NotificationHelper(application)
    private var loaderId: Long = 0

    private val _availableOrders = MutableStateFlow<List<Order>>(emptyList())
    val availableOrders: StateFlow<List<Order>> = _availableOrders.asStateFlow()

    private val _myOrders = MutableStateFlow<List<Order>>(emptyList())
    val myOrders: StateFlow<List<Order>> = _myOrders.asStateFlow()

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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Статистика как Flow
    val completedCount: StateFlow<Int> get() = _completedCount.asStateFlow()
    private val _completedCount = MutableStateFlow(0)

    val totalEarnings: StateFlow<Double?> get() = _totalEarnings.asStateFlow()
    private val _totalEarnings = MutableStateFlow<Double?>(null)

    val averageRating: StateFlow<Float?> get() = _averageRating.asStateFlow()
    private val _averageRating = MutableStateFlow<Float?>(null)

    fun initialize(loaderId: Long) {
        this.loaderId = loaderId
        loadAvailableOrders()
        loadMyOrders()
        loadCurrentUser()
        loadStats()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            repository.getUserByIdFlow(loaderId).collect { user ->
                _currentUser.value = user
            }
        }
    }

    private fun loadAvailableOrders() {
        viewModelScope.launch {
            try {
                combine(
                    repository.getAvailableOrders(),
                    _currentUser
                ) { orders, user ->
                    val myRating = user?.rating?.toFloat() ?: 5f
                    orders.filter { it.minWorkerRating <= myRating }
                }.collect { filtered ->
                    _availableOrders.value = filtered
                    updateWorkerCounts(filtered)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка загрузки заказов: ${e.message}"
            }
        }
    }

    private fun loadMyOrders() {
        viewModelScope.launch {
            try {
                repository.getOrdersByWorker(loaderId).collect { directOrders ->
                    mergeMyOrders(directOrders)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка загрузки моих заказов: ${e.message}"
            }
        }
        viewModelScope.launch {
            try {
                repository.getOrderIdsByWorker(loaderId).collect { workerOrderIds ->
                    val directOrders = _myOrders.value
                    val directIds = directOrders.map { it.id }.toSet()
                    val extraIds = workerOrderIds.filter { it !in directIds }
                    val extraOrders = extraIds.mapNotNull { repository.getOrderById(it) }
                    mergeMyOrders(directOrders + extraOrders)
                }
            } catch (e: Exception) {
                // не критично
            }
        }
    }

    private suspend fun mergeMyOrders(orders: List<Order>) {
        val result = orders
            .filter { it.status == OrderStatus.TAKEN || it.status == OrderStatus.COMPLETED }
            .sortedByDescending { it.dateTime }
        _myOrders.value = result
        updateWorkerCounts(result)
    }

    private suspend fun updateWorkerCounts(orders: List<Order>) {
        val counts = mutableMapOf<Long, Int>()
        orders.forEach { order ->
            counts[order.id] = repository.getWorkerCountSync(order.id)
        }
        _workerCounts.value = counts
    }

    private fun loadStats() {
        viewModelScope.launch {
            repository.getCompletedOrdersCount(loaderId).collect { count ->
                _completedCount.value = count
            }
        }
        viewModelScope.launch {
            repository.getTotalEarnings(loaderId).collect { earnings ->
                _totalEarnings.value = earnings
            }
        }
        viewModelScope.launch {
            repository.getAverageRating(loaderId).collect { rating ->
                _averageRating.value = rating
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            updateWorkerCounts(_availableOrders.value + _myOrders.value)
            kotlinx.coroutines.delay(600)
            _isRefreshing.value = false
        }
    }

    suspend fun getUserById(id: Long): User? = repository.getUserById(id)

    fun takeOrder(order: Order) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val current = repository.getOrderById(order.id)
                if (current != null && current.status == OrderStatus.AVAILABLE) {
                    val alreadyTaken = repository.hasWorkerTakenOrder(order.id, loaderId)
                    if (alreadyTaken) {
                        _snackbarMessage.value = "⚠️ Вы уже взяли этот заказ"
                        return@launch
                    }
                    repository.takeOrder(order.id, loaderId)
                    val loader = repository.getUserById(loaderId)
                    if (loader != null) notificationHelper.sendOrderTakenNotification(order.address, loader.name)
                    val newCount = repository.getWorkerCountSync(order.id)
                    _workerCounts.value = _workerCounts.value + (order.id to newCount)
                    _snackbarMessage.value = "✅ Заказ взят!"
                } else {
                    _snackbarMessage.value = "⚠️ Заказ больше недоступен"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка взятия заказа: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun completeOrder(order: Order) {
        viewModelScope.launch {
            try {
                repository.completeOrder(order.id)
                _snackbarMessage.value = "🎉 Заказ завершён!"
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка завершения заказа: ${e.message}"
            }
        }
    }

    fun rateOrder(orderId: Long, rating: Float) {
        viewModelScope.launch {
            try {
                repository.rateOrder(orderId, rating)
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка выставления оценки: ${e.message}"
            }
        }
    }

    fun saveProfile(name: String, phone: String, birthDate: Long?) {
        viewModelScope.launch {
            try {
                val user = repository.getUserById(loaderId) ?: return@launch
                repository.updateUser(user.copy(name = name, phone = phone, birthDate = birthDate))
                _snackbarMessage.value = "✅ Профиль сохранён"
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка сохранения: ${e.message}"
            }
        }
    }

    fun clearSnackbar() { _snackbarMessage.value = null }
    fun clearError() { _errorMessage.value = null }
}
