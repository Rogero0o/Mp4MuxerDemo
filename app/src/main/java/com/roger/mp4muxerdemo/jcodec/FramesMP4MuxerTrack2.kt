package com.roger.mp4muxerdemo.jcodec

import android.content.Context
import android.util.Log

import org.jcodec.common.Assert
import org.jcodec.common.IntArrayList
import org.jcodec.common.LongArrayList
import org.jcodec.common.SeekableByteChannel
import org.jcodec.common.model.Rational
import org.jcodec.common.model.Size
import org.jcodec.common.model.Unit
import org.jcodec.containers.mp4.MP4Packet
import org.jcodec.containers.mp4.TrackType
import org.jcodec.containers.mp4.boxes.Box
import org.jcodec.containers.mp4.boxes.ChunkOffsets64Box
import org.jcodec.containers.mp4.boxes.CompositionOffsetsBox
import org.jcodec.containers.mp4.boxes.Edit
import org.jcodec.containers.mp4.boxes.HandlerBox
import org.jcodec.containers.mp4.boxes.Header
import org.jcodec.containers.mp4.boxes.MediaBox
import org.jcodec.containers.mp4.boxes.MediaHeaderBox
import org.jcodec.containers.mp4.boxes.MediaInfoBox
import org.jcodec.containers.mp4.boxes.MovieHeaderBox
import org.jcodec.containers.mp4.boxes.NodeBox
import org.jcodec.containers.mp4.boxes.SampleDescriptionBox
import org.jcodec.containers.mp4.boxes.SampleEntry
import org.jcodec.containers.mp4.boxes.SampleSizesBox
import org.jcodec.containers.mp4.boxes.SampleToChunkBox
import org.jcodec.containers.mp4.boxes.SyncSamplesBox
import org.jcodec.containers.mp4.boxes.TimeToSampleBox
import org.jcodec.containers.mp4.boxes.TrackHeaderBox
import org.jcodec.containers.mp4.boxes.TrakBox
import org.jcodec.containers.mp4.muxer.AbstractMP4MuxerTrack
import org.jcodec.containers.mp4.muxer.TimecodeMP4MuxerTrack

import java.io.IOException
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.Date

/**
 * Created by roger on 29/08/2017.
 */

class FramesMP4MuxerTrack2(private val out: SeekableByteChannel, trackId: Int, type: TrackType, timescale: Int) : AbstractMP4MuxerTrack(trackId, type, timescale) {

    private var sampleDurations: MutableList<TimeToSampleBox.TimeToSampleEntry> = ArrayList()
    private var sameDurCount: Long = 0
    private var curDuration: Long = -1

    private var chunkOffsets = LongArrayList()
    private var sampleSizes = IntArrayList()
    private var iframes = IntArrayList()

    private val compositionOffsets = ArrayList<CompositionOffsetsBox.Entry>()
    private var lastCompositionOffset = 0
    private var lastCompositionSamples = 0
    private var ptsEstimate: Long = 0

    private var lastEntry = -1

    private var trackTotalDuration: Long = 0
    private var curFrame: Int = 0
    private var allIframes = true
    var timecodeTrack: TimecodeMP4MuxerTrack? = null
        private set

    private var context: Context? = null

    fun setContext(mContext: Context) {
        context = mContext
    }

    init {

        setTgtChunkDuration(Rational(1, 1), Unit.FRAME)
    }

