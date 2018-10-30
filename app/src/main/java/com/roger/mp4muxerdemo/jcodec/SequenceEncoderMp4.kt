package com.roger.mp4muxerdemo.jcodec

import android.content.Context
import android.util.Log

import org.jcodec.codecs.h264.H264Utils
import org.jcodec.codecs.h264.io.model.NALUnit
import org.jcodec.codecs.h264.io.model.NALUnitType
import org.jcodec.common.FileChannelWrapper
import org.jcodec.common.NIOUtils
import org.jcodec.common.SeekableByteChannel
import org.jcodec.containers.mp4.Brand
import org.jcodec.containers.mp4.MP4Packet
import org.jcodec.containers.mp4.TrackType

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.util.ArrayList

import org.jcodec.codecs.h264.H264Utils.createMOVSampleEntry

/**
 * projectName: 	    Jcodec2
 * packageName:	        com.luoxiang.org.jcodec
 * className:	        SequenceEncoderMp4
 * author:	            Luoxiang
 * time:	            2017/1/6	17:40
 * desc:	            TODO
 *
 *
 * svnVersion:	        $Rev
 * upDateAuthor:	    Vincent
 * upDate:	            2017/1/6
 * upDateDesc:	        TODO
 */

class SequenceEncoderMp4
//    public void encodeImage(Bitmap bi) throws IOException {
//        encodeNativeFrame(org.jcodec.scale.BitmapUtil.fromBitmap(bi));
//    }


@Throws(IOException::class)
constructor(out: File, private val context: Context) {

    /**
     * 控制帧率缩放,时间长度调整这个值
     * 值越小,生成视频时间越长
     * 如:timeScale = 50     一秒视屏采用50张图片
     */
    private val timeScale = 6

    private val ch: SeekableByteChannel
    private val spsList: ArrayList<ByteBuffer>
    private val ppsList: ArrayList<ByteBuffer>
    private val outTrack: FramesMP4MuxerTrack2
    private var frameNo: Int = 0
    private val muxer: MyMuxerMp4

    private var isFinished = false

    init {
        // Muxer that will store the encoded frames
        var size: Long = 0

        try {
            size = getFileSize(out)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        this.ch = FileChannelWrapper(RandomAccessFile(out, "rw").channel)
        if (size > 100) {
            ch.position(size)
        }

        muxer = MyMuxerMp4(ch, Brand.MP4, size < 100)

        // Add video track to muxer
        outTrack = muxer.addTrack(TrackType.VIDEO, timeScale)
        outTrack.setContext(context)

        // Encoder extra data ( SPS, PPS ) to be stored in a special place of
        // MP4
        spsList = ArrayList()
        ppsList = ArrayList()

        isFinished = false

        if (size > 100) {
            setFrameNo(ListCache.getInstance(context)!!.lastIndex.toInt())
        }
    }


    @Throws(IOException::class)
    fun encodeNativeFrame(re: ByteBuffer) {
        val result = clone(re)
        val type = re.get(4).toInt() and 0x1F
        if (type == 7 || type == 8) {//read sps and pps and save it
            if (spsList.size == 0) {
                spsList.clear()
                ppsList.clear()
                wipePS(result, spsList, ppsList)
                saveSpsPPS()
            }
        } else {
            H264Utils.encodeMOVPacket(result)
            // Add packet to video track
            if (type == 5) {
                outTrack.addFrame(MP4Packet(result, frameNo.toLong(), timeScale.toLong(), 1, frameNo.toLong(), true, null, frameNo.toLong(), 0))
            } else {
                outTrack.addFrame(MP4Packet(result, frameNo.toLong(), timeScale.toLong(), 1, frameNo.toLong(), false, null, frameNo.toLong(), 0))
            }
            frameNo++
        }
    }

    private fun saveSpsPPS() {
        val bb = clone(spsList[0])
        val b = ByteArray(bb.remaining())
        bb.get(b, 0, b.size)
        ListCache.getInstance(context)!!.saveSPS(b)

        val bbp = clone(ppsList[0])
        val bp = ByteArray(bbp.remaining())
        bbp.get(bp, 0, bp.size)
        ListCache.getInstance(context)!!.savePPS(bp)
    }

    fun setFrameNo(num: Int) {
        frameNo = num

        try {
            val userCla = outTrack.javaClass as Class<*>
            val fs = userCla.declaredFields
            for (i in fs.indices) {
                val f = fs[i]
                f.isAccessible = true
                val type = f.type.toString()
                if (f.name == "curFrame" && type.endsWith("int")) {
                    f.set(outTrack, num)
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        outTrack.trackTotalDuration = (num + 1).toLong()
    }

    @Throws(IOException::class)
    fun finish() {

        if (isFinished) {
            return
        }
        isFinished = true
        // Push saved SPS/PPS to a special storage in MP4
        if (spsList.size == 0) {
            val spsbuf = ByteBuffer.wrap(ListCache.getInstance(context)!!.pps)
            spsbuf.get()
            spsList.add(spsbuf)
            val ppsbuf = ByteBuffer.wrap(ListCache.getInstance(context)!!.pps)
            ppsbuf.get()
            ppsList.add(ppsbuf)
        }
        outTrack.addSampleEntry(createMOVSampleEntry(spsList, ppsList, 4))
        // Write MP4 header and finalize recording
        muxer.writeHeader()
        NIOUtils.closeQuietly(ch)
    }

    companion object {


        fun wipePS(`in`: ByteBuffer, spsList: MutableList<ByteBuffer>?, ppsList: MutableList<ByteBuffer>?) {
            val dup = `in`.duplicate()
            while (dup.hasRemaining()) {
                val buf = H264Utils.nextNALUnit(dup) ?: break
                val nu = NALUnit.read(buf)
                if (nu.type == NALUnitType.PPS) {
                    ppsList?.add(buf)
                    `in`.position(dup.position())
                } else if (nu.type == NALUnitType.SPS) {
                    spsList?.add(buf)
                    `in`.position(dup.position())
                } else if (nu.type == NALUnitType.IDR_SLICE || nu.type == NALUnitType.NON_IDR_SLICE) {
                    break
                }
            }
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

        @Throws(Exception::class)
        fun getFileSize(file: File): Long {
            var size: Long = 0
            if (file.exists()) {
                var fis: FileInputStream? = null
                fis = FileInputStream(file)
                size = fis.available().toLong()
            } else {
                file.createNewFile()
                Log.e("获取文件大小", "文件不存在!")
            }
            return size
        }
    }
}
