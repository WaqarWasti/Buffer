package app.buffer

import android.app.Application
import app.buffer.data.Attempts
import app.buffer.data.Store

class BufferApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Attempts.init(this)
    }
}
