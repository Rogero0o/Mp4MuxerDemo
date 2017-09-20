package com.roger.mp4muxerdemo;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import com.roger.mp4muxerdemo.utils.CameraUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Vector;

public class EncoderVideoRunnable implements Runnable {
    private static final String TAG = "EncoderVideoRunnable";
    private static final String MIME_TYPE = "video/avc";
    // 帧率
    private static final int FRAME_RATE = 6;
    // 间隔1s插入一帧关键帧
    private static final int FRAME_INTERVAL = 1;
    // 绑定编码器缓存区超时时间为10s
    private static final int TIMES_OUT = 10000;
    // 码率
    private static final int BIT_RATE = CameraUtils.PREVIEW_WIDTH * CameraUtils.PREVIEW_HEIGHT * 3 * 8 * FRAME_RATE / 256;
    // 正常垂直方向拍摄
    private boolean isPhoneHorizontal = true;

    // MP4混合器
    private WeakReference<MediaMuxerUtils> muxerRunnableRf;
    // 硬编码器
    private MediaCodec mVideoEncodec;
    private int mColorFormat;
    private boolean isExit = false;
    private boolean isEncoderStart = false;

    private Vector<byte[]> frameBytes;
    private byte[] mFrameData;
    private boolean isFrontCamera;
    private long prevPresentationTimes;
    private MediaFormat mFormat;

    public EncoderVideoRunnable(WeakReference<MediaMuxerUtils> muxerRunnableRf) {
        this.muxerRunnableRf = muxerRunnableRf;
        frameBytes = new Vector<byte[]>();
        mFrameData = new byte[CameraUtils.PREVIEW_WIDTH * CameraUtils.PREVIEW_HEIGHT * 3 / 2];
        initMediaFormat();
    }

