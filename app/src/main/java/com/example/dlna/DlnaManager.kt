package com.example.dlna

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import android.util.Xml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

class DlnaManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _activeDevice = MutableStateFlow<DlnaDevice?>(null)
    val activeDevice: StateFlow<DlnaDevice?> = _activeDevice.asStateFlow()

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _volume = MutableStateFlow(50)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val isDlnaActive: Boolean
        get() = _activeDevice.value != null

    private var scanJob: Job? = null

    companion object {
        private const val TAG = "DlnaManager"
        private const val SSDP_HOST = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val RENDERING_CONTROL_SERVICE = "urn:schemas-upnp-org:service:RenderingControl:1"
        private const val MEDIA_RENDERER_DEVICE = "urn:schemas-upnp-org:device:MediaRenderer:1"
    }

    fun startDiscovery() {
        if (_isScanning.value) return
        scanJob?.cancel()
        scanJob = scope.launch {
            _isScanning.value = true
            _lastError.value = null

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val multicastLock = try {
                wifiManager?.createMulticastLock("DlnaMulticastLock")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not acquire multicast lock: ${e.message}")
                null
            }

            try {
                withContext(Dispatchers.IO) {
                    discoverWithSockets()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Discovery error: ${e.message}")
            } finally {
                try {
                    multicastLock?.release()
                } catch (e: Exception) {
                    // Ignored
                }
                _isScanning.value = false
            }
        }
    }

    private suspend fun discoverWithSockets() {
        val targets = listOf(
            AV_TRANSPORT_SERVICE,
            MEDIA_RENDERER_DEVICE
        )

        val discoveredLocations = mutableSetOf<String>()

        for (target in targets) {
            val query = buildSsdpQuery(target)
            val socket = try {
                DatagramSocket().apply {
                    soTimeout = 2500
                    reuseAddress = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Socket creation failed: ${e.message}")
                continue
            }

            try {
                val group = InetAddress.getByName(SSDP_HOST)
                val sendData = query.toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(sendData, sendData.size, group, SSDP_PORT)
                socket.send(packet)

                val buffer = ByteArray(4096)
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 2500) {
                    try {
                        val receivePacket = DatagramPacket(buffer, buffer.size)
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                        val location = extractHeader(response, "LOCATION")
                        if (!location.isNullOrBlank() && discoveredLocations.add(location)) {
                            scope.launch {
                                parseDeviceDescription(location)
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        break
                    } catch (e: Exception) {
                        break
                    }
                }
            } finally {
                socket.close()
            }
            delay(300)
        }
    }

    private fun buildSsdpQuery(target: String): String {
        return "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: $SSDP_HOST:$SSDP_PORT\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: $target\r\n" +
                "\r\n"
    }

    private fun extractHeader(response: String, headerName: String): String? {
        val lines = response.lines()
        for (line in lines) {
            val index = line.indexOf(':')
            if (index > 0) {
                val key = line.substring(0, index).trim()
                if (key.equals(headerName, ignoreCase = true)) {
                    return line.substring(index + 1).trim()
                }
            }
        }
        return null
    }

    private suspend fun parseDeviceDescription(locationUrl: String) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(locationUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext

            val xml = response.body?.string() ?: return@withContext
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var friendlyName = ""
            var modelName = ""
            var manufacturer = ""
            var udn = ""
            var currentTag = ""
            var insideService = false
            var currentServiceType = ""
            var currentControlUrl = ""
            var avTransportControlUrl = ""
            var renderingControlUrl = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        if (currentTag.equals("service", ignoreCase = true)) {
                            insideService = true
                            currentServiceType = ""
                            currentControlUrl = ""
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotBlank()) {
                            when {
                                currentTag.equals("friendlyName", ignoreCase = true) && friendlyName.isEmpty() -> {
                                    friendlyName = text
                                }
                                currentTag.equals("modelName", ignoreCase = true) && modelName.isEmpty() -> {
                                    modelName = text
                                }
                                currentTag.equals("manufacturer", ignoreCase = true) && manufacturer.isEmpty() -> {
                                    manufacturer = text
                                }
                                currentTag.equals("UDN", ignoreCase = true) && udn.isEmpty() -> {
                                    udn = text
                                }
                                insideService && currentTag.equals("serviceType", ignoreCase = true) -> {
                                    currentServiceType = text
                                }
                                insideService && currentTag.equals("controlURL", ignoreCase = true) -> {
                                    currentControlUrl = text
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("service", ignoreCase = true)) {
                            if (currentServiceType.contains("AVTransport", ignoreCase = true) && currentControlUrl.isNotBlank()) {
                                avTransportControlUrl = currentControlUrl
                            } else if (currentServiceType.contains("RenderingControl", ignoreCase = true) && currentControlUrl.isNotBlank()) {
                                renderingControlUrl = currentControlUrl
                            }
                            insideService = false
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }

            if (avTransportControlUrl.isNotBlank() && friendlyName.isNotBlank()) {
                val resolvedControlUrl = resolveUrl(locationUrl, avTransportControlUrl)
                val resolvedRenderingControlUrl = if (renderingControlUrl.isNotBlank()) resolveUrl(locationUrl, renderingControlUrl) else null
                val id = if (udn.isNotBlank()) udn else locationUrl

                val device = DlnaDevice(
                    id = id,
                    name = friendlyName,
                    modelName = modelName,
                    manufacturer = manufacturer,
                    locationUrl = locationUrl,
                    controlUrl = resolvedControlUrl,
                    renderingControlUrl = resolvedRenderingControlUrl
                )

                _devices.update { current ->
                    if (current.none { it.id == device.id || it.controlUrl == device.controlUrl }) {
                        current + device
                    } else {
                        current.map { if (it.id == device.id) device else it }
                    }
                }
                Log.i(TAG, "Discovered DLNA Renderer: ${device.name} ($resolvedControlUrl)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing device description at $locationUrl: ${e.message}")
        }
    }

    private fun resolveUrl(baseUrl: String, relativeOrAbsoluteUrl: String): String {
        return try {
            if (relativeOrAbsoluteUrl.startsWith("http://", ignoreCase = true) ||
                relativeOrAbsoluteUrl.startsWith("https://", ignoreCase = true)
            ) {
                relativeOrAbsoluteUrl
            } else {
                URI(baseUrl).resolve(relativeOrAbsoluteUrl).toString()
            }
        } catch (e: Exception) {
            relativeOrAbsoluteUrl
        }
    }

    var lastStreamUrl: String? = null
        private set

    var lastStreamTitle: String? = null
        private set

    var lastStreamArtist: String? = null
        private set

    var lastStreamStation: String? = null
        private set

    var lastStreamArtworkUrl: String? = null
        private set

    fun updateCurrentMetadata(
        title: String,
        artist: String = "",
        stationName: String = "",
        artworkUrl: String? = null
    ) {
        if (title.isNotBlank()) lastStreamTitle = title
        if (artist.isNotBlank()) lastStreamArtist = artist
        if (stationName.isNotBlank()) lastStreamStation = stationName
        if (!artworkUrl.isNullOrBlank()) lastStreamArtworkUrl = artworkUrl
    }

    fun getDeviceVolume(): Int = _volume.value

    fun setVolumeAsync(vol: Int) {
        scope.launch {
            setVolume(vol)
        }
    }

    fun adjustVolume(delta: Int) {
        val current = _volume.value
        val target = (current + delta).coerceIn(0, 100)
        setVolumeAsync(target)
    }

    suspend fun fetchVolume(device: DlnaDevice? = _activeDevice.value): Int? = withContext(Dispatchers.IO) {
        val targetDevice = device ?: return@withContext null
        val controlUrl = targetDevice.renderingControlUrl ?: targetDevice.controlUrl
        val soapBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body>" +
                "<u:GetVolume xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\">" +
                "<InstanceID>0</InstanceID>" +
                "<Channel>Master</Channel>" +
                "</u:GetVolume>" +
                "</s:Body>" +
                "</s:Envelope>"

        try {
            val mediaType = "text/xml; charset=utf-8".toMediaType()
            val requestBody = soapBody.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(controlUrl)
                .addHeader("SOAPAction", "\"urn:schemas-upnp-org:service:RenderingControl:1#GetVolume\"")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val match = Regex("<CurrentVolume>(\\d+)</CurrentVolume>", RegexOption.IGNORE_CASE).find(body)
                val parsed = match?.groupValues?.get(1)?.toIntOrNull()
                if (parsed != null) {
                    val clamped = parsed.coerceIn(0, 100)
                    _volume.value = clamped
                    return@withContext clamped
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GetVolume failed on ${targetDevice.name}: ${e.message}")
        }
        null
    }

    suspend fun setVolume(volume: Int, device: DlnaDevice? = _activeDevice.value): Boolean = withContext(Dispatchers.IO) {
        val targetDevice = device ?: return@withContext false
        val clamped = volume.coerceIn(0, 100)
        _volume.value = clamped

        val controlUrl = targetDevice.renderingControlUrl ?: targetDevice.controlUrl
        val soapBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body>" +
                "<u:SetVolume xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\">" +
                "<InstanceID>0</InstanceID>" +
                "<Channel>Master</Channel>" +
                "<DesiredVolume>$clamped</DesiredVolume>" +
                "</u:SetVolume>" +
                "</s:Body>" +
                "</s:Envelope>"

        val success = sendRenderingControlSoapAction(controlUrl, "SetVolume", soapBody)
        if (!success && targetDevice.renderingControlUrl != null && targetDevice.renderingControlUrl != targetDevice.controlUrl) {
            sendRenderingControlSoapAction(targetDevice.controlUrl, "SetVolume", soapBody)
        } else {
            success
        }
    }

    private fun sendRenderingControlSoapAction(controlUrl: String, action: String, body: String): Boolean {
        return try {
            val mediaType = "text/xml; charset=utf-8".toMediaType()
            val requestBody = body.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(controlUrl)
                .addHeader("SOAPAction", "\"urn:schemas-upnp-org:service:RenderingControl:1#$action\"")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "RenderingControl SOAP action $action failed on $controlUrl: ${e.message}")
            false
        }
    }

    suspend fun castStream(
        device: DlnaDevice,
        streamUrl: String,
        title: String,
        artist: String = "",
        stationName: String = "",
        artworkUrl: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        _lastError.value = null
        try {
            // Stop remote device if currently playing/transitioning
            try {
                sendStop(device.controlUrl)
                delay(150)
            } catch (e: Exception) {
                // Ignore stop error
            }

            val setUriSuccess = sendSetAvTransportUri(
                controlUrl = device.controlUrl,
                mediaUrl = streamUrl,
                title = title,
                artist = artist,
                stationName = stationName,
                artworkUrl = artworkUrl
            )
            if (!setUriSuccess) {
                _lastError.value = "Impossible de configurer le flux sur ${device.name}"
                return@withContext false
            }

            delay(100)
            val playSuccess = sendPlay(device.controlUrl)
            if (playSuccess) {
                _activeDevice.value = device
                _isCasting.value = true
                lastStreamUrl = streamUrl
                lastStreamTitle = title
                lastStreamArtist = artist
                lastStreamStation = stationName
                lastStreamArtworkUrl = artworkUrl
                scope.launch { fetchVolume(device) }
                return@withContext true
            } else {
                _lastError.value = "Échec du lancement de la lecture sur ${device.name}"
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error casting to ${device.name}: ${e.message}", e)
            _lastError.value = e.message ?: "Erreur de connexion"
            return@withContext false
        }
    }

    suspend fun changeSource(
        streamUrl: String,
        title: String,
        artist: String = "",
        stationName: String = "",
        artworkUrl: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val device = _activeDevice.value ?: return@withContext false
        Log.i(TAG, "Changing source directly on DLNA renderer ${device.name} ($streamUrl) preserving renderer URI")
        _lastError.value = null
        try {
            // Stop current playback cleanly without breaking UPnP device binding
            try {
                sendStop(device.controlUrl)
                delay(150)
            } catch (e: Exception) {
                // Ignore stop error
            }

            val setUriSuccess = sendSetAvTransportUri(
                controlUrl = device.controlUrl,
                mediaUrl = streamUrl,
                title = title,
                artist = artist,
                stationName = stationName,
                artworkUrl = artworkUrl
            )
            if (!setUriSuccess) {
                _lastError.value = "Impossible de configurer le flux sur ${device.name}"
                return@withContext false
            }

            delay(100)
            val playSuccess = sendPlay(device.controlUrl)
            if (playSuccess) {
                _isCasting.value = true
                lastStreamUrl = streamUrl
                lastStreamTitle = title
                lastStreamArtist = artist
                lastStreamStation = stationName
                lastStreamArtworkUrl = artworkUrl
                return@withContext true
            } else {
                delay(200)
                val retryPlay = sendPlay(device.controlUrl)
                if (retryPlay) {
                    _isCasting.value = true
                    lastStreamUrl = streamUrl
                    lastStreamTitle = title
                    lastStreamArtist = artist
                    lastStreamStation = stationName
                    lastStreamArtworkUrl = artworkUrl
                    return@withContext true
                }
                _lastError.value = "Échec du lancement de la lecture sur ${device.name}"
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error changing source on ${device.name}: ${e.message}", e)
            _lastError.value = e.message ?: "Erreur de connexion"
            return@withContext false
        }
    }

    suspend fun resyncStream(
        device: DlnaDevice,
        streamUrl: String,
        title: String,
        artist: String = "",
        stationName: String = "",
        artworkUrl: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Executing STOP -> SET_AV_TRANSPORT_URI -> PLAY on ${device.name}")
        _lastError.value = null
        try {
            // 1. STOP to purge renderer buffer
            try {
                sendStop(device.controlUrl)
                delay(200)
            } catch (e: Exception) {
                Log.w(TAG, "Stop during resync failed: ${e.message}")
            }

            // 2. SET_AV_TRANSPORT_URI
            val setUriSuccess = sendSetAvTransportUri(
                controlUrl = device.controlUrl,
                mediaUrl = streamUrl,
                title = title,
                artist = artist,
                stationName = stationName,
                artworkUrl = artworkUrl
            )
            if (!setUriSuccess) {
                _lastError.value = "Impossible de reconfigurer le flux sur ${device.name}"
                return@withContext false
            }

            delay(150)

            // 3. PLAY
            val playSuccess = sendPlay(device.controlUrl)
            if (playSuccess) {
                _activeDevice.value = device
                _isCasting.value = true
                lastStreamUrl = streamUrl
                lastStreamTitle = title
                lastStreamArtist = artist
                lastStreamStation = stationName
                lastStreamArtworkUrl = artworkUrl
                return@withContext true
            } else {
                delay(200)
                val retryPlay = sendPlay(device.controlUrl)
                if (retryPlay) {
                    _activeDevice.value = device
                    _isCasting.value = true
                    lastStreamUrl = streamUrl
                    lastStreamTitle = title
                    lastStreamArtist = artist
                    lastStreamStation = stationName
                    lastStreamArtworkUrl = artworkUrl
                    return@withContext true
                }
                _lastError.value = "Échec de la relance de lecture sur ${device.name}"
                return@withContext false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Resync failed on ${device.name}: ${e.message}")
            return@withContext false
        }
    }

    suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        val device = _activeDevice.value ?: return@withContext false
        Log.i(TAG, "Pausing DLNA stream on ${device.name}")
        val paused = sendPause(device.controlUrl)
        if (!paused) {
            // Live broadcast streams on some renderers reject Pause, fallback to Stop
            Log.d(TAG, "Pause returned false, falling back to Stop for live stream")
            sendStop(device.controlUrl)
        }
        return@withContext true
    }

    suspend fun stopPlayback(): Boolean = withContext(Dispatchers.IO) {
        val device = _activeDevice.value ?: return@withContext false
        Log.i(TAG, "Stopping DLNA stream on ${device.name} without dropping connection")
        val stopped = sendStop(device.controlUrl)
        return@withContext stopped
    }

    suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        val device = _activeDevice.value ?: return@withContext false
        Log.i(TAG, "Resuming DLNA stream on ${device.name}")
        val played = sendPlay(device.controlUrl)
        if (!played && !lastStreamUrl.isNullOrBlank()) {
            Log.i(TAG, "Direct play returned false, re-applying transport URI and retrying Play")
            sendSetAvTransportUri(
                controlUrl = device.controlUrl,
                mediaUrl = lastStreamUrl!!,
                title = lastStreamTitle ?: device.name,
                artist = lastStreamArtist.orEmpty(),
                stationName = lastStreamStation.orEmpty(),
                artworkUrl = lastStreamArtworkUrl
            )
            delay(150)
            return@withContext sendPlay(device.controlUrl)
        }
        return@withContext played
    }

    suspend fun stopCasting(): Boolean = withContext(Dispatchers.IO) {
        val device = _activeDevice.value ?: return@withContext true
        try {
            sendStop(device.controlUrl)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping DLNA: ${e.message}")
        } finally {
            _activeDevice.value = null
            _isCasting.value = false
            lastStreamUrl = null
            lastStreamTitle = null
            lastStreamArtist = null
            lastStreamStation = null
            lastStreamArtworkUrl = null
        }
        return@withContext true
    }

    private fun sendSetAvTransportUri(
        controlUrl: String,
        mediaUrl: String,
        title: String,
        artist: String = "",
        stationName: String = "",
        artworkUrl: String? = null
    ): Boolean {
        val effectiveTitle = when {
            title.isNotBlank() && title != stationName -> {
                if (artist.isNotBlank() && !title.contains(artist, ignoreCase = true)) {
                    "$artist - $title"
                } else {
                    title
                }
            }
            title.isNotBlank() -> title
            stationName.isNotBlank() -> stationName
            else -> "Ocarina Webradio"
        }
        val effectiveArtist = if (artist.isNotBlank()) artist else stationName
        val effectiveAlbum = if (stationName.isNotBlank()) stationName else effectiveTitle

        val escapedUri = escapeXml(mediaUrl)
        val escapedTitle = escapeXml(effectiveTitle)
        val escapedArtist = escapeXml(effectiveArtist)
        val escapedAlbum = escapeXml(effectiveAlbum)
        val escapedStation = escapeXml(stationName)
        val escapedArtwork = artworkUrl?.let { escapeXml(it) }.orEmpty()

        val artistTag = if (escapedArtist.isNotBlank()) {
            "<dc:creator>$escapedArtist</dc:creator><upnp:artist>$escapedArtist</upnp:artist>"
        } else ""

        val albumTag = if (escapedAlbum.isNotBlank()) {
            "<upnp:album>$escapedAlbum</upnp:album><dc:description>$escapedStation</dc:description>"
        } else ""

        val artworkTag = if (escapedArtwork.isNotBlank()) {
            "<upnp:albumArtURI>$escapedArtwork</upnp:albumArtURI>"
        } else ""

        val didlMetadata = escapeXml(
            "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
                    "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
                    "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
                    "<item id=\"1\" parentID=\"0\" restricted=\"1\">" +
                    "<dc:title>$escapedTitle</dc:title>" +
                    artistTag +
                    albumTag +
                    artworkTag +
                    "<upnp:class>object.item.audioItem.audioBroadcast</upnp:class>" +
                    "<res protocolInfo=\"http-get:*:audio/*:*\">$escapedUri</res>" +
                    "</item></DIDL-Lite>"
        )

        val soapBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body>" +
                "<u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                "<InstanceID>0</InstanceID>" +
                "<CurrentURI>$escapedUri</CurrentURI>" +
                "<CurrentURIMetaData>$didlMetadata</CurrentURIMetaData>" +
                "</u:SetAVTransportURI>" +
                "</s:Body>" +
                "</s:Envelope>"

        return sendSoapAction(controlUrl, "SetAVTransportURI", soapBody)
    }

    private fun sendPlay(controlUrl: String): Boolean {
        val soapBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body>" +
                "<u:Play xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                "<InstanceID>0</InstanceID>" +
                "<Speed>1</Speed>" +
                "</u:Play>" +
                "</s:Body>" +
                "</s:Envelope>"

        return sendSoapAction(controlUrl, "Play", soapBody)
    }

    private fun sendPause(controlUrl: String): Boolean {
        val soapBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body>" +
                "<u:Pause xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                "<InstanceID>0</InstanceID>" +
                "</u:Pause>" +
                "</s:Body>" +
                "</s:Envelope>"

        return sendSoapAction(controlUrl, "Pause", soapBody)
    }

    private fun sendStop(controlUrl: String): Boolean {
        val soapBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body>" +
                "<u:Stop xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                "<InstanceID>0</InstanceID>" +
                "</u:Stop>" +
                "</s:Body>" +
                "</s:Envelope>"

        return sendSoapAction(controlUrl, "Stop", soapBody)
    }

    private fun sendSoapAction(controlUrl: String, action: String, body: String): Boolean {
        return try {
            val mediaType = "text/xml; charset=utf-8".toMediaType()
            val requestBody = body.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(controlUrl)
                .addHeader("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "SOAP action $action failed on $controlUrl: ${e.message}")
            false
        }
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
