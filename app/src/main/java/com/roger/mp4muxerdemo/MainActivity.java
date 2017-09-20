package com.roger.mp4muxerdemo;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.roger.mp4muxerdemo.jcodec.ListCache;
import com.roger.mp4muxerdemo.jcodec.SequenceEncoderMp4;
import com.roger.mp4muxerdemo.utils.CameraUtils;
import com.roger.mp4muxerdemo.utils.PermissionUtils;

import java.io.File;
import java.io.IOException;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private Button mBtnRecord;
    private SurfaceView mSurfaceView;
    private CameraUtils mCamManager;
    private boolean isRecording;


    public static SequenceEncoderMp4 sequenceEncoderMp4 = null;

    private File out;
    private Handler mHandler;
    String FILE_FOLDER = Environment.getExternalStorageDirectory()
            .getAbsolutePath() + File.separator + "Mp4MuxerDemo";

    private boolean isPreviewCatch;

    private CameraUtils.OnPreviewFrameResult mPreviewListener = new CameraUtils.OnPreviewFrameResult() {
        @Override
        public void onPreviewResult(byte[] data, Camera camera) {
            mCamManager.getCameraIntance().addCallbackBuffer(data);
            if (isPreviewCatch) {
                Log.d("Tag", "---isPreviewCatch---:" + data);
                MediaMuxerUtils.getMuxerRunnableInstance().addVideoFrameData(data);
                isPreviewCatch = false;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCamManager = CameraUtils.getCamManagerInstance(MainActivity.this);
        mSurfaceView = (SurfaceView) findViewById(R.id.main_record_surface);

        mSurfaceView.getHolder().addCallback(this);
        mSurfaceView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mCamManager.cameraFocus(new CameraUtils.OnCameraFocusResult() {
                    @Override
                    public void onFocusResult(boolean result) {
                        if (result) {
                            Toast.makeText(MainActivity.this, "对焦成功", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });

        mBtnRecord = (Button) findViewById(R.id.main_record_btn);
        mBtnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MediaMuxerUtils mMuxerUtils = MediaMuxerUtils.getMuxerRunnableInstance();
                if (!isRecording) {
                    mMuxerUtils.startMuxerThread(mCamManager.getCameraDirection());
                    mBtnRecord.setText("停止录像");
                } else {
                    //停止录像并生成mp4文件
                    mMuxerUtils.stopMuxerThread();
                    mBtnRecord.setText("开始录像");
                }
                isRecording = !isRecording;
            }
        });

        findViewById(R.id.main_delete_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (out.exists()) {
                        out.delete();
                        out.createNewFile();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.main_shengcheng_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    sequenceEncoderMp4.setFrameNo((int) ListCache.getInstance(MainActivity.this).getLastIndex());
                    sequenceEncoderMp4.finish();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        out = new File(FILE_FOLDER, "jcodec_enc.mp4");
        if (!out.getParentFile().exists()) {
            out.getParentFile().mkdirs();
        }
        try {
            sequenceEncoderMp4 = new SequenceEncoderMp4(out, this);
        } catch (IOException e) {
            e.printStackTrace();
        }


        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                isPreviewCatch = true;
                mHandler.sendEmptyMessageDelayed(0, 1000);
            }
        };
        mHandler.sendEmptyMessageDelayed(0, 1000);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        showCamera(mSurfaceView);
        mCamManager.setSurfaceHolder(surfaceHolder);
        mCamManager.setOnPreviewResult(mPreviewListener);
        mCamManager.createCamera();
        mCamManager.startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mCamManager.stopPreivew();
        mCamManager.destoryCamera();
    }

    public void showCamera(View view) {
        PermissionUtils.requestPermission(this, PermissionUtils.CODE_CAMERA, mPermissionGrant);
    }


    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        PermissionUtils.requestPermissionsResult(this, requestCode, permissions, grantResults, mPermissionGrant);

    }

    private PermissionUtils.PermissionGrant mPermissionGrant = new PermissionUtils.PermissionGrant() {
        @Override
        public void onPermissionGranted(int requestCode) {
            switch (requestCode) {
                case PermissionUtils.CODE_RECORD_AUDIO:
                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_RECORD_AUDIO", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_GET_ACCOUNTS:
                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_GET_ACCOUNTS", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_READ_PHONE_STATE:
                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_READ_PHONE_STATE", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_CALL_PHONE:
                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_CALL_PHONE", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_CAMERA:
                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_CAMERA", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_ACCESS_FINE_LOCATION:
                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_ACCESS_FINE_LOCATION", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_ACCESS_COARSE_LOCATION:
                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_ACCESS_COARSE_LOCATION", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_READ_EXTERNAL_STORAGE:
                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_READ_EXTERNAL_STORAGE", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_WRITE_EXTERNAL_STORAGE:
                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_WRITE_EXTERNAL_STORAGE", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        }
    };
}
