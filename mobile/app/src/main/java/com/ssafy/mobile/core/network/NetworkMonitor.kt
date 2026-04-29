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
        @ApplicationContext private val context: Context,
    ) {
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val isOnline: Flow<Boolean> =
            callbackFlow {
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            // no-op: 실제 온라인 여부는 onCapabilitiesChanged에서 VALIDATED 체크 후 결정
                        }

                        override fun onLost(network: Network) {
                            trySend(false)
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            val hasInternet =
                                networkCapabilities.hasCapability(
                                    NetworkCapabilities.NET_CAPABILITY_INTERNET,
                                )
                            val isValidated =
                                networkCapabilities.hasCapability(
                                    NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                                )
                            trySend(hasInternet && isValidated)
                        }
                    }

                connectivityManager.registerNetworkCallback(
                    NetworkRequest
                        .Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    callback,
                )

                val initialStatus =
                    connectivityManager.activeNetwork?.let { network ->
                        val capabilities = connectivityManager.getNetworkCapabilities(network)
                        val hasInternet =
                            capabilities?.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                            ) == true
                        val isValidated =
                            capabilities?.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                            ) == true
                        hasInternet && isValidated
                    } ?: false
                trySend(initialStatus)

                awaitClose {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            }.distinctUntilChanged()
                .conflate()
    }
