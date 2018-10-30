package com.roger.mp4muxerdemo.jcodec

import org.jcodec.common.AudioFormat
import org.jcodec.common.NIOUtils
import org.jcodec.common.SeekableByteChannel
import org.jcodec.common.model.Size
import org.jcodec.containers.mp4.Brand
import org.jcodec.containers.mp4.MP4Util
import org.jcodec.containers.mp4.TrackType
import org.jcodec.containers.mp4.boxes.AudioSampleEntry
import org.jcodec.containers.mp4.boxes.Box
import org.jcodec.containers.mp4.boxes.EndianBox
import org.jcodec.containers.mp4.boxes.FileTypeBox
import org.jcodec.containers.mp4.boxes.FormatBox
import org.jcodec.containers.mp4.boxes.Header
import org.jcodec.containers.mp4.boxes.LeafBox
import org.jcodec.containers.mp4.boxes.MovieBox
import org.jcodec.containers.mp4.boxes.MovieHeaderBox
import org.jcodec.containers.mp4.boxes.NodeBox
import org.jcodec.containers.mp4.boxes.SampleEntry
import org.jcodec.containers.mp4.boxes.VideoSampleEntry
import org.jcodec.containers.mp4.muxer.AbstractMP4MuxerTrack
import org.jcodec.containers.mp4.muxer.MP4Muxer
import org.jcodec.containers.mp4.muxer.PCMMP4MuxerTrack
import org.jcodec.containers.mp4.muxer.TimecodeMP4MuxerTrack

import java.io.IOException
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.Date

import org.jcodec.containers.mp4.TrackType.SOUND
import org.jcodec.containers.mp4.TrackType.VIDEO

/**
 * Created by roger on 29/08/2017.
 */

