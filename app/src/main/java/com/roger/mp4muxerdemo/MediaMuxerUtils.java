package com.roger.mp4muxerdemo;

import android.media.MediaCodec;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Vector;

public class MediaMuxerUtils {
    private static final String TAG = "MediaMuxerUtils";
    public static final int TRACK_VIDEO = 0;
    private boolean isMuxerStarted;
    private boolean isExit = false;
    private int videoTrack = -1;

    private Object lock = new Object();
    private Vector<MuxerData> mMuxerDatas;
    private EncoderVideoRunnable videoRunnable;
    private Thread mMuxerThread;
    private Thread mVideoThread;
    private boolean isFrontCamera;
    private static MediaMuxerUtils muxerUtils;

    private MediaMuxerUtils() {
    }

    public static MediaMuxerUtils getMuxerRunnableInstance() {
        if (muxerUtils == null) {
            muxerUtils = new MediaMuxerUtils();
        }
        return muxerUtils;
    }

    private void initMuxer() {
        mMuxerDatas = new Vector<>();
        videoRunnable = new EncoderVideoRunnable(new WeakReference<>(this));
        mVideoThread = new Thread(videoRunnable);
        videoRunnable.setFrontCamera(isFrontCamera);
        mVideoThread.start();
        isExit = false;
    }

    class MediaMuxerRunnable implements Runnable {
        @Override
        public void run() {
            initMuxer();
            while (!isExit) {
                // 混合器没有启动或数据缓存为空，则阻塞混合线程等待启动(数据输入)
                if (isMuxerStarted) {
                    // 从缓存读取数据写入混合器中
                    if (mMuxerDatas.isEmpty()) {
                        Log.w(TAG, "run--->混合器没有数据，阻塞线程等待");
                        synchronized (lock) {
                            try {
                                lock.wait();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        MuxerData data = mMuxerDatas.remove(0);
                        if (data != null) {
                            int track = 0;
                            try {
                                if (data.trackIndex == TRACK_VIDEO) {
                                    track = videoTrack;
                                    Log.d(TAG, "---写入视频数据---");
                                    MainActivity.sequenceEncoderMp4.encodeNativeFrame(data.byteBuf);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "写入数据到混合器失败，track=" + track);
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "run--->混合器没有启动，阻塞线程等待");
                    synchronized (lock) {
                        try {
                            lock.wait();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            stopMuxer();
        }
    }

    private void startMuxer() {
        if (!isMuxerStarted) {
            isMuxerStarted = true;
            synchronized (lock) {
                lock.notify();
            }
            Log.d(TAG, "---启动混合器---");
        }
    }

    private void stopMuxer() {
        Log.d(TAG, "---停止混合器---");
        if (isMuxerStarted) {
            isMuxerStarted = false;
        }
    }

    // 添加音、视频轨道
    public void setMediaFormat() {
        startMuxer();
    }

    // 向MediaMuxer添加数据
    public void addMuxerData(MuxerData data) {
        Log.d(TAG, "---向MediaMuxer添加数据---");
        if (mMuxerDatas == null) {
            Log.e(TAG, "添加数据失败");
            return;
        }
        mMuxerDatas.add(data);
        synchronized (lock) {
            lock.notify();
        }
    }

    // 添加图像数据到视频编码器
    public void addVideoFrameData(byte[] frameData) {
        if (videoRunnable != null) {
            videoRunnable.addData(frameData);
        }
    }

    public void startMuxerThread(boolean isFrontCamera) {
        this.isFrontCamera = isFrontCamera;
        if (mMuxerThread == null) {
            synchronized (MediaMuxerUtils.this) {
                mMuxerThread = new Thread(new MediaMuxerRunnable());
                mMuxerThread.start();
            }
        }
    }

    public void stopMuxerThread() {
        try {
            MainActivity.sequenceEncoderMp4.finish();
        } catch (Exception e) {
            e.printStackTrace();
        }
        exit();
        if (mMuxerThread != null) {
            try {
                mMuxerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mMuxerThread = null;
    }

    private void exit() {
        Log.d(TAG, "---停止混合器(录音、录像)线程---");
        if (videoRunnable != null) {
            videoRunnable.exit();
        }
        if (mVideoThread != null) {
            try {
                mVideoThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mVideoThread = null;
        }
        isExit = true;
        synchronized (lock) {
            lock.notify();
        }
    }

    public boolean isMuxerStarted() {
        return isMuxerStarted;
    }

    public static class MuxerData {
        int trackIndex;
        ByteBuffer byteBuf;
        MediaCodec.BufferInfo bufferInfo;

        public MuxerData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
            this.trackIndex = trackIndex;
            this.byteBuf = byteBuf;
            this.bufferInfo = bufferInfo;
        }
    }
}
