package updated.mysterium.vpn.core

import android.util.Log
import com.beust.klaxon.Klaxon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mysterium.*
import updated.mysterium.vpn.exceptions.ConnectAlreadyExistsException
import updated.mysterium.vpn.exceptions.ConnectInsufficientBalanceException
import updated.mysterium.vpn.exceptions.ConnectInvalidProposalException
import updated.mysterium.vpn.exceptions.ConnectUnknownException
import updated.mysterium.vpn.model.connection.Status
import updated.mysterium.vpn.model.nodes.ProposalItem
import updated.mysterium.vpn.model.nodes.ProposalsResponse
import updated.mysterium.vpn.model.payment.Order
import updated.mysterium.vpn.model.statistics.Location
import updated.mysterium.vpn.model.statistics.Statistics
import updated.mysterium.vpn.model.wallet.Identity
import updated.mysterium.vpn.model.wallet.IdentityRegistrationFees
import java.math.RoundingMode.*

// Wrapper around Go mobile node library bindings. It should not change any result
// returned from internal mobile node and instead all mappings should happen in
// ViewModels.
class NodeRepository(var deferredNode: DeferredNode) {

    private companion object {
        const val TAG = "NodeRepository"
    }

    // Get available proposals for mobile. Internally on Go side
    // proposals are fetched once and cached but it is possible to refresh cache by
    // passing refresh flag.
    //
    // Note that this method need to deserialize JSON byte array since Go Mobile
    // does not support passing complex slices via it's bridge.
    suspend fun proposals(req: GetProposalsRequest): List<ProposalItem> = withContext(Dispatchers.IO) {
        val bytes = getProposals(req)
        val proposalsResponse = parseProposals(bytes)
        if (proposalsResponse?.proposals == null) {
            listOf()
        } else {
            proposalsResponse.proposals
        }
    }

    // Register connection status callback.
    suspend fun registerConnectionStatusChangeCallback(cb: (status: String) -> Unit) {
        withContext(Dispatchers.IO) {
            deferredNode.await().registerConnectionStatusChangeCallback { status -> cb(status) }
        }
    }

    // Register statistics callback.
    suspend fun registerStatisticsChangeCallback(cb: (stats: Statistics) -> Unit) {
        withContext(Dispatchers.IO) {
            deferredNode.await().registerStatisticsChangeCallback { duration, bytesReceived, bytesSent, tokensSpent ->
                cb(Statistics(duration, bytesReceived, bytesSent, tokensSpent))
            }
        }
    }

    // Register statistics callback.
    suspend fun registerBalanceChangeCallback(cb: (balance: Double) -> Unit) {
        withContext(Dispatchers.IO) {
            deferredNode.await().registerBalanceChangeCallback { _, balance ->
                cb(balance)
            }
        }
    }

    suspend fun registerOrderUpdatedCallback(cb: (payload: OrderUpdatedCallbackPayload) -> Unit) {
        withContext(Dispatchers.IO) {
            deferredNode.await().registerOrderUpdatedCallback(cb)
        }
    }

    // Connect to VPN service.
    suspend fun connect(req: ConnectRequest) = withContext(Dispatchers.IO) {
        val res = deferredNode.await().connect(req) ?: return@withContext

        Log.e(TAG, res.errorMessage)
        when (res.errorCode) {
            "InvalidProposal" -> throw ConnectInvalidProposalException(res.errorMessage)
            "InsufficientBalance" -> throw ConnectInsufficientBalanceException(res.errorMessage)
            "Unknown" -> {
                if (res.errorMessage == "connection already exists") {
                    throw ConnectAlreadyExistsException(res.errorMessage)
                } else {
                    throw ConnectUnknownException(res.errorMessage)
                }
            }
        }
    }

    suspend fun reconnect(req: ConnectRequest) = withContext(Dispatchers.IO) {
        val res = deferredNode.await().reconnect(req) ?: return@withContext

        when (res.errorCode) {
            "InvalidProposal" -> throw ConnectInvalidProposalException(res.errorMessage)
            "InsufficientBalance" -> throw ConnectInsufficientBalanceException(res.errorMessage)
            "Unknown" -> {
                if (res.errorMessage == "connection already exists") {
                    throw ConnectAlreadyExistsException(res.errorMessage)
                } else {
                    throw ConnectUnknownException(res.errorMessage)
                }
            }
        }
    }

