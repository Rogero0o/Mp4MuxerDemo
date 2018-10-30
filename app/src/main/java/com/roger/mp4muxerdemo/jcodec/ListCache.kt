package com.roger.mp4muxerdemo.jcodec

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Base64
import android.util.Log

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

import org.jcodec.common.IntArrayList
import org.jcodec.common.LongArrayList
import org.jcodec.containers.mp4.boxes.SampleToChunkBox
import org.jcodec.containers.mp4.boxes.TimeToSampleBox

import java.util.ArrayList

class ListCache private constructor(private val mContext: Context) {

    private val gson: Gson = GsonBuilder().setLenient().create()

    val beanList: List<SampleToChunkBox.SampleToChunkEntry>
        get() {
            val str = getAutoTestLogBeanCache(mContext)
            var list: ArrayList<SampleToChunkBox.SampleToChunkEntry>? = gson.fromJson<ArrayList<SampleToChunkBox.SampleToChunkEntry>>(str, object : TypeToken<ArrayList<SampleToChunkBox.SampleToChunkEntry>>() {

            }.type)
            if (list == null) {
                list = ArrayList()
            }
            return list
        }

    val sampleSizes: IntArrayList
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
            val str = prefs.getString(LIST_SampleSizes_KEY, "")
            return gson.fromJson(str, object : TypeToken<IntArrayList>() {

            }.type)
        }

    val iframeSizes: IntArrayList
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
            val str = prefs.getString(LIST_IFRAME_KEY, "")
            Log.i("Tag", "getIFRAMESizes:" + str!!)
            return gson.fromJson(str, object : TypeToken<IntArrayList>() {

            }.type)
        }

    val sampleDurationsList: MutableList<TimeToSampleBox.TimeToSampleEntry>
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
            val str = prefs.getString(LIST_SampleDurations_KEY, "")
            var list: ArrayList<TimeToSampleBox.TimeToSampleEntry>? = gson.fromJson<ArrayList<TimeToSampleBox.TimeToSampleEntry>>(str, object : TypeToken<ArrayList<TimeToSampleBox.TimeToSampleEntry>>() {

            }.type)
            if (list == null) {
                list = ArrayList()
            }
            return list
        }

    val chunkOffsets: LongArrayList
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
            val str = prefs.getString(LIST_chunkOffsets_KEY, "")
            return gson.fromJson(str, object : TypeToken<LongArrayList>() {

            }.type)
        }

    val lastIndex: Long
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
            return prefs.getLong(LAST_INDEX, 0)
        }

    val sps: ByteArray
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
            val array = Base64.decode(prefs.getString(SPS, ""), Base64.DEFAULT)
            Log.i("Tag", "getSPS:" + array.size)
            return array
        }

    val pps: ByteArray
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
            val array = Base64.decode(prefs.getString(PPS, ""), Base64.DEFAULT)
            Log.i("Tag", "getPPS:" + array.size)
            return array
        }

    fun saveBeanList(beanList: List<SampleToChunkBox.SampleToChunkEntry>) {
        val jsonStr = gson.toJson(beanList)
        setAutoTestLogBeanCache(mContext, jsonStr)
    }

    private fun getAutoTestLogBeanCache(mContext: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
        return prefs.getString(LIST_KEY, "")
    }

    private fun setAutoTestLogBeanCache(mContext: Context, str: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
        prefs.edit().putString(LIST_KEY, str).apply()
    }

    fun saveSampleSizes(sampleSizes: IntArrayList) {
        val jsonStr = gson.toJson(sampleSizes)
        val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
        prefs.edit().putString(LIST_SampleSizes_KEY, jsonStr).apply()
    }

    fun saveIFRAMESizes(sampleSizes: IntArrayList) {
        val jsonStr = gson.toJson(sampleSizes)
        Log.i("Tag", "saveIFRAMESizes:$jsonStr")
        val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
        prefs.edit().putString(LIST_IFRAME_KEY, jsonStr).apply()
    }


    fun saveSampleDurationsList(beanList: List<TimeToSampleBox.TimeToSampleEntry>) {
        val jsonStr = gson.toJson(beanList)
        val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
        prefs.edit().putString(LIST_SampleDurations_KEY, jsonStr).apply()
    }

    fun saveChunkOffsets(beanList: LongArrayList) {
        val jsonStr = gson.toJson(beanList)
        val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
        prefs.edit().putString(LIST_chunkOffsets_KEY, jsonStr).apply()
    }

    fun saveLastIndex(index: Long) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
        prefs.edit().putLong(LAST_INDEX, index).apply()
    }

    fun saveSPS(bytes: ByteArray) {
        val saveThis = Base64.encodeToString(bytes, Base64.DEFAULT)
        Log.i("Tag", "saveSPS:" + saveThis.length)
        val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
        prefs.edit().putString(SPS, saveThis).apply()
    }

    fun savePPS(bytes: ByteArray) {
        val saveThis = Base64.encodeToString(bytes, Base64.DEFAULT)
        Log.i("Tag", "savePPS:" + saveThis.length)
        val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
        prefs.edit().putString(PPS, saveThis).apply()
    }

    companion object {

        private val LIST_KEY = "LIST_KEY"
        private val LIST_SampleSizes_KEY = "sampleSizes_key"
        private val LIST_IFRAME_KEY = "sampleIFRAMESizes_key"
        private val LIST_SampleDurations_KEY = "sampleDurations_key"
        private val LIST_chunkOffsets_KEY = "chunkOffsets_key"
        private val LAST_INDEX = "last_index"

        private val SPS = "sps"
        private val PPS = "pps"
        @Volatile
        private var mInstance: ListCache? = null


        fun getInstance(context: Context?): ListCache {
            if (mInstance == null) {
                synchronized(ListCache::class.java) {
                    if (mInstance == null) {
                        context?.let {
                            mInstance = ListCache(it)
                        }
                    }
                }
            }
            return mInstance!!
        }
    }
}
