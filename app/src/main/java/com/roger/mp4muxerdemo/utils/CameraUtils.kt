package com.roger.mp4muxerdemo.utils

import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Camera
import android.hardware.Camera.AutoFocusCallback
import android.hardware.Camera.CameraInfo
import android.hardware.Camera.PreviewCallback
import android.hardware.Camera.Size
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder

import java.io.IOException
import java.lang.ref.WeakReference

/** Camera操作封装类
 * Created by jiangdongguo on 2017/5/6.
 */
class CameraUtils private constructor() {
    var cameraIntance: Camera? = null
        private set
    var cameraDirection = false
        private set
    private var mPreviewListener: OnPreviewFrameResult? = null
    private var mHolderRef: WeakReference<SurfaceHolder>? = null

    //将预览数据回传到onPreviewResult方法中
    private val previewCallback = object : PreviewCallback {
        private val rotate = false

        override fun onPreviewFrame(data: ByteArray, camera: Camera) {
            mPreviewListener!!.onPreviewResult(data, camera)
        }
    }

    //获得手机方向
    //得到手机的角度
    //旋转90度
    //旋转0度
    //旋转270
    //旋转180
    //分别计算前后置摄像头需要旋转的角度
    private val previewRotateDegree: Int
        get() {
            var phoneDegree = 0
            var result = 0
            val phoneRotate = (mContext as Activity).windowManager.defaultDisplay.orientation
            when (phoneRotate) {
                Surface.ROTATION_0 -> phoneDegree = 0
                Surface.ROTATION_90 -> phoneDegree = 90
                Surface.ROTATION_180 -> phoneDegree = 180
                Surface.ROTATION_270 -> phoneDegree = 270
            }
            val cameraInfo = CameraInfo()
            if (cameraDirection) {
                Camera.getCameraInfo(CameraInfo.CAMERA_FACING_FRONT, cameraInfo)
                result = (cameraInfo.orientation + phoneDegree) % 360
                result = (360 - result) % 360
            } else {
                Camera.getCameraInfo(CameraInfo.CAMERA_FACING_BACK, cameraInfo)
                result = (cameraInfo.orientation - phoneDegree + 360) % 360
            }
            return result
        }

    val previewFormat: Int
        get() = if (cameraIntance == null) {
            -1
        } else cameraIntance!!.parameters.previewFormat

    var surfaceHolder: SurfaceHolder?
        get() = if (mHolderRef == null) {
            null
        } else mHolderRef!!.get()
        set(mSurfaceHolder) {
            if (mHolderRef != null) {
                mHolderRef!!.clear()
                mHolderRef = null
            }
            mHolderRef = WeakReference<SurfaceHolder>(mSurfaceHolder)
        }

    interface OnPreviewFrameResult {
        fun onPreviewResult(data: ByteArray, camera: Camera)
    }

    interface OnCameraFocusResult {
        fun onFocusResult(result: Boolean)
    }

    fun setOnPreviewResult(mPreviewListener: OnPreviewFrameResult) {
        this.mPreviewListener = mPreviewListener
    }

    fun startPreview() {
        if (cameraIntance == null) {
            return
        }
        //设定预览控件
        try {
            Log.i(TAG, "CameraManager-->开始相机预览")
            cameraIntance!!.setPreviewDisplay(mHolderRef!!.get())
        } catch (e: IOException) {
            e.printStackTrace()
        }

        //开始预览Camera
        try {
            cameraIntance!!.startPreview()
        } catch (e: RuntimeException) {
            Log.i(TAG, "相机预览失败，重新启动Camera.")
            stopPreivew()
            destoryCamera()
            createCamera()
            startPreview()
        }

        //自动对焦
        cameraIntance!!.autoFocus(null)
        //设置预览回调缓存
        val previewFormat = cameraIntance!!.parameters.previewFormat
        val previewSize = cameraIntance!!.parameters.previewSize
        val size = previewSize.width * previewSize.height * ImageFormat.getBitsPerPixel(previewFormat) / 8
        cameraIntance!!.addCallbackBuffer(ByteArray(size))
        cameraIntance!!.setPreviewCallbackWithBuffer(previewCallback)
    }

