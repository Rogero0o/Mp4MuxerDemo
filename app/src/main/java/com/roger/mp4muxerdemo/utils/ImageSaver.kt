package com.roger.mp4muxerdemo.utils

import android.media.Image
import android.util.Log
import com.roger.mp4muxerdemo.MediaMuxerUtils
import java.io.ByteArrayOutputStream

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

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
        private val file: File
) : Runnable {

    override fun run() {
        try {
            val outputbytes = ByteArrayOutputStream()

            val bufferY = image.getPlanes()[0].getBuffer()
            val data0 = ByteArray(bufferY.remaining())
            bufferY.get(data0)

            val bufferU = image.getPlanes()[1].getBuffer()
            val data1 = ByteArray(bufferU.remaining())
            bufferU.get(data1)

            val bufferV = image.getPlanes()[2].getBuffer()
            val data2 = ByteArray(bufferV.remaining())
            bufferV.get(data2)

            try {
                outputbytes.write(data0)
                outputbytes.write(data2)
                outputbytes.write(data1)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            val bytes = outputbytes.toByteArray().copyOf()
            MediaMuxerUtils.muxerRunnableInstance.addVideoFrameData(bytes)

        } catch (e: IOException) {
            Log.e(TAG, e.toString())
        } finally {
            image.close()
        }
    }

    companion object {
        /**
         * Tag for the [Log].
         */
        private val TAG = "ImageSaver"
    }
}
