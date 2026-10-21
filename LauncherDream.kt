package sa.tv.launcher

import android.service.dreams.DreamService

class LauncherDream : DreamService() {
    private var v: AmbientView? = null
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val a = AmbientView(this)
        v = a
        setContentView(a)
    }
    override fun onDreamingStarted() { super.onDreamingStarted(); v?.start() }
    override fun onDreamingStopped() { v?.stop(); super.onDreamingStopped() }
}
