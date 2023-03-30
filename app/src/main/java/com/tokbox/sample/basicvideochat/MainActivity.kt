package com.tokbox.sample.basicvideochat

import android.Manifest
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.opentok.android.*
import com.opentok.android.PublisherKit.PublisherListener
import com.opentok.android.PublisherKit.PublisherRtcStatsReportListener
import com.opentok.android.Session.SessionListener
import com.opentok.android.SubscriberKit.SubscriberListener
import com.opentok.android.SubscriberKit.SubscriberRtcStatsReportListener
import com.spearline.watchrtc.logger.WatchRTCLoggerImpl
import com.spearline.watchrtc.sdk.*
import com.tokbox.sample.basicvideochat.network.APIService
import com.tokbox.sample.basicvideochat.network.GetSessionResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.EasyPermissions.PermissionCallbacks
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.*

class MainActivity : AppCompatActivity(), PermissionCallbacks {
    private var retrofit: Retrofit? = null
    private var apiService: APIService? = null
    private var session: Session? = null
    private var publisher: Publisher? = null
    private var subscriber: Subscriber? = null
    private lateinit var publisherViewContainer: FrameLayout
    private lateinit var subscriberViewContainer: FrameLayout

    //WatchRTC
    private var watchRTC: WatchRTC? = null
    private var callbackWatchRTC: GetStatsCallback? = null

    private var watchRTCRoomId = ""

    private val publisherListener: PublisherListener = object : PublisherListener {
        override fun onStreamCreated(publisherKit: PublisherKit, stream: Stream) {
            Log.d(TAG, "onStreamCreated: Publisher Stream Created. Own stream ${stream.streamId}")
            watchRTC?.addEvent("publisher-onStreamCreated",
                EventType.Global,
                HashMap<String, String>().apply { put("streamId", stream.streamId) })
        }

        override fun onStreamDestroyed(publisherKit: PublisherKit, stream: Stream) {
            Log.d(
                TAG,
                "onStreamDestroyed: Publisher Stream Destroyed. Own stream ${stream.streamId}"
            )
            watchRTC?.addEvent("publisher-onStreamDestroyed",
                EventType.Global,
                HashMap<String, String>().apply { put("streamId", stream.streamId) })
        }

        override fun onError(publisherKit: PublisherKit, opentokError: OpentokError) {
            finishWithMessage("PublisherKit onError: ${opentokError.message}")
            watchRTC?.addEvent("publisher-onError",
                EventType.Global,
                HashMap<String, String>().apply {
                    put(
                        "errorMessage",
                        opentokError.message
                    )
                    put(
                        "streamId",
                        publisherKit.stream.streamId
                    )
                })
        }
    }
    private val sessionListener: SessionListener = object : SessionListener {
        override fun onConnected(session: Session) {
            Log.d(TAG, "onConnected: Connected to session: ${session.sessionId}")

            publisher = Publisher.Builder(this@MainActivity).build()
            publisher?.setRtcStatsReportListener(publisherRTCStatRepot)
            publisher?.setPublisherListener(publisherListener)
            publisher?.renderer?.setStyle(
                BaseVideoRenderer.STYLE_VIDEO_SCALE,
                BaseVideoRenderer.STYLE_VIDEO_FILL
            )
            publisherViewContainer.addView(publisher?.view)
            if (publisher?.view is GLSurfaceView) {
                (publisher?.view as GLSurfaceView).setZOrderOnTop(true)
            }
            session.publish(publisher)
        }

        override fun onDisconnected(session: Session) {
            Log.d(TAG, "onDisconnected: Disconnected from session: ${session.sessionId}")
            watchRTC?.addEvent("session-onDisconnected",
                EventType.Global,
                HashMap<String, String>().apply {
                    put(
                        "sessionId",
                        session.sessionId
                    )
                })

            watchRTC?.disconnect()
        }

        override fun onStreamReceived(session: Session, stream: Stream) {
            Log.d(
                TAG,
                "onStreamReceived: New Stream Received ${stream.streamId} in session: ${session.sessionId}"
            )

            watchRTC?.connect(this@MainActivity, null)

            if (subscriber == null) {
                subscriber = Subscriber.Builder(this@MainActivity, stream).build().also {
                    it.renderer?.setStyle(
                        BaseVideoRenderer.STYLE_VIDEO_SCALE,
                        BaseVideoRenderer.STYLE_VIDEO_FILL
                    )

                    it.setSubscriberListener(subscriberListener)
                }

                subscriber?.setRtcStatsReportListener(subscriberRTCReport)

                session.subscribe(subscriber)
                subscriberViewContainer.addView(subscriber?.view)
            }
            watchRTC?.addEvent("session-onStreamReceived",
                EventType.Global,
                HashMap<String, String>().apply {
                    put(
                        "sessionId",
                        session.sessionId
                    )
                })
        }


        override fun onStreamDropped(session: Session, stream: Stream) {
            Log.d(
                TAG,
                "onStreamDropped: Stream Dropped: ${stream.streamId} in session: ${session.sessionId}"
            )
            if (subscriber != null) {
                subscriber = null
                subscriberViewContainer.removeAllViews()
            }
            watchRTC?.addEvent("session-onStreamDropped",
                EventType.Global,
                HashMap<String, String>().apply {
                    put(
                        "sessionId",
                        session.sessionId
                    )
                })
        }

        override fun onError(session: Session, opentokError: OpentokError) {
            finishWithMessage("Session error: ${opentokError.message}")
            watchRTC?.addEvent("session-onError",
                EventType.Global,
                HashMap<String, String>().apply {
                    put(
                        "sessionId",
                        session.sessionId
                    )
                    put(
                        "errorMessage",
                        opentokError.message
                    )
                })
        }
    }

