package uz.antiradar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class TrackerService : Service(), LocationListener, TextToSpeech.OnInitListener {
    private lateinit var lm: LocationManager
    private var tts: TextToSpeech? = null
    private var ttsOk = false
    private val done = HashSet<Cam>()
    private var prev: Location? = null
    private var head: Float? = null
    private var lastOver = 0L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel("t", "Антирадар", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(this, "t").setContentTitle("Антирадар UZ работает")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        tts = TextToSpeech(this, this)
        lm = getSystemService(LocationManager::class.java)
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
        } catch (e: SecurityException) {
            stopSelf()
        }
        State.running = true
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onDestroy() {
        lm.removeUpdates(this)
        tts?.shutdown()
        State.running = false
        State.onChange?.invoke()
        super.onDestroy()
    }

    override fun onInit(s: Int) {
        if (s == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ru", "RU")
            ttsOk = true
        }
    }

    private fun say(t: String) {
        if (ttsOk) tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "a")
    }

    private fun diff(a: Float, b: Float) = abs(((a - b + 540f) % 360f) - 180f)

    override fun onLocationChanged(l: Location) {
        val p = prev
        if (p != null && p.distanceTo(l) > 6) head = (p.bearingTo(l) + 360f) % 360f
        if (l.hasBearing() && l.speed > 2) head = l.bearing
        prev = l
        val h = head
        val v = (l.speed * 3.6f).toInt()
        var best: Cam? = null
        var bd = 1e9f
        val r = FloatArray(2)
        for (c in Store.cams) {
            Location.distanceBetween(l.latitude, l.longitude, c.lat, c.lon, r)
            val d = r[0]
            if (d > 1500) { done.remove(c); continue }
            val ahead = h == null || diff((r[1] + 360f) % 360f, h) < 55f
            if (ahead && d < bd) { best = c; bd = d } else if (!ahead && d > 300) done.remove(c)
        }
        val c = best
        val over = c != null && bd < 800 && v > c.limit + 2
        val tr = maxOf(250f, l.speed * 15f)
        if (c != null && bd < tr && done.add(c)) {
            say("Впереди камера, ${maxOf(50, (bd / 50f).roundToInt() * 50)} метров. Ограничение ${c.limit}.")
        }
        if (over && System.currentTimeMillis() - lastOver > 7000) {
            lastOver = System.currentTimeMillis()
            say("Превышение. Снизьте скорость.")
        }
        State.speed = v
        State.dist = if (c != null) bd else -1f
        State.limit = c?.limit ?: 0
        State.over = over
        State.lat = l.latitude
        State.lon = l.longitude
        State.fix = true
        State.onChange?.invoke()
    }

    @Deprecated("old API")
    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}
}