    @Throws(IOException::class)
    fun addFrame(pkt: MP4Packet) {
        if (finished)
            throw IllegalStateException("The muxer track has finished muxing")
        val entryNo = pkt.entryNo + 1

        val compositionOffset = (pkt.pts - ptsEstimate).toInt()
        if (compositionOffset != lastCompositionOffset) {
            if (lastCompositionSamples > 0)
                compositionOffsets.add(CompositionOffsetsBox.Entry(lastCompositionSamples, lastCompositionOffset))
            lastCompositionOffset = compositionOffset
            lastCompositionSamples = 0
        }
        lastCompositionSamples++
        ptsEstimate += pkt.duration

        if (lastEntry != -1 && lastEntry != entryNo) {
            outChunk(lastEntry)
            samplesInLastChunk = -1
        }

        curChunk.add(pkt.data)

        if (pkt.isKeyFrame)
            iframes.add(curFrame + 1)
        else
            allIframes = false
        ListCache.getInstance(context!!)?.saveIFRAMESizes(iframes)


        curFrame++

        chunkDuration += pkt.duration
        if (curDuration != -1L && pkt.duration != curDuration) {
            sampleDurations.add(TimeToSampleBox.TimeToSampleEntry(sameDurCount.toInt(), curDuration.toInt()))
            sameDurCount = 0

            ListCache.getInstance(context!!)?.saveSampleDurationsList(sampleDurations)
        }
        curDuration = pkt.duration
        sameDurCount++
        trackTotalDuration += pkt.duration


        outChunkIfNeeded(entryNo)

        processTimecode(pkt)

        lastEntry = entryNo

        ListCache.getInstance(context).saveLastIndex(curFrame.toLong())
        Log.i("Tag", "trackTotalDuration:" + trackTotalDuration + " lastCompositionSamples:" + lastCompositionSamples +
                " ptsEstimate:" + ptsEstimate + " sameDurCount:" + sameDurCount + " lastEntry:" + lastEntry + " samplesInLastChunk:" + samplesInLastChunk
                + " lastCompositionOffset:" + lastCompositionOffset)
    }

    fun setTrackTotalDuration(total: Long) {
        trackTotalDuration = total - 1
        ptsEstimate = total - 1
        chunkNo = total.toInt() - 1
        lastCompositionSamples = total.toInt() - 1
        iframes.clear()
        sameDurCount = total - 1
        samplesInLastChunk = 1
        curDuration = 1
        samplesInChunks = ListCache.getInstance(context).beanList
        sampleSizes = ListCache.getInstance(context).sampleSizes
        sampleDurations = ListCache.getInstance(context).sampleDurationsList
        chunkOffsets = ListCache.getInstance(context).chunkOffsets
        iframes = ListCache.getInstance(context).iframeSizes
        allIframes = false
    }

    @Throws(IOException::class)
    private fun processTimecode(pkt: MP4Packet) {
        if (timecodeTrack != null)
            timecodeTrack!!.addTimecode(pkt)
    }

    @Throws(IOException::class)
    private fun outChunkIfNeeded(entryNo: Int) {
        Assert.assertTrue(tgtChunkDurationUnit == Unit.FRAME || tgtChunkDurationUnit == Unit.SEC)

        if (tgtChunkDurationUnit == Unit.FRAME && curChunk.size * tgtChunkDuration.den == tgtChunkDuration.num) {
            outChunk(entryNo)
        } else if (tgtChunkDurationUnit == Unit.SEC && chunkDuration > 0
                && chunkDuration * tgtChunkDuration.den >= tgtChunkDuration.num * timescale) {
            outChunk(entryNo)
        }
    }

    @Throws(IOException::class)
    internal fun outChunk(entryNo: Int) {
        if (curChunk.size == 0)
            return

        chunkOffsets.add(out.position())

        for (bs in curChunk) {
            sampleSizes.add(bs.remaining())
            out.write(bs)
        }
        ListCache.getInstance(context).saveSampleSizes(sampleSizes)
        ListCache.getInstance(context).saveChunkOffsets(chunkOffsets)

        Log.i("Tag", "samplesInLastChunk:" + samplesInLastChunk + " curChunk.size():" + curChunk.size)
        if (samplesInLastChunk == -1 || samplesInLastChunk != curChunk.size) {
            samplesInChunks.add(SampleToChunkBox.SampleToChunkEntry((chunkNo + 1).toLong(), curChunk.size, entryNo))
            ListCache.getInstance(context).saveBeanList(samplesInChunks)
        }
        samplesInLastChunk = curChunk.size
        chunkNo++


        chunkDuration = 0
        curChunk.clear()
    }