class MyMuxerMp4 @Throws(IOException::class)
constructor(protected var out: SeekableByteChannel, ftyp: FileTypeBox, reWrite: Boolean) {
    private val tracks = ArrayList<AbstractMP4MuxerTrack>()
    protected var mdatOffset: Long = 0

    private var nextTrackId = 1
    private val reWrite: Boolean = false

    val videoTrack: AbstractMP4MuxerTrack?
        get() {
            for (frameMuxer in tracks) {
                if (frameMuxer.isVideo) {
                    return frameMuxer
                }
            }
            return null
        }

    val timecodeTrack: AbstractMP4MuxerTrack?
        get() {
            for (frameMuxer in tracks) {
                if (frameMuxer.isTimecode) {
                    return frameMuxer
                }
            }
            return null
        }

    val audioTracks: List<AbstractMP4MuxerTrack>
        get() {
            val result = ArrayList<AbstractMP4MuxerTrack>()
            for (frameMuxer in tracks) {
                if (frameMuxer.isAudio) {
                    result.add(frameMuxer)
                }
            }
            return result
        }

    @Throws(IOException::class)
    @JvmOverloads constructor(output: SeekableByteChannel, brand: Brand = Brand.MP4, reWrite: Boolean = true) : this(output, brand.fileTypeBox, reWrite) {
    }

    init {
        if (reWrite) {
            val buf = ByteBuffer.allocate(1024)
            ftyp.write(buf)
            Header("wide", 8).write(buf)
            Header("mdat", 1).write(buf)
            mdatOffset = buf.position().toLong()
            buf.putLong(0)
            buf.flip()
            out.write(buf)
        }
    }

    fun addVideoTrackWithTimecode(fourcc: String, size: Size, encoderName: String, timescale: Int): FramesMP4MuxerTrack2 {
        val timecode = addTimecodeTrack(timescale)

        val track = addTrack(VIDEO, timescale)

        track.addSampleEntry(videoSampleEntry(fourcc, size, encoderName))
        track.setTimecode(timecode)

        return track
    }

    fun addVideoTrack(fourcc: String, size: Size, encoderName: String, timescale: Int): FramesMP4MuxerTrack2 {
        val track = addTrack(VIDEO, timescale)

        track.addSampleEntry(videoSampleEntry(fourcc, size, encoderName))
        return track
    }

    fun addTimecodeTrack(timescale: Int): TimecodeMP4MuxerTrack {
        val track = TimecodeMP4MuxerTrack(out, nextTrackId++, timescale)
        tracks.add(track)
        return track
    }

    fun addTrack(type: TrackType, timescale: Int): FramesMP4MuxerTrack2 {
        val track = FramesMP4MuxerTrack2(out, nextTrackId++, type, timescale)
        tracks.add(track)
        return track
    }

    fun addPCMTrack(timescale: Int, sampleDuration: Int, sampleSize: Int,
                    se: SampleEntry): PCMMP4MuxerTrack {
        val track = PCMMP4MuxerTrack(out, nextTrackId++, SOUND, timescale, sampleDuration, sampleSize, se)
        tracks.add(track)
        return track
    }

    fun getTracks(): List<AbstractMP4MuxerTrack> {
        return tracks
    }

    @Throws(IOException::class)
    fun writeHeader() {
        val movie = finalizeHeader()

        storeHeader(movie)
    }

    @Throws(IOException::class)
    fun storeHeader(movie: MovieBox) {
        if (mdatOffset == 0L) {
            mdatOffset = 48
        }
        val mdatSize = out.position() - mdatOffset + 8
        MP4Util.writeMovie(out, movie)

        out.position(mdatOffset)
        NIOUtils.writeLong(out, mdatSize)
    }

    @Throws(IOException::class)
    fun finalizeHeader(): MovieBox {
        val movie = MovieBox()
        val mvhd = movieHeader(movie)
        movie.addFirst(mvhd)

        for (track in tracks) {
            try {
                val classType = track.javaClass
                val method = classType.getDeclaredMethod("finish",
                        *arrayOf<Class<*>>(MovieHeaderBox::class.java))
                method.isAccessible = true // 抑制Java的访问控制检查
                val trak = method.invoke(track, *arrayOf<Any>(mvhd)) as Box
                if (trak != null)
                    movie.add(trak)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
        return movie
    }

    private fun movieHeader(movie: NodeBox): MovieHeaderBox {
        var timescale = tracks[0].timescale
        var duration = tracks[0].trackTotalDuration
        val videoTrack = videoTrack
        if (videoTrack != null) {
            timescale = videoTrack.timescale
            duration = videoTrack.trackTotalDuration
        }
        return MovieHeaderBox(timescale, duration, 1.0f, 1.0f, Date().time, Date().time,
                intArrayOf(0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000), nextTrackId)
    }

    fun addPCMAudioTrack(format: AudioFormat): PCMMP4MuxerTrack {
        return addPCMTrack(format.sampleRate, 1, (format.sampleSizeInBits shr 3) * format.channels, audioSampleEntry(format))
    }

    fun addCompressedAudioTrack(fourcc: String, timescale: Int, channels: Int, sampleRate: Int,
                                samplesPerPkt: Int, vararg extra: Box): FramesMP4MuxerTrack2 {
        val track = addTrack(SOUND, timescale)

        val ase = AudioSampleEntry(Header(fourcc, 0), 1.toShort(), channels.toShort(), 16.toShort(),
                sampleRate, 0.toShort(), 0, 65534, 0, samplesPerPkt, 0, 0, 2, 1.toShort())

        val wave = NodeBox(Header("wave"))
        ase.add(wave)

        wave.add(FormatBox(fourcc))
        for (box in extra)
            wave.add(box)

        wave.add(terminatorAtom())

        track.addSampleEntry(ase)

        return track
    }

    companion object {

        fun videoSampleEntry(fourcc: String, size: Size, encoderName: String?): VideoSampleEntry {
            return VideoSampleEntry(Header(fourcc), 0.toShort(), 0.toShort(), "jcod", 0, 768, size.width.toShort(),
                    size.height.toShort(), 72, 72, 1.toShort(), encoderName
                    ?: "jcodec", 24.toShort(),
                    1.toShort(), (-1).toShort())
        }

        fun audioSampleEntry(fourcc: String, drefId: Int, sampleSize: Int, channels: Int,
                             sampleRate: Int, endian: EndianBox.Endian): AudioSampleEntry {
            val ase = AudioSampleEntry(Header(fourcc, 0), drefId.toShort(), channels.toShort(),
                    16.toShort(), sampleRate, 0.toShort(), 0, 65535, 0, 1, sampleSize, channels * sampleSize, sampleSize,
                    1.toShort())

            val wave = NodeBox(Header("wave"))
            ase.add(wave)

            wave.add(FormatBox(fourcc))
            wave.add(EndianBox(endian))
            wave.add(terminatorAtom())
            // ase.add(new ChannelBox(atom));

            return ase
        }

        fun terminatorAtom(): LeafBox {
            return LeafBox(Header(String(ByteArray(4))), ByteBuffer.allocate(0))
        }

        fun lookupFourcc(format: AudioFormat): String {
            return if (format.sampleSizeInBits == 16 && !format.isBigEndian)
                "sowt"
            else if (format.sampleSizeInBits == 24)
                "in24"
            else
                throw IllegalArgumentException("Audio format $format is not supported.")
        }

        fun audioSampleEntry(format: AudioFormat): AudioSampleEntry {
            return MP4Muxer.audioSampleEntry(lookupFourcc(format), 1,
                    format.sampleSizeInBits shr 3, format.channels, format.sampleRate,
                    if (format.isBigEndian) EndianBox.Endian.BIG_ENDIAN else EndianBox.Endian.LITTLE_ENDIAN)
        }
    }
}
