package uz.antiradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Cam(val lat: Double, val lon: Double, val limit: Int, val name: String)

object Store {
    val cams = mutableListOf<Cam>()
    private var loaded = false

    fun init(c: Context) {
        if (loaded) return
        loaded = true
        val a = JSONArray(c.getSharedPreferences("a", 0).getString("cams", "[]"))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            cams.add(Cam(o.getDouble("lat"), o.getDouble("lon"), o.getInt("limit"), o.getString("name")))
        }
    }

    fun save(c: Context) {
        val a = JSONArray()
        cams.forEach { a.put(JSONObject().put("lat", it.lat).put("lon", it.lon).put("limit", it.limit).put("name", it.name)) }
        c.getSharedPreferences("a", 0).edit().putString("cams", a.toString()).apply()
    }
}

object State {
    var speed = 0
    var dist = -1f
    var limit = 0
    var over = false
    var lat = 0.0
    var lon = 0.0
    var fix = false
    var running = false
    var onChange: (() -> Unit)? = null
}
