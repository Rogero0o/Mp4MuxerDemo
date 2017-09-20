package com.roger.mp4muxerdemo.jcodec;

import android.content.Context;
import android.util.Log;

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.common.FileChannelWrapper;
import org.jcodec.common.NIOUtils;
import org.jcodec.common.SeekableByteChannel;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.TrackType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.jcodec.codecs.h264.H264Utils.createMOVSampleEntry;

/**
 * projectName: 	    Jcodec2
 * packageName:	        com.luoxiang.org.jcodec
 * className:	        SequenceEncoderMp4
 * author:	            Luoxiang
 * time:	            2017/1/6	17:40
 * desc:	            TODO
 * <p>
 * svnVersion:	        $Rev
 * upDateAuthor:	    Vincent
 * upDate:	            2017/1/6
 * upDateDesc:	        TODO
 */

public class SequenceEncoderMp4 {

    /**
     * 控制帧率缩放,时间长度调整这个值
     * 值越小,生成视频时间越长
     * 如:timeScale = 50     一秒视屏采用50张图片
     */
    private int timeScale = 6;
    private Context context;

//    public void encodeImage(Bitmap bi) throws IOException {
//        encodeNativeFrame(org.jcodec.scale.BitmapUtil.fromBitmap(bi));
//    }


    public SequenceEncoderMp4(File out, Context context)
            throws IOException {

        this.context = context;
        // Muxer that will store the encoded frames
        long size = 0;

        try {
            size = getFileSize(out);
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.ch = new FileChannelWrapper(new RandomAccessFile(out, "rw").getChannel());
        if (size > 100) {
            ch.position(size);
        }

        muxer = new MyMuxerMp4(ch, Brand.MP4, size < 100);

        // Add video track to muxer
        outTrack = muxer.addTrack(TrackType.VIDEO, timeScale);
        outTrack.setContext(context);

        // Encoder extra data ( SPS, PPS ) to be stored in a special place of
        // MP4
        spsList = new ArrayList<>();
        ppsList = new ArrayList<>();

        isFinished = false;

        if (size > 100) {
            setFrameNo((int) ListCache.getInstance(context).getLastIndex());
        }
    }

    private SeekableByteChannel ch;
    private ArrayList<ByteBuffer> spsList;
    private ArrayList<ByteBuffer> ppsList;
    private FramesMP4MuxerTrack2 outTrack;
    private int frameNo;
    private MyMuxerMp4 muxer;


    public void encodeNativeFrame(ByteBuffer re) throws IOException {
        ByteBuffer result = clone(re);
        int type = re.get(4) & 0x1F;
        if (type == 7 || type == 8) {//read sps and pps and save it
            if (spsList.size() == 0) {
                spsList.clear();
                ppsList.clear();
                wipePS(result, spsList, ppsList);
                saveSpsPPS();
            }
        } else {
            H264Utils.encodeMOVPacket(result);
            // Add packet to video track
            if (type == 5) {
                outTrack.addFrame(new MP4Packet(result, frameNo, timeScale, 1, frameNo, true, null, frameNo, 0));
            } else {
                outTrack.addFrame(new MP4Packet(result, frameNo, timeScale, 1, frameNo, false, null, frameNo, 0));
            }
            frameNo++;
        }
    }

    private void saveSpsPPS() {
        ByteBuffer bb = clone(spsList.get(0));
        byte[] b = new byte[bb.remaining()];
        bb.get(b, 0, b.length);
        ListCache.getInstance(context).saveSPS(b);

        ByteBuffer bbp = clone(ppsList.get(0));
        byte[] bp = new byte[bbp.remaining()];
        bbp.get(bp, 0, bp.length);
        ListCache.getInstance(context).savePPS(bp);
    }


    public static void wipePS(ByteBuffer in, List<ByteBuffer> spsList, List<ByteBuffer> ppsList) {
        ByteBuffer dup = in.duplicate();
        while (dup.hasRemaining()) {
            ByteBuffer buf = H264Utils.nextNALUnit(dup);
            if (buf == null) {
                break;
            }
            NALUnit nu = NALUnit.read(buf);
            if (nu.type == NALUnitType.PPS) {
                if (ppsList != null)
                    ppsList.add(buf);
                in.position(dup.position());
            } else if (nu.type == NALUnitType.SPS) {
                if (spsList != null)
                    spsList.add(buf);
                in.position(dup.position());
            } else if (nu.type == NALUnitType.IDR_SLICE || nu.type == NALUnitType.NON_IDR_SLICE) {
                break;
            }
        }
    }

    public static ByteBuffer clone(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();//copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        original.get();
        return clone;
    }

    public void setFrameNo(int num) {
        frameNo = num;

        try {
            Class userCla = (Class) outTrack.getClass();
            Field[] fs = userCla.getDeclaredFields();
            for (int i = 0; i < fs.length; i++) {
                Field f = fs[i];
                f.setAccessible(true);
                String type = f.getType().toString();
                if (f.getName().equals("curFrame") && type.endsWith("int")) {
                    f.set(outTrack, num);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        outTrack.setTrackTotalDuration(num + 1);
    }

    private boolean isFinished = false;

    public void finish() throws IOException {

        if (isFinished) {
            return;
        }
        isFinished = true;
        // Push saved SPS/PPS to a special storage in MP4
        if (spsList.size() == 0) {
            ByteBuffer spsbuf = ByteBuffer.wrap(ListCache.getInstance(context).getSPS());
            spsbuf.get();
            spsList.add(spsbuf);
            ByteBuffer ppsbuf = ByteBuffer.wrap(ListCache.getInstance(context).getPPS());
            ppsbuf.get();
            ppsList.add(ppsbuf);
        }
        outTrack.addSampleEntry(createMOVSampleEntry(spsList, ppsList, 4));
        // Write MP4 header and finalize recording
        muxer.writeHeader();
        NIOUtils.closeQuietly(ch);
    }

    public static long getFileSize(File file) throws Exception {
        long size = 0;
        if (file.exists()) {
            FileInputStream fis = null;
            fis = new FileInputStream(file);
            size = fis.available();
        } else {
            file.createNewFile();
            Log.e("获取文件大小", "文件不存在!");
        }
        return size;
    }
}