    // Disconnect from VPN service.
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        deferredNode.await().disconnect()
    }

    // Unlock identity and return it's address. Internally mobile node will create default identity
    // if it is not created yet.
    suspend fun getIdentity(
        getIdentityRequest: GetIdentityRequest = GetIdentityRequest()
    ): Identity = withContext(Dispatchers.IO) {
        val res = deferredNode.await().getIdentity(getIdentityRequest)
        Identity(address = res.identityAddress, channelAddress = res.channelAddress, registrationStatus = res.registrationStatus)
    }

    // Get registration fees.
    suspend fun identityRegistrationFees() = withContext(Dispatchers.IO) {
        val res = deferredNode.await().identityRegistrationFees
        IdentityRegistrationFees(fee = res.fee)
    }

    suspend fun createPaymentOrder(req: CreateOrderRequest) = withContext(Dispatchers.IO) {
        val order = deferredNode.await().createOrder(req).decodeToString()
        Log.d(TAG, "createPaymentOrder response: $order")
        Order.fromJSON(order) ?: error("Could not parse JSON: $order")
    }

    suspend fun listOrders(req: ListOrdersRequest) = withContext(Dispatchers.IO) {
        val orders = deferredNode.await().listOrders(req)
        Order.listFromJSON(orders.decodeToString()) ?: error("Could not parse JSON: $orders")
    }

    // Register identity with given fee.
    suspend fun registerIdentity(req: RegisterIdentityRequest) = withContext(Dispatchers.IO) {
        deferredNode.await().registerIdentity(req)
    }

    // Get current location with country and IP.
    suspend fun location() = withContext(Dispatchers.IO) {
        val res = deferredNode.await().location
        Location(
            ip = res.ip,
            countryCode = res.country
        )
    }

    // Get current  connection status.
    suspend fun status() = withContext(Dispatchers.IO) {
        val res = deferredNode.await().status
        Status(
            state = res.state,
            providerID = res.providerID,
            serviceType = res.serviceType
        )
    }

    // Get current balance.
    suspend fun balance(req: GetBalanceRequest) = withContext(Dispatchers.IO) {
        val res = deferredNode.await().getBalance(req)
        res.balance
    }

    // Send user feedback.
    suspend fun sendFeedback(req: SendFeedbackRequest) = withContext(Dispatchers.IO) {
        deferredNode.await().sendFeedback(req)
    }

    suspend fun getExchangeRate(currency: String) = withContext(Dispatchers.IO) {
        deferredNode.await().exchangeRate(currency)
    }

    suspend fun getLastSessions(sessionFilter: SessionFilter): ByteArray = withContext(Dispatchers.IO) {
        deferredNode.await().listConsumerSessions(sessionFilter)
    }

    suspend fun downloadPrivateKey(
        identityAddress: String, newPassphrase: String
    ): ByteArray = withContext(Dispatchers.IO) {
        deferredNode.await().exportIdentity(identityAddress, newPassphrase)
    }

    suspend fun exportIdentity(
        address: String, newPassphrase: String
    ): ByteArray = withContext(Dispatchers.IO) {
        deferredNode.await().exportIdentity(address, newPassphrase)
    }

    suspend fun importIdentity(
        privateKey: ByteArray, passphrase: String
    ): String = withContext(Dispatchers.IO) {
        deferredNode.await().importIdentity(privateKey, passphrase)
    }

    suspend fun getWalletEquivalent(balance: Double): Estimates = withContext(Dispatchers.IO) {
        deferredNode.await().calculateEstimates(balance)
    }

    suspend fun getResidentCountry(): String = withContext(Dispatchers.IO) {
        deferredNode.await().residentCountry()
    }

    suspend fun saveResidentCountry(
        residentCountryUpdateRequest: ResidentCountryUpdateRequest
    ) = withContext(Dispatchers.IO) {
        deferredNode.await().updateResidentCountry(residentCountryUpdateRequest)
    }

    suspend fun getFilterPresets(): ByteArray = withContext(Dispatchers.IO) {
        deferredNode.await().listProposalFilterPresets()
    }

    suspend fun getProposalsByFilterId(getProposalRequest: GetProposalsRequest) = withContext(Dispatchers.IO) {
        val bytesProposals = deferredNode.await().getProposals(getProposalRequest)
        val proposalsResponse = parseProposals(bytesProposals)
        if (proposalsResponse?.proposals == null) {
            listOf()
        } else {
            proposalsResponse.proposals
        }
    }

    suspend fun getRegistrationTokenReward(registrationToken: String) = withContext(Dispatchers.IO) {
        deferredNode.await().registrationTokenReward(registrationToken)
    }

    suspend fun isFreeRegistrationEligible(address: String) = withContext(Dispatchers.IO) {
        deferredNode.await().isFreeRegistrationEligible(address)
    }

    suspend fun forceBalanceUpdate(req: GetBalanceRequest) = withContext(Dispatchers.IO) {
        deferredNode.await().forceBalanceUpdate(req)
    }

    private suspend fun getProposals(req: GetProposalsRequest) = withContext(Dispatchers.IO) {
        deferredNode.await().getProposals(req)
    }

    private suspend fun parseProposals(bytes: ByteArray) = withContext(Dispatchers.Default) {
        Klaxon().parse<ProposalsResponse>(bytes.inputStream())
    }
}
