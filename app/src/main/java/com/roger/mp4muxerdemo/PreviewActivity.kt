package com.roger.mp4muxerdemo

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.roger.mp4muxerdemo.fragment.Camera2Fragment

class PreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        savedInstanceState ?: supportFragmentManager.beginTransaction()
                .replace(R.id.container, Camera2Fragment.newInstance())
                .commit()
    }

}
