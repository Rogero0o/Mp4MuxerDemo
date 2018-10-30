package com.roger.mp4muxerdemo

import android.media.MediaCodec
import android.util.Log

import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.Vector

class MediaMuxerUtils private constructor() {
    private var isMuxerStarted: Boolean = false
    private var isExit = false
    private val videoTrack = -1

    private val lock = java.lang.Object()
    private var mMuxerDatas: Vector<MuxerData>? = null
    private var videoRunnable: EncoderVideoRunnable? = null
    private var mMuxerThread: Thread? = null
    private var mVideoThread: Thread? = null
    private var isFrontCamera: Boolean = false

    private fun initMuxer() {
        mMuxerDatas = Vector()
        videoRunnable = EncoderVideoRunnable(WeakReference(this))
        mVideoThread = Thread(videoRunnable)
        videoRunnable!!.isFrontCamera = isFrontCamera
        mVideoThread!!.start()
        isExit = false
    }

    internal inner class MediaMuxerRunnable : Runnable {
        override fun run() {
            initMuxer()
            while (!isExit) {
                // 混合器没有启动或数据缓存为空，则阻塞混合线程等待启动(数据输入)
                if (isMuxerStarted) {
                    // 从缓存读取数据写入混合器中
                    if (mMuxerDatas!!.isEmpty()) {
                        Log.w(TAG, "run--->混合器没有数据，阻塞线程等待")
                        synchronized(lock) {
                            try {
                                lock.wait()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                        }
                    } else {
                        val data = mMuxerDatas!!.removeAt(0)
                        if (data != null) {
                            var track = 0
                            try {
                                if (data.trackIndex == TRACK_VIDEO) {
                                    track = videoTrack
                                    Log.d(TAG, "---写入视频数据---")
                                    MainActivity.sequenceEncoderMp4!!.encodeNativeFrame(data.byteBuf)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "写入数据到混合器失败，track=$track")
                                e.printStackTrace()
                            }

                        }
                    }
                } else {
                    Log.w(TAG, "run--->混合器没有启动，阻塞线程等待")
                    synchronized(lock) {
                        try {
                            lock.wait()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                    }
                }
            }
            stopMuxer()
        }
    }

    private fun startMuxer() {
        if (!isMuxerStarted) {
            isMuxerStarted = true
            synchronized(lock) {
                lock.notify()
            }
            Log.d(TAG, "---启动混合器---")
        }
    }

    private fun stopMuxer() {
        Log.d(TAG, "---停止混合器---")
        if (isMuxerStarted) {
            isMuxerStarted = false
        }
    }

    // 添加音、视频轨道
    fun setMediaFormat() {
        startMuxer()
    }

    // 向MediaMuxer添加数据
    fun addMuxerData(data: MuxerData) {
        Log.d(TAG, "---向MediaMuxer添加数据---")
        if (mMuxerDatas == null) {
            Log.e(TAG, "添加数据失败")
            return
        }
        mMuxerDatas!!.add(data)
        synchronized(lock) {
            lock.notify()
        }
    }

    // 添加图像数据到视频编码器
    fun addVideoFrameData(frameData: ByteArray) {
        if (videoRunnable != null) {
            videoRunnable!!.addData(frameData)
        }
    }

    fun startMuxerThread(isFrontCamera: Boolean) {
        this.isFrontCamera = isFrontCamera
        if (mMuxerThread == null) {
            synchronized(this@MediaMuxerUtils) {
                mMuxerThread = Thread(MediaMuxerRunnable())
                mMuxerThread!!.start()
            }
        }
    }

    fun stopMuxerThread() {
        try {
            MainActivity.sequenceEncoderMp4!!.finish()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        exit()
        if (mMuxerThread != null) {
            try {
                mMuxerThread!!.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

        }
        mMuxerThread = null
    }

    private fun exit() {
        Log.d(TAG, "---停止混合器(录音、录像)线程---")
        if (videoRunnable != null) {
            videoRunnable!!.exit()
        }
        if (mVideoThread != null) {
            try {
                mVideoThread!!.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            mVideoThread = null
        }
        isExit = true
        synchronized(lock) {
            lock.notify()
        }
    }


    class MuxerData(internal var trackIndex: Int, internal var byteBuf: ByteBuffer, internal var bufferInfo: MediaCodec.BufferInfo)

    companion object {
        private val TAG = "MediaMuxerUtils"
        val TRACK_VIDEO = 0
        private var muxerUtils: MediaMuxerUtils? = null

        val muxerRunnableInstance: MediaMuxerUtils
            get() {
                if (muxerUtils == null) {
                    muxerUtils = MediaMuxerUtils()
                }
                return muxerUtils!!
            }
    }
}