    @Throws(IOException::class)
    override fun finish(mvhd: MovieHeaderBox): Box {
        if (finished)
            throw IllegalStateException("The muxer track has finished muxing")

        outChunk(lastEntry)

        Log.i("Tag", "sameDurCount:$sameDurCount curDuration:$curDuration")
        if (sameDurCount > 0) {
            sampleDurations.add(TimeToSampleBox.TimeToSampleEntry(sameDurCount.toInt(), curDuration.toInt()))
        }
        finished = true

        val trak = TrakBox()
        val dd = displayDimensions
        val tkhd = TrackHeaderBox(trackId, mvhd.timescale.toLong() * trackTotalDuration / timescale, dd.width.toFloat(), dd.height.toFloat(), Date().time, Date().time, 1.0f,
                0.toShort(), 0, intArrayOf(0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000))
        tkhd.flags = 0xf
        trak.add(tkhd)

        tapt(trak)

        val media = MediaBox()
        trak.add(media)
        media.add(MediaHeaderBox(timescale, trackTotalDuration, 0, Date().time, Date().time,
                0))

        val hdlr = HandlerBox("mhlr", type.handler, "appl", 0, 0)
        media.add(hdlr)

        val minf = MediaInfoBox()
        media.add(minf)
        mediaHeader(minf, type)
        minf.add(HandlerBox("dhlr", "url ", "appl", 0, 0))
        addDref(minf)
        val stbl = NodeBox(Header("stbl"))
        minf.add(stbl)

        putCompositionOffsets(stbl)
        putEdits(trak)
        putName(trak)

        stbl.add(SampleDescriptionBox(*sampleEntries.toTypedArray()))
        stbl.add(SampleToChunkBox(samplesInChunks.toTypedArray()))
        stbl.add(SampleSizesBox(sampleSizes.toArray()))
        stbl.add(TimeToSampleBox(sampleDurations.toTypedArray()))
        stbl.add(ChunkOffsets64Box(chunkOffsets.toArray()))
        if (!allIframes && iframes.size() > 0)
            stbl.add(SyncSamplesBox(iframes.toArray()))

        Log.i("Tag", "sampleEntries:" + sampleEntries.size)
        Log.i("Tag", "samplesInChunks:" + samplesInChunks.size)
        Log.i("Tag", "sampleSizes:" + sampleSizes.size())
        Log.i("Tag", "sampleDurations:" + sampleDurations.size)
        Log.i("Tag", "chunkOffsets:" + chunkOffsets.size())
        Log.i("Tag", "iframes:" + iframes.size())

        return trak
    }

    private fun putCompositionOffsets(stbl: NodeBox) {
        if (compositionOffsets.size > 0) {
            compositionOffsets.add(CompositionOffsetsBox.Entry(lastCompositionSamples, lastCompositionOffset))

            val min = minOffset(compositionOffsets)
            if (min > 0) {
                for (entry in compositionOffsets) {
                    entry.offset -= min
                }
            }

            val first = compositionOffsets[0]
            if (first.getOffset() > 0) {
                if (edits == null) {
                    edits = ArrayList()
                    edits.add(Edit(trackTotalDuration, first.getOffset().toLong(), 1.0f))
                } else {
                    for (edit in edits) {
                        edit.mediaTime = edit.mediaTime + first.getOffset()
                    }
                }
            }

            stbl.add(CompositionOffsetsBox(compositionOffsets.toTypedArray()))
        }
    }

    override fun getTrackTotalDuration(): Long {
        return trackTotalDuration
    }

    fun addSampleEntries(sampleEntries: Array<SampleEntry>) {
        for (se in sampleEntries) {
            addSampleEntry(se)
        }
    }

    fun setTimecode(timecodeTrack: TimecodeMP4MuxerTrack) {
        this.timecodeTrack = timecodeTrack
    }

    companion object {

        fun minOffset(offs: List<CompositionOffsetsBox.Entry>): Int {
            var min = Integer.MAX_VALUE
            for (entry in offs) {
                if (entry.getOffset() < min)
                    min = entry.getOffset()
            }
            return min
        }
    }
}
