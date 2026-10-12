package uz.antiradar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var spd: TextView
    private lateinit var info: TextView
    private lateinit var st: TextView
    private lateinit var go: Button
    private lateinit var lim: EditText

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 90, 40, 40) }
        fun tv(size: Float) = TextView(this).apply { setTextColor(Color.WHITE); textSize = size }
        spd = tv(88f); info = tv(20f); st = tv(14f)
        go = Button(this)
        lim = EditText(this).apply { setText("60"); inputType = InputType.TYPE_CLASS_NUMBER; setTextColor(Color.WHITE) }
        val add = Button(this).apply { text = "Камера здесь" }
        val del = Button(this).apply { text = "Удалить последнюю" }
        listOf(spd, info, st, go, lim, add, del).forEach { root.addView(it) }
        setContentView(root)
        go.setOnClickListener {
            if (State.running) {
                stopService(Intent(this, TrackerService::class.java))
                State.running = false
            } else ask()
            render()
        }
        add.setOnClickListener {
            if (!State.fix) { st.text = "Нет GPS-сигнала"; return@setOnClickListener }
            Store.cams.add(Cam(State.lat, State.lon, lim.text.toString().toIntOrNull() ?: 60, "Камера ${Store.cams.size + 1}"))
            Store.save(this); render()
        }
        del.setOnClickListener {
            if (Store.cams.isNotEmpty()) { Store.cams.removeAt(Store.cams.size - 1); Store.save(this); render() }
        }
    }

    private fun ask() {
        val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (checkSelfPermission(need[0]) == PackageManager.PERMISSION_GRANTED) start()
        else requestPermissions(need.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) start()
        else st.text = "Нужно разрешение на геолокацию"
    }

    private fun start() {
        ContextCompat.startForegroundService(this, Intent(this, TrackerService::class.java))
        State.running = true
        render()
    }

    override fun onResume() { super.onResume(); State.onChange = { render() }; render() }
    override fun onPause() { State.onChange = null; super.onPause() }

    private fun render() {
        spd.text = State.speed.toString() + " км/ч"
        info.text = if (State.dist >= 0) {
            val d = State.dist
            (if (d < 1000) "${(d / 10).toInt() * 10} м" else "%.1f км".format(d / 1000)) + " до камеры, лимит ${State.limit}"
        } else "Камер впереди нет"
        st.text = (if (State.running) "GPS работает" else "Выключено") + ", камер в базе: ${Store.cams.size}"
        go.text = if (State.running) "Стоп" else "Старт"
        root.setBackgroundColor(if (State.over) Color.rgb(200, 30, 30) else Color.rgb(15, 26, 42))
    }
}