    private void initMediaFormat() {
        try {
            MediaCodecInfo mCodecInfo = selectSupportCodec(MIME_TYPE);
            if (mCodecInfo == null) {
                Log.d(TAG, "匹配编码器失败" + MIME_TYPE);
                return;
            }
            mColorFormat = selectSupportColorFormat(mCodecInfo, MIME_TYPE);
            Log.d(TAG, "mColorFormat:" + mColorFormat);
            // NV21->I420
            mVideoEncodec = MediaCodec.createByCodecName(mCodecInfo.getName());
        } catch (IOException e) {
            Log.e(TAG, "创建编码器失败" + e.getMessage());
            e.printStackTrace();
        }
        if (!isPhoneHorizontal) {
            mFormat = MediaFormat.createVideoFormat(MIME_TYPE, CameraUtils.PREVIEW_HEIGHT, CameraUtils.PREVIEW_WIDTH);
        } else {
            mFormat = MediaFormat.createVideoFormat(MIME_TYPE, CameraUtils.PREVIEW_WIDTH, CameraUtils.PREVIEW_HEIGHT);
        }
        mFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat); // 颜色格式
        mFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, FRAME_INTERVAL);
    }

    private void startCodec() {
        frameBytes.clear();
        isExit = false;
        if (mVideoEncodec != null) {
            mVideoEncodec.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mVideoEncodec.start();
            isEncoderStart = true;
            Log.d(TAG, "配置、启动视频编码器");
        }
    }

    private void stopCodec() {
        if (mVideoEncodec != null) {
            mVideoEncodec.stop();
            mVideoEncodec.release();
            mVideoEncodec = null;
            isEncoderStart = false;
            Log.d(TAG, "关闭视频编码器");
        }
    }

    public void addData(byte[] yuvData) {
        if (frameBytes != null) {
            frameBytes.add(yuvData);
        }
    }

    @Override
    public void run() {
        if (!isEncoderStart) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            startCodec();
        }
        // 如果编码器没有启动或者没有图像数据，线程阻塞等待
        while (!isExit) {
            if (!frameBytes.isEmpty()) {
                byte[] bytes = frameBytes.remove(0);
                try {
                    encoderBytes(bytes);
                } catch (IllegalStateException e) {
                    // 捕获因中断线程并停止混合dequeueOutputBuffer报的状态异常
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    // 捕获因中断线程并停止混合MediaCodec为NULL异常
                    e.printStackTrace();
                }
            }
        }
        stopCodec();
    }

    @SuppressLint("NewApi")
    private void encoderBytes(byte[] rawFrame) {
        ByteBuffer[] inputBuffers = mVideoEncodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mVideoEncodec.getOutputBuffers();
        //前置摄像头旋转270度，后置摄像头旋转90度
        int mWidth = CameraUtils.PREVIEW_WIDTH;
        int mHeight = CameraUtils.PREVIEW_HEIGHT;
        NV21toI420SemiPlanar(rawFrame, mFrameData, mWidth, mHeight);
        //返回编码器的一个输入缓存区句柄，-1表示当前没有可用的输入缓存区
        int inputBufferIndex = mVideoEncodec.dequeueInputBuffer(TIMES_OUT);
        if (inputBufferIndex >= 0) {
            // 绑定一个被空的、可写的输入缓存区inputBuffer到客户端
            ByteBuffer inputBuffer = null;
            if (!isLollipop()) {
                inputBuffer = inputBuffers[inputBufferIndex];
            } else {
                inputBuffer = mVideoEncodec.getInputBuffer(inputBufferIndex);
            }
            // 向输入缓存区写入有效原始数据，并提交到编码器中进行编码处理
            inputBuffer.clear();
            inputBuffer.put(mFrameData);
            mVideoEncodec.queueInputBuffer(inputBufferIndex, 0, mFrameData.length, getPTSUs(), 0);
        }

        // 返回一个输出缓存区句柄，当为-1时表示当前没有可用的输出缓存区
        // mBufferInfo参数包含被编码好的数据，timesOut参数为超时等待的时间
        MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = -1;
        do {
            outputBufferIndex = mVideoEncodec.dequeueOutputBuffer(mBufferInfo, TIMES_OUT);
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.i(TAG, "获得编码器输出缓存区超时");
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // 如果API小于21，APP需要重新绑定编码器的输入缓存区；
                // 如果API大于21，则无需处理INFO_OUTPUT_BUFFERS_CHANGED
                if (!isLollipop()) {
                    outputBuffers = mVideoEncodec.getOutputBuffers();
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // 编码器输出缓存区格式改变，通常在存储数据之前且只会改变一次
                // 这里设置混合器视频轨道，如果音频已经添加则启动混合器（保证音视频同步）
                MediaFormat newFormat = mVideoEncodec.getOutputFormat();
                MediaMuxerUtils mMuxerUtils = muxerRunnableRf.get();
                if (mMuxerUtils != null) {
                    mMuxerUtils.setMediaFormat();
                }
                Log.i(TAG, "编码器输出缓存区格式改变，添加视频轨道到混合器");
            } else {
                // 获取一个只读的输出缓存区inputBuffer ，它包含被编码好的数据
                ByteBuffer outputBuffer = null;
                if (!isLollipop()) {
                    outputBuffer = outputBuffers[outputBufferIndex];
                } else {
                    outputBuffer = mVideoEncodec.getOutputBuffer(outputBufferIndex);
                }
                // 如果API<=19，需要根据BufferInfo的offset偏移量调整ByteBuffer的位置
                // 并且限定将要读取缓存区数据的长度，否则输出数据会混乱
                if (isKITKAT()) {
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);
                }

                MediaMuxerUtils mMuxerUtils = muxerRunnableRf.get();
                mMuxerUtils.addMuxerData(new MediaMuxerUtils.MuxerData(
                        MediaMuxerUtils.TRACK_VIDEO, clone(outputBuffer),
                        mBufferInfo));

                // 根据NALU类型判断帧类型
//                int type = outputBuffer.get(4) & 0x1F;
//                Log.d(TAG, "------还有数据---->" + type);
//                if (type == 7 || type == 8) {
//                    Log.e(TAG, "------PPS、SPS帧(非图像数据)，忽略-------");
//                    if (mMuxerUtils != null && mMuxerUtils.isMuxerStarted()) {
//                        mMuxerUtils.addMuxerData(new MediaMuxerUtils.MuxerData(
//                                MediaMuxerUtils.TRACK_VIDEO, outputBuffer,
//                                mBufferInfo));
//                        prevPresentationTimes = mBufferInfo.presentationTimeUs;
//                        isAddKeyFrame = true;
//                        Log.e(TAG, "----------->添加关键帧到混合器");
//                    }
////					mBufferInfo.size = 0;
//                } else if (type == 5) {
//                    // 录像时，第1秒画面会静止，这是由于音视轨没有完全被添加
//                    // Muxer没有启动
//                    Log.e(TAG, "------I帧(关键帧)-------");
//                    if (mMuxerUtils != null && mMuxerUtils.isMuxerStarted()) {
//                        mMuxerUtils.addMuxerData(new MediaMuxerUtils.MuxerData(
//                                MediaMuxerUtils.TRACK_VIDEO, outputBuffer,
//                                mBufferInfo));
//                        prevPresentationTimes = mBufferInfo.presentationTimeUs;
//                        isAddKeyFrame = true;
//                        Log.e(TAG, "----------->添加关键帧到混合器");
//                    }
//                } else {
//                    if (isAddKeyFrame) {
//                        Log.d(TAG, "------非I帧(type=1)，添加到混合器-------");
//                        if (mMuxerUtils != null && mMuxerUtils.isMuxerStarted()) {
//                            mMuxerUtils.addMuxerData(new MediaMuxerUtils.MuxerData(
//                                    MediaMuxerUtils.TRACK_VIDEO, outputBuffer,
//                                    mBufferInfo));
//                            prevPresentationTimes = mBufferInfo.presentationTimeUs;
//                            Log.d(TAG, "------添加到混合器");
//                        }
//                    }
//                }
                // 处理结束，释放输出缓存区资源
                mVideoEncodec.releaseOutputBuffer(outputBufferIndex, false);
            }
        } while (outputBufferIndex >= 0);
    }

    public ByteBuffer clone(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();//copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        original.get();
        return clone;
    }

    public void exit() {
        isExit = true;
    }

    /**
     * 遍历所有编解码器，返回第一个与指定MIME类型匹配的编码器
     * 判断是否有支持指定mime类型的编码器
     */
    private MediaCodecInfo selectSupportCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            // 判断是否为编码器，否则直接进入下一次循环
            if (!codecInfo.isEncoder()) {
                continue;
            }
            // 如果是编码器，判断是否支持Mime类型
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    public boolean isFrontCamera() {
        return isFrontCamera;
    }

    public void setFrontCamera(boolean isFrontCamera) {
        this.isFrontCamera = isFrontCamera;
    }

    /**
     * 根据mime类型匹配编码器支持的颜色格式
     */
    private int selectSupportColorFormat(MediaCodecInfo mCodecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = mCodecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isCodecRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        return 0;
    }

    private boolean isCodecRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    private boolean isLollipop() {
        // API>=21
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private boolean isKITKAT() {
        // API<=19
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT;
    }

    private long getPTSUs() {
        long result = System.nanoTime() / 1000;
        if (result < prevPresentationTimes) {
            result = (prevPresentationTimes - result) + result;
        }
        return result;
    }

    public static void NV21toI420SemiPlanar(byte[] nv21bytes, byte[] i420bytes,
                                            int width, int height) {
        System.arraycopy(nv21bytes, 0, i420bytes, 0, width * height);
        for (int i = width * height; i < nv21bytes.length; i += 2) {
            i420bytes[i] = nv21bytes[i + 1];
            i420bytes[i + 1] = nv21bytes[i];
        }
    }
}
