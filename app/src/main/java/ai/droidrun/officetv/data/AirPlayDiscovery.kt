package ai.droidrun.officetv.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/** Resolves only AirPlay advertisements whose IPv4 host belongs to this TV. No PIN access. */
@Suppress("DEPRECATION") // Fire OS API 30 needs the original NSD resolve API.
class AirPlayDiscovery(context: Context) {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val names = mutableSetOf<String>()
    private val failed = mutableMapOf<String, NsdServiceInfo>()
    private val advertised = mutableSetOf<String>()
    private val ready = MutableStateFlow<String?>(null)
    val name = ready.asStateFlow()
    private var discovery: NsdManager.DiscoveryListener? = null
    private var generation = 0
    private val queue = ArrayDeque<Pair<Int, NsdServiceInfo>>()
    private var resolving = false

    fun start() {
        if (discovery != null) return
        val scan = ++generation
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                handler.post { if (scan == generation) stop() }
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                handler.post {
                    if (scan == generation && discovery != null) {
                        advertised.add(info.serviceName)
                        failed.values.forEach { queue.addLast(scan to it) }
                        failed.clear()
                        if (queue.none { it.second.serviceName == info.serviceName }) queue.addLast(scan to info)
                        resolveNext()
                    }
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                handler.post {
                    if (scan == generation) {
                        advertised.remove(info.serviceName)
                        names.remove(info.serviceName)
                        failed.remove(info.serviceName)
                        queue.removeAll { it.second.serviceName == info.serviceName }
                        ready.value = names.firstOrNull()
                    }
                }
            }
        }
        discovery = listener
        try {
            manager.discoverServices("_airplay._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: RuntimeException) { stop() }
    }

    private fun resolveNext() {
        if (resolving || queue.isEmpty()) return
        val (scan, service) = queue.removeFirst()
        resolving = true
        try {
            manager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) = complete(null)
                override fun onServiceResolved(info: NsdServiceInfo) = complete(info)
                private fun complete(info: NsdServiceInfo?) {
                    handler.post {
                        if (scan != generation || discovery == null) return@post
                        if (info == null && service.serviceName in advertised) failed[service.serviceName] = service
                        if (info != null && info.serviceName in advertised &&
                            info.host is Inet4Address && info.host.hostAddress in ownIPv4()
                        ) {
                            names.add(info.serviceName)
                            ready.value = names.firstOrNull()
                        }
                        resolving = false
                        resolveNext()
                    }
                }
            })
        } catch (_: RuntimeException) {
            if (service.serviceName in advertised) failed[service.serviceName] = service
            resolving = false
            resolveNext()
        }
    }

    fun stop() {
        generation++
        val listener = discovery
        discovery = null
        queue.clear()
        failed.clear()
        resolving = false
        names.clear()
        advertised.clear()
        ready.value = null
        if (listener != null) runCatching { manager.stopServiceDiscovery(listener) }
    }

    private fun ownIPv4(): Set<String?> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap {
            Collections.list(it.inetAddresses)
        }.filterIsInstance<Inet4Address>().filterNot { it.isLoopbackAddress }.map { it.hostAddress }.toSet()
    }.getOrDefault(emptySet())
}
