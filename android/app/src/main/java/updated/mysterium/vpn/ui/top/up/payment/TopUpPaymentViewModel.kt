package updated.mysterium.vpn.ui.top.up.payment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import mysterium.RegisterIdentityRequest
import updated.mysterium.vpn.common.extensions.liveDataResult
import updated.mysterium.vpn.model.wallet.IdentityModel
import updated.mysterium.vpn.model.wallet.IdentityRegistrationStatus
import updated.mysterium.vpn.network.provider.usecase.UseCaseProvider

class TopUpPaymentViewModel(useCaseProvider: UseCaseProvider) : ViewModel() {

    private companion object {
        const val STATUS_PAID = "paid"
        const val STATUS_EXPIRED = "expired"
        const val STATUS_INVALID = "invalid"
        const val STATUS_CANCELED = "canceled"
        const val STATUS_REFUNDED = "refunded"
    }

    val paymentSuccessfully: LiveData<Unit>
        get() = _paymentSuccessfully

    val paymentExpired: LiveData<Unit>
        get() = _paymentExpired

    val paymentFailed: LiveData<Unit>
        get() = _paymentFailed

    val paymentCanceled: LiveData<Unit>
        get() = _paymentCanceled

    private val paymentUseCase = useCaseProvider.payment()
    private val connectionUseCase = useCaseProvider.connection()
    private val balanceUseCase = useCaseProvider.balance()
    private val pushyUseCase = useCaseProvider.pushy()
    private val _paymentSuccessfully = MutableLiveData<Unit>()
    private val _paymentExpired = MutableLiveData<Unit>()
    private val _paymentFailed = MutableLiveData<Unit>()
    private val _paymentCanceled = MutableLiveData<Unit>()
    private var orderId: Long? = null

    fun createPaymentOrder(
        currency: String,
        mystAmount: Double,
        isLighting: Boolean
    ) = liveDataResult {
        registerOrderCallback()
        val order = paymentUseCase.createPaymentOrder(
            currency,
            connectionUseCase.getIdentityAddress(),
            mystAmount,
            isLighting
        )
        orderId = order.id
        order
    }

    fun clearPopUpTopUpHistory() {
        balanceUseCase.clearBalancePopUpHistory()
        balanceUseCase.clearMinBalancePopUpHistory()
        balanceUseCase.clearBalancePushHistory()
        balanceUseCase.clearMinBalancePushHistory()
    }

    fun updateLastCurrency(currency: String) {
        pushyUseCase.updateCryptoCurrency(currency)
    }

    fun registerAccount() = liveDataResult {
        val identity = connectionUseCase.getIdentity()
        val identityModel = IdentityModel(identity)
        val req = RegisterIdentityRequest().apply {
            identityAddress = identityModel.address
            token?.let {
                this.token = it
            }
        }
        connectionUseCase.registerIdentity(req)
        connectionUseCase.registrationFees()
    }

    private suspend fun registerOrderCallback() {
        paymentUseCase.paymentOrderCallback {
            if (orderId.toString() == it.orderID) {
                when (it.status) {
                    STATUS_PAID -> _paymentSuccessfully.postValue(Unit)
                    STATUS_EXPIRED -> _paymentExpired.postValue(Unit)
                    STATUS_INVALID, STATUS_REFUNDED -> _paymentFailed.postValue(Unit)
                    STATUS_CANCELED -> _paymentCanceled.postValue(Unit)
                }
            }
        }
    }
}
