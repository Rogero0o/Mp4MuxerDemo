package com.roger.mp4muxerdemo

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log

import com.roger.mp4muxerdemo.utils.CameraUtils

import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.Vector

class EncoderVideoRunnable(// MP4混合器
        private val muxerRunnableRf: WeakReference<MediaMuxerUtils>) : Runnable {
    // 正常垂直方向拍摄
    private val isPhoneHorizontal = true
    // 硬编码器
    private var mVideoEncodec: MediaCodec? = null
    private var mColorFormat: Int = 0
    private var isExit = false
    private var isEncoderStart = false

    private val frameBytes: Vector<ByteArray>?
    private val mFrameData: ByteArray
    var isFrontCamera: Boolean = false
    private val prevPresentationTimes: Long = 0
    private var mFormat: MediaFormat? = null

    private// API>=21
    val isLollipop: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    private// API<=19
    val isKITKAT: Boolean
        get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT

    private val ptsUs: Long
        get() {
            var result = System.nanoTime() / 1000
            if (result < prevPresentationTimes) {
                result = prevPresentationTimes - result + result
            }
            return result
        }

    init {
        frameBytes = Vector()
        mFrameData = ByteArray(CameraUtils.PREVIEW_WIDTH * CameraUtils.PREVIEW_HEIGHT * 3 / 2)
        initMediaFormat()
    }

    private fun initMediaFormat() {
        try {
            val mCodecInfo = selectSupportCodec(MIME_TYPE)
            if (mCodecInfo == null) {
                Log.d(TAG, "匹配编码器失败$MIME_TYPE")
                return
            }
            mColorFormat = selectSupportColorFormat(mCodecInfo, MIME_TYPE)
            Log.d(TAG, "mColorFormat:$mColorFormat")
            // NV21->I420
            mVideoEncodec = MediaCodec.createByCodecName(mCodecInfo.name)
        } catch (e: IOException) {
            Log.e(TAG, "创建编码器失败" + e.message)
            e.printStackTrace()
        }

        if (!isPhoneHorizontal) {
            mFormat = MediaFormat.createVideoFormat(MIME_TYPE, CameraUtils.PREVIEW_HEIGHT, CameraUtils.PREVIEW_WIDTH)
        } else {
            mFormat = MediaFormat.createVideoFormat(MIME_TYPE, CameraUtils.PREVIEW_WIDTH, CameraUtils.PREVIEW_HEIGHT)
        }
        mFormat!!.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        mFormat!!.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        mFormat!!.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat) // 颜色格式
        mFormat!!.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, FRAME_INTERVAL)
    }

    private fun startCodec() {
        frameBytes!!.clear()
        isExit = false
        if (mVideoEncodec != null) {
            mVideoEncodec!!.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mVideoEncodec!!.start()
            isEncoderStart = true
            Log.d(TAG, "配置、启动视频编码器")
        }
    }

    private fun stopCodec() {
        if (mVideoEncodec != null) {
            mVideoEncodec!!.stop()
            mVideoEncodec!!.release()
            mVideoEncodec = null
            isEncoderStart = false
            Log.d(TAG, "关闭视频编码器")
        }
    }

    fun addData(yuvData: ByteArray) {
        frameBytes?.add(yuvData)
    }

    override fun run() {
        if (!isEncoderStart) {
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            startCodec()
        }
        // 如果编码器没有启动或者没有图像数据，线程阻塞等待
        while (!isExit) {
            if (!frameBytes!!.isEmpty()) {
                val bytes = frameBytes.removeAt(0)
                try {
                    encoderBytes(bytes)
                } catch (e: IllegalStateException) {
                    // 捕获因中断线程并停止混合dequeueOutputBuffer报的状态异常
                    e.printStackTrace()
                } catch (e: NullPointerException) {
                    // 捕获因中断线程并停止混合MediaCodec为NULL异常
                    e.printStackTrace()
                }

            }
        }
        stopCodec()
    }

    @SuppressLint("NewApi", "WrongConstant")
    private fun encoderBytes(rawFrame: ByteArray) {
        val inputBuffers = mVideoEncodec!!.inputBuffers
        var outputBuffers = mVideoEncodec!!.outputBuffers
        //前置摄像头旋转270度，后置摄像头旋转90度
        val mWidth = CameraUtils.PREVIEW_WIDTH
        val mHeight = CameraUtils.PREVIEW_HEIGHT
        NV21toI420SemiPlanar(rawFrame, mFrameData, mWidth, mHeight)
        //返回编码器的一个输入缓存区句柄，-1表示当前没有可用的输入缓存区
        val inputBufferIndex = mVideoEncodec!!.dequeueInputBuffer(TIMES_OUT.toLong())
        if (inputBufferIndex >= 0) {
            // 绑定一个被空的、可写的输入缓存区inputBuffer到客户端
            var inputBuffer: ByteBuffer? = null
            if (!isLollipop) {
                inputBuffer = inputBuffers[inputBufferIndex]
            } else {
                inputBuffer = mVideoEncodec!!.getInputBuffer(inputBufferIndex)
            }
            // 向输入缓存区写入有效原始数据，并提交到编码器中进行编码处理
            inputBuffer!!.clear()
            inputBuffer.put(mFrameData)
            mVideoEncodec!!.queueInputBuffer(inputBufferIndex, 0, mFrameData.size, ptsUs, 0)
        }

        // 返回一个输出缓存区句柄，当为-1时表示当前没有可用的输出缓存区
        // mBufferInfo参数包含被编码好的数据，timesOut参数为超时等待的时间
        val mBufferInfo = MediaCodec.BufferInfo()
        var outputBufferIndex = -1
        do {
            outputBufferIndex = mVideoEncodec!!.dequeueOutputBuffer(mBufferInfo, TIMES_OUT.toLong())
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.i(TAG, "获得编码器输出缓存区超时")
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // 如果API小于21，APP需要重新绑定编码器的输入缓存区；
                // 如果API大于21，则无需处理INFO_OUTPUT_BUFFERS_CHANGED
                if (!isLollipop) {
                    outputBuffers = mVideoEncodec!!.outputBuffers
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // 编码器输出缓存区格式改变，通常在存储数据之前且只会改变一次
                // 这里设置混合器视频轨道，如果音频已经添加则启动混合器（保证音视频同步）
                val newFormat = mVideoEncodec!!.outputFormat
                val mMuxerUtils = muxerRunnableRf.get()
                mMuxerUtils?.setMediaFormat()
                Log.i(TAG, "编码器输出缓存区格式改变，添加视频轨道到混合器")
            } else {
                // 获取一个只读的输出缓存区inputBuffer ，它包含被编码好的数据
                var outputBuffer: ByteBuffer? = null
                if (!isLollipop) {
                    outputBuffer = outputBuffers[outputBufferIndex]
                } else {
                    outputBuffer = mVideoEncodec!!.getOutputBuffer(outputBufferIndex)
                }
                // 如果API<=19，需要根据BufferInfo的offset偏移量调整ByteBuffer的位置
                // 并且限定将要读取缓存区数据的长度，否则输出数据会混乱
                if (isKITKAT) {
                    outputBuffer!!.position(mBufferInfo.offset)
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size)
                }

                val mMuxerUtils = muxerRunnableRf.get()
                mMuxerUtils?.addMuxerData(MediaMuxerUtils.MuxerData(
                        MediaMuxerUtils.TRACK_VIDEO, clone(outputBuffer),
                        mBufferInfo))

                mVideoEncodec!!.releaseOutputBuffer(outputBufferIndex, false)
            }
        } while (outputBufferIndex >= 0)
    }

    fun clone(original: ByteBuffer): ByteBuffer {
        val clone = ByteBuffer.allocate(original.capacity())
        original.rewind()//copy from the beginning
        clone.put(original)
        original.rewind()
        clone.flip()
        original.get()
        return clone
    }

    fun exit() {
        isExit = true
    }

    /**
     * 遍历所有编解码器，返回第一个与指定MIME类型匹配的编码器
     * 判断是否有支持指定mime类型的编码器
     */
    private fun selectSupportCodec(mimeType: String): MediaCodecInfo? {
        val numCodecs = MediaCodecList.getCodecCount()
        for (i in 0 until numCodecs) {
            val codecInfo = MediaCodecList.getCodecInfoAt(i)
            // 判断是否为编码器，否则直接进入下一次循环
            if (!codecInfo.isEncoder) {
                continue
            }
            // 如果是编码器，判断是否支持Mime类型
            val types = codecInfo.supportedTypes
            for (j in types.indices) {
                if (types[j].equals(mimeType, ignoreCase = true)) {
                    return codecInfo
                }
            }
        }
        return null
    }

    /**
     * 根据mime类型匹配编码器支持的颜色格式
     */
    private fun selectSupportColorFormat(mCodecInfo: MediaCodecInfo, mimeType: String): Int {
        val capabilities = mCodecInfo.getCapabilitiesForType(mimeType)
        for (i in capabilities.colorFormats.indices) {
            val colorFormat = capabilities.colorFormats[i]
            if (isCodecRecognizedFormat(colorFormat)) {
                return colorFormat
            }
        }
        return 0
    }

    private fun isCodecRecognizedFormat(colorFormat: Int): Boolean {
        when (colorFormat) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar, MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar -> return true
            else -> return false
        }
    }

    companion object {
        private val TAG = "EncoderVideoRunnable"
        private val MIME_TYPE = "video/avc"
        // 帧率
        private val FRAME_RATE = 6
        // 间隔1s插入一帧关键帧
        private val FRAME_INTERVAL = 1
        // 绑定编码器缓存区超时时间为10s
        private val TIMES_OUT = 10000
        // 码率
        private val BIT_RATE = CameraUtils.PREVIEW_WIDTH * CameraUtils.PREVIEW_HEIGHT * 3 * 8 * FRAME_RATE / 256

        fun NV21toI420SemiPlanar(nv21bytes: ByteArray, i420bytes: ByteArray,
                                 width: Int, height: Int) {
            System.arraycopy(nv21bytes, 0, i420bytes, 0, width * height)
            var i = width * height
            while (i < nv21bytes.size) {
                i420bytes[i] = nv21bytes[i + 1]
                i420bytes[i + 1] = nv21bytes[i]
                i += 2
            }
        }
    }
}
