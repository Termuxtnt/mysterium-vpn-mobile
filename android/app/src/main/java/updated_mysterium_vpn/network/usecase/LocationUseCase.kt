package updated_mysterium_vpn.network.usecase

import network.mysterium.service.core.NodeRepository

class LocationUseCase(private val nodeRepository: NodeRepository) {

    suspend fun getLocation() = nodeRepository.location()
}