    fun stopPreivew() {
        if (cameraIntance == null) {
            return
        }
        try {
            cameraIntance!!.setPreviewDisplay(null)
            cameraIntance!!.setPreviewCallbackWithBuffer(null)
            cameraIntance!!.stopPreview()
            Log.i(TAG, "CameraManager-->停止相机预览")
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    fun createCamera() {
        //创建Camera
        openCamera()
        setCamParameters()
    }

    private fun openCamera() {
        if (cameraIntance != null) {
            stopPreivew()
            destoryCamera()
        }
        //打开前置摄像头
        if (cameraDirection) {
            val cameraInfo = CameraInfo()
            val camNums = Camera.getNumberOfCameras()
            for (i in 0 until camNums) {
                Camera.getCameraInfo(i, cameraInfo)
                if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
                    try {
                        cameraIntance = Camera.open(i)
                        Log.i(TAG, "CameraManager-->创建Camera对象，开启前置摄像头")
                        break
                    } catch (e: Exception) {
                        Log.d(TAG, "打开前置摄像头失败：" + e.message)
                    }

                }
            }
        } else {
            try {
                cameraIntance = Camera.open()
                Log.i(TAG, "CameraManager-->创建Camera对象，开启后置摄像头")
            } catch (e: Exception) {
                Log.d(TAG, "打开后置摄像头失败：" + e.message)
            }

        }
    }

    fun destoryCamera() {
        if (cameraIntance == null) {
            return
        }
        cameraIntance!!.release()
        cameraIntance = null
        Log.i(TAG, "CameraManager-->释放相机资源")
    }

    private fun setCamParameters() {
        if (cameraIntance == null)
            return
        val params = cameraIntance!!.parameters
        if (isUsingYv12) {
            params.previewFormat = ImageFormat.YV12
        } else {
            params.previewFormat = ImageFormat.NV21
        }
        //开启自动对焦
        val focusModes = params.supportedFocusModes
        if (isSupportFocusAuto(focusModes)) {
            params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
        }
        //设置预览分辨率，问题出在这里
        val previewSizes = params.supportedPreviewSizes
        if (!isSupportPreviewSize(previewSizes)) {
            PREVIEW_WIDTH = previewSizes[0].width
            PREVIEW_HEIGHT = previewSizes[0].height
        }
        params.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
        //设置预览的最大、最小像素
        val max = determineMaximumSupportedFramerate(params)
        params.setPreviewFpsRange(max[0], max[1])
        //使参数配置生效
        cameraIntance!!.parameters = params
        //旋转预览方向
        val rotateDegree = previewRotateDegree
        cameraIntance!!.setDisplayOrientation(rotateDegree)
    }

    fun cameraFocus(listener: OnCameraFocusResult?) {
        if (cameraIntance != null) {
            cameraIntance!!.autoFocus { success, camera ->
                listener?.onFocusResult(success)
            }
        }
    }

    private fun isSupportFocusAuto(focusModes: List<String>): Boolean {
        var isSupport = false
        for (mode in focusModes) {
            if (mode == Camera.Parameters.FLASH_MODE_AUTO) {
                isSupport = true
                break
            }
        }
        return isSupport
    }

    private fun isSupportPreviewSize(previewSizes: List<Size>): Boolean {
        var isSupport = false
        for (size in previewSizes) {
            if (size.width == PREVIEW_WIDTH && size.height == PREVIEW_HEIGHT || size.width == PREVIEW_HEIGHT && size.height == PREVIEW_WIDTH) {
                isSupport = true
                break
            }
        }
        return isSupport
    }

    fun switchCamera() {
        cameraDirection = !cameraDirection
        createCamera()
        startPreview()
    }

    fun setPreviewSize(width: Int, height: Int) {
        PREVIEW_WIDTH = width
        PREVIEW_HEIGHT = height
    }

    companion object {
        private val TAG = "CameraManager"
        var PREVIEW_WIDTH = 1920
        var PREVIEW_HEIGHT = 1080
        var isUsingYv12 = false
        private var mContext: Context? = null


        private var mCameraManager: CameraUtils? = null

        fun getCamManagerInstance(mContext: Context): CameraUtils? {
            CameraUtils.mContext = mContext
            if (mCameraManager == null) {
                mCameraManager = CameraUtils()
            }
            return mCameraManager
        }

        fun determineMaximumSupportedFramerate(parameters: Camera.Parameters): IntArray {
            var maxFps = intArrayOf(0, 0)
            val supportedFpsRanges = parameters.supportedPreviewFpsRange
            val it = supportedFpsRanges.iterator()
            while (it.hasNext()) {
                val interval = it.next()
                if (interval[1] > maxFps[1] || interval[0] > maxFps[0] && interval[1] == maxFps[1]) {
                    maxFps = interval
                }
            }
            return maxFps
        }
    }
}
