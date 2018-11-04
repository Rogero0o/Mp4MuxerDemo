package com.roger.mp4muxerdemo.utils

import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.Image
import android.util.Log
import com.roger.mp4muxerdemo.MediaMuxerUtils

import java.io.File
import java.io.IOException
import android.graphics.ImageFormat
import com.roger.mp4muxerdemo.fragment.Camera2Fragment


/**
 * Saves a JPEG [Image] into the specified [File].
 */
internal class ImageSaver(
        /**
         * The JPEG image
         */
        private val image: Image,

        /**
         * The file we save the image into.
         */
        private val fragment: Camera2Fragment
) : Runnable {

    override fun run() {
        try {


            val planes = image.planes
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]
            val mBuffer = fragment.yuvToBuffer(yPlane.getBuffer(),
                    uPlane.getBuffer(),
                    vPlane.getBuffer(),
                    yPlane.getPixelStride(),
                    yPlane.getRowStride(),
                    uPlane.getPixelStride(),
                    uPlane.getRowStride(),
                    vPlane.getPixelStride(),
                    vPlane.getRowStride(),
                    image.getWidth(),
                    image.getHeight())
            MediaMuxerUtils.muxerRunnableInstance.addVideoFrameData(mBuffer)
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
        } finally {
            image.close()
        }
    }


    private fun getDataFromImage(image: Image, colorFormat: Int): ByteArray {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21")
        }
        if (!isImageFormatSupported(image)) {
            throw RuntimeException("can't convert Image to byte array, format " + image.format)
        }
        val crop = image.cropRect
        val format = image.format
        val width = crop.width()
        val height = crop.height()
        val planes = image.planes
        val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
        val rowData = ByteArray(planes[0].rowStride)
        var channelOffset = 0
        var outputStride = 1
        for (i in planes.indices) {
            when (i) {
                0 -> {
                    channelOffset = 0
                    outputStride = 1
                }
                1 -> if (colorFormat == COLOR_FormatI420) {
                    channelOffset = width * height
                    outputStride = 1
                } else if (colorFormat == COLOR_FormatNV21) {
                    channelOffset = width * height + 1
                    outputStride = 2
                }
                2 -> if (colorFormat == COLOR_FormatI420) {
                    channelOffset = (width.toDouble() * height.toDouble() * 1.25).toInt()
                    outputStride = 1
                } else if (colorFormat == COLOR_FormatNV21) {
                    channelOffset = width * height
                    outputStride = 2
                }
            }
            val buffer = planes[i].buffer
            val rowStride = planes[i].rowStride
            val pixelStride = planes[i].pixelStride

            val shift = if (i == 0) 0 else 1
            val w = width shr shift
            val h = height shr shift
            buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
            for (row in 0 until h) {
                val length: Int
                if (pixelStride == 1 && outputStride == 1) {
                    length = w
                    buffer.get(data, channelOffset, length)
                    channelOffset += length
                } else {
                    length = (w - 1) * pixelStride + 1
                    buffer.get(rowData, 0, length)
                    for (col in 0 until w) {
                        data[channelOffset] = rowData[col * pixelStride]
                        channelOffset += outputStride
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
        }
        return data
    }

    private val COLOR_FormatI420 = 1
    private val COLOR_FormatNV21 = 2

    private fun isImageFormatSupported(image: Image): Boolean {
        val format = image.format
        when (format) {
            ImageFormat.YUV_420_888, ImageFormat.NV21, ImageFormat.YV12 -> return true
        }
        return false
    }

    companion object {
        /**
         * Tag for the [Log].
         */
        private val TAG = "ImageSaver"
    }
}
