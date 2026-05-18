package com.ssafy.mobile.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class NetworkMonitor
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val isOnline: Flow<Boolean> =
            callbackFlow {
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            // 실제 인터넷 사용 가능 여부는 onCapabilitiesChanged에서 검증합니다.
                        }

                        override fun onLost(network: Network) {
                            trySend(connectivityManager.isCurrentlyOnline())
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            trySend(connectivityManager.isCurrentlyOnline())
                        }
                    }

                connectivityManager.registerNetworkCallback(
                    NetworkRequest
                        .Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    callback,
                )

                trySend(connectivityManager.isCurrentlyOnline())

                awaitClose {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            }.distinctUntilChanged()
                .conflate()

        private fun ConnectivityManager.isCurrentlyOnline(): Boolean =
            activeNetwork
                ?.let(::getNetworkCapabilities)
                ?.isValidatedInternet() == true

        private fun NetworkCapabilities.isValidatedInternet(): Boolean =
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
