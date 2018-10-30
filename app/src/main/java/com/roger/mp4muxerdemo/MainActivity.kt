package com.roger.mp4muxerdemo

import android.Manifest
import android.app.Activity
import android.hardware.Camera
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.Toast

import com.roger.mp4muxerdemo.jcodec.ListCache
import com.roger.mp4muxerdemo.jcodec.SequenceEncoderMp4
import com.roger.mp4muxerdemo.utils.CameraUtils
import com.roger.mp4muxerdemo.utils.CameraUtils.OnPreviewFrameResult

import java.io.File
import java.io.IOException

import kr.co.namee.permissiongen.PermissionFail
import kr.co.namee.permissiongen.PermissionGen
import kr.co.namee.permissiongen.PermissionSuccess

class MainActivity : Activity(), SurfaceHolder.Callback, OnPreviewFrameResult, CameraUtils.OnCameraFocusResult {
    private var mBtnRecord: Button? = null
    private var mSurfaceView: SurfaceView? = null
    private var mCamManager: CameraUtils? = null
    private var isRecording: Boolean = false

    private var out: File? = null
    private var mHandler: Handler? = null
    private var FILE_FOLDER = Environment.getExternalStorageDirectory()
            .absolutePath + File.separator + "Mp4MuxerDemo"

    private var isPreviewCatch: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mCamManager = CameraUtils.getCamManagerInstance(this@MainActivity)
        mSurfaceView = findViewById<View>(R.id.main_record_surface) as SurfaceView

        mSurfaceView!!.holder.addCallback(this)
        mSurfaceView!!.setOnClickListener {
            mCamManager!!.cameraFocus(this)
        }

        mBtnRecord = findViewById<View>(R.id.main_record_btn) as Button
        mBtnRecord!!.setOnClickListener {
            val mMuxerUtils = MediaMuxerUtils.getMuxerRunnableInstance()
            if (!isRecording) {
                mMuxerUtils.startMuxerThread(mCamManager!!.cameraDirection)
                mBtnRecord!!.text = "停止录像"
            } else {
                //停止录像并生成mp4文件
                mMuxerUtils.stopMuxerThread()
                mBtnRecord!!.text = "开始录像"
            }
            isRecording = !isRecording
        }

        findViewById<View>(R.id.main_delete_btn).setOnClickListener {
            try {
                if (out!!.exists()) {
                    out!!.delete()
                    out!!.createNewFile()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        findViewById<View>(R.id.main_shengcheng_btn).setOnClickListener {
            try {
                sequenceEncoderMp4!!.setFrameNo(ListCache.getInstance(this@MainActivity)!!.lastIndex.toInt())
                sequenceEncoderMp4!!.finish()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }



    }

    private fun init() {
        out = File(FILE_FOLDER, "jcodec_enc.mp4")
        if (!out!!.parentFile.exists()) {
            out!!.parentFile.mkdirs()
        }
        try {
            sequenceEncoderMp4 = SequenceEncoderMp4(out!!, this)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        mHandler = object : Handler() {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                isPreviewCatch = true
                mHandler!!.sendEmptyMessageDelayed(0, 1000)
            }
        }
        mHandler!!.sendEmptyMessageDelayed(0, 1000)
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        mCamManager!!.surfaceHolder = surfaceHolder
        PermissionGen.with(this@MainActivity)
                .addRequestCode(100)
                .permissions(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .request()
    }

    override fun surfaceChanged(surfaceHolder: SurfaceHolder, i: Int, i1: Int, i2: Int) {

    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
        mCamManager!!.stopPreivew()
        mCamManager!!.destoryCamera()
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        PermissionGen.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }

    @PermissionSuccess(requestCode = 100)
    fun doSomething() {
        init()
        mCamManager!!.setOnPreviewResult(this)
        mCamManager!!.createCamera()
        mCamManager!!.startPreview()
        Toast.makeText(this, "Permission is granted", Toast.LENGTH_SHORT).show()
    }

    @PermissionFail(requestCode = 100)
    fun doFailSomething() {
        Toast.makeText(this, "Permission is not granted", Toast.LENGTH_SHORT).show()
    }

    override fun onPreviewResult(data: ByteArray, camera: Camera) {
        mCamManager!!.cameraIntance?.addCallbackBuffer(data)
        if (isPreviewCatch) {
            Log.d("Tag", "---isPreviewCatch---:$data")
            MediaMuxerUtils.getMuxerRunnableInstance().addVideoFrameData(data)
            isPreviewCatch = false
        }
    }

    override fun onFocusResult(result: Boolean) {
        if (result) {
            Toast.makeText(this@MainActivity, "对焦成功", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        var sequenceEncoderMp4: SequenceEncoderMp4? = null
    }
}