    private val publisherRTCStatRepot =
        PublisherRtcStatsReportListener { p0, p1 ->
            Log.d(TAG, "publisherRTCStatRepot.onRtcStatsReport() called.")

            if (callbackWatchRTC == null) {
                Log.d(TAG, "callbackWatchRTC is null")
            }

            p1?.let {
                if (p1.size == 1) {
                    val mapStats = mapFromVonageToRTCStats(p1[0].jsonArrayOfReports)
                    callbackWatchRTC?.onStatsAvailable(mapStats)
                }
            }
        }

    private val subscriberRTCReport =
        SubscriberRtcStatsReportListener { p0, p1 ->
            if (callbackWatchRTC == null) {
                Log.d(TAG, "callbackWatchRTC is null")
            }
            Log.d(TAG, "subscriberRTCReport.onRtcStatsReport() called.")
            p1?.let {
                val mapStats = mapFromVonageToRTCStats(p1)
                callbackWatchRTC?.onStatsAvailable(mapStats)
            }
        }

    private var subscriberListener: SubscriberListener = object : SubscriberListener {
        override fun onConnected(subscriberKit: SubscriberKit) {
            Log.d(
                TAG,
                "onConnected: Subscriber connected. Stream: ${subscriberKit.stream.streamId}"
            )
            watchRTC?.addEvent("subscriber-onConnected",
                EventType.Global,
                HashMap<String, String>().apply {
                    put(
                        "streamId",
                        subscriberKit.stream.streamId
                    )
                })
        }

        override fun onDisconnected(subscriberKit: SubscriberKit) {
            Log.d(
                TAG,
                "onDisconnected: Subscriber disconnected. Stream: ${subscriberKit.stream.streamId}"
            )
            watchRTC?.addEvent("subscriber-onDisconnected",
                EventType.Global,
                HashMap<String, String>().apply {
                    put(
                        "streamId",
                        subscriberKit.stream.streamId
                    )
                })
        }

        override fun onError(subscriberKit: SubscriberKit, opentokError: OpentokError) {
            finishWithMessage("SubscriberKit onError: ${opentokError.message}")
            val data = hashMapOf<String, String>()
            data["streamId"] = subscriberKit.stream.streamId
            data["errorMessage"] = opentokError.message
            watchRTC?.addEvent(
                "subscriber-onError",
                EventType.Global,
                data
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        publisherViewContainer = findViewById(R.id.publisher_container)
        subscriberViewContainer = findViewById(R.id.subscriber_container)

        if (intent.hasExtra("roomId")) {
            watchRTCRoomId = intent.getStringExtra("roomId") ?: ""
        }

        requestPermissions()
    }

    override fun onPause() {
        super.onPause()
        session?.onPause()
    }

    override fun onResume() {
        super.onResume()
        session?.onResume()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        Log.d(TAG, "onPermissionsGranted:$requestCode: $perms")
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        finishWithMessage("onPermissionsDenied: $requestCode: $perms")
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_CODE)
    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (EasyPermissions.hasPermissions(this, *perms)) {
            if (ServerConfig.hasChatServerUrl()) {
                // Custom server URL exists - retrieve session config
                if (!ServerConfig.isValid) {
                    finishWithMessage("Invalid chat server url: ${ServerConfig.CHAT_SERVER_URL}")
                    return
                }
                initRetrofit()
                getSession()
            } else {
                // Use hardcoded session config
                if (!OpenTokConfig.isValid) {
                    finishWithMessage("Invalid OpenTokConfig. ${OpenTokConfig.description}")
                    return
                }
                initializeSession(
                    OpenTokConfig.API_KEY,
                    OpenTokConfig.SESSION_ID,
                    OpenTokConfig.TOKEN_PUBLISHER
                )
            }
        } else {
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.rationale_video_app),
                PERMISSIONS_REQUEST_CODE,
                *perms
            )
        }
    }

    /* Make a request for session data */
    private fun getSession() {
        Log.i(TAG, "getSession")

        apiService?.session?.enqueue(object : Callback<GetSessionResponse?> {
            override fun onResponse(
                call: Call<GetSessionResponse?>,
                response: Response<GetSessionResponse?>
            ) {
                response.body()?.also {
                    initializeSession(it.apiKey, it.sessionId, it.token)
                }
            }

            override fun onFailure(call: Call<GetSessionResponse?>, t: Throwable) {
                throw RuntimeException(t.message)
            }
        })
    }

    private fun initializeSession(apiKey: String, sessionId: String, token: String) {
        Log.i(TAG, "apiKey: $apiKey")
        Log.i(TAG, "sessionId: $sessionId")
        Log.i(TAG, "token: $token")

        //WatchRTC SDK initialization
        initWatchRTCSDK()
        /*
        The context used depends on the specific use case, but usually, it is desired for the session to
        live outside of the Activity e.g: live between activities. For a production applications,
        it's convenient to use Application context instead of Activity context.
         */
        session = Session.Builder(this, apiKey, sessionId).build().also {
            it.setSessionListener(sessionListener)
        }
        /*if (Build.MANUFACTURER == "samsung") {
            session?.connect(OpenTokConfig.TOKEN)
        } else {
            session?.connect(OpenTokConfig.TOKEN)
        }*/
        session?.connect(token)


    }

    private fun initRetrofit() {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(ServerConfig.CHAT_SERVER_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .client(client)
            .build().also {
                apiService = it.create(APIService::class.java)
            }
    }

    private fun finishWithMessage(message: String) {
        Log.e(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val PERMISSIONS_REQUEST_CODE = 124
    }

    //WatchRTC getStats callback to collect webrtc stats
    private val dataProvider = object : RtcDataProvider {
        override fun getStats(callback: GetStatsCallback) {
            callbackWatchRTC = callback
            publisher?.getRtcStatsReport()
            subscriber?.getRtcStatsReport()
        }
    }

    private fun initWatchRTCSDK() {
        watchRTC = WatchRTC(dataProvider)
        //Optional For debug perspective only, Please remove in prod release.;
        watchRTC?.setLoggerImpl(WatchRTCLoggerImpl())

        /**
         * WatchRTC configuration
         * Please add/update below parameters,
         * rtcApiKey: add watchrtc api key you can get it from the TestRTC portal.
         * rtcRoomId: Room id
         * rtcPeerId: Peer connection id
         * keys : optional key-value pairs to be sent to WatchRTC for information purpose.
         */
        val config = WatchRTCConfig(
            BuildConfig.watchrtc_api_key,
            "Vonage-SDK-Android-$watchRTCRoomId",
            if (Build.MANUFACTURER == "samsung") "PC_0" else "PC_1",
            HashMap<String, ArrayList<String>>().apply {
                put("company", ArrayList<String>().apply { add("Spearline") })
            },
            null
        )
        watchRTC?.setConfig(config)
    }

    /**
     * This function convert Vonage stats to WatchRTC stats report.
     */
    private fun mapFromVonageToRTCStats(stats: String): com.spearline.watchrtc.model.RTCStatsReport {
        val report = HashMap<String, com.spearline.watchrtc.model.RTCStatsReport.RTCStat>()

        val jsonArray = JSONArray(stats)
        var timeStamp = Date().time

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)

            val statProperties = HashMap<String, Any>()
            for (memberJsonKey in jsonObject.keys()) {
                statProperties[memberJsonKey] = jsonObject.get(memberJsonKey)
            }
            timeStamp = jsonObject.getLong("timestamp")
            val watchRtcStat = com.spearline.watchrtc.model.RTCStatsReport.RTCStat(
                timeStamp,
                statProperties
            )
            report[jsonObject.getString("id")] = watchRtcStat
        }
        return com.spearline.watchrtc.model.RTCStatsReport(report, timeStamp)
    }


    override fun onDestroy() {
        super.onDestroy()
        session?.disconnect()
    }
}