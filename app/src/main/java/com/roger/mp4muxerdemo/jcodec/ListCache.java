package com.roger.mp4muxerdemo.jcodec;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.jcodec.common.IntArrayList;
import org.jcodec.common.LongArrayList;
import org.jcodec.containers.mp4.boxes.SampleToChunkBox;
import org.jcodec.containers.mp4.boxes.TimeToSampleBox;

import java.util.ArrayList;
import java.util.List;

public class ListCache {

    private static final String LIST_KEY = "LIST_KEY";
    private static final String LIST_SampleSizes_KEY = "sampleSizes_key";
    private static final String LIST_IFRAME_KEY = "sampleIFRAMESizes_key";
    private static final String LIST_SampleDurations_KEY = "sampleDurations_key";
    private static final String LIST_chunkOffsets_KEY = "chunkOffsets_key";
    private static final String LAST_INDEX = "last_index";

    private static final String SPS = "sps";
    private static final String PPS = "pps";


    private Context mContext;
    private volatile static ListCache mInstance;

    private Gson gson;


    public static ListCache getInstance(Context context) {
        if (mInstance == null) {
            synchronized (ListCache.class) {
                if (mInstance == null) {
                    mInstance = new ListCache(context);
                }
            }
        }
        return mInstance;
    }

    private ListCache(Context context) {
        mContext = context;
        gson = new GsonBuilder().setLenient().create();
    }

    public void saveBeanList(List<SampleToChunkBox.SampleToChunkEntry> beanList) {
        String jsonStr = gson.toJson(beanList);
        setAutoTestLogBeanCache(mContext, jsonStr);
    }

    public List<SampleToChunkBox.SampleToChunkEntry> getBeanList() {
        String str = getAutoTestLogBeanCache(mContext);
        ArrayList<SampleToChunkBox.SampleToChunkEntry> list = gson.fromJson(str, new TypeToken<ArrayList<SampleToChunkBox.SampleToChunkEntry>>() {
        }.getType());
        if (list == null) {
            list = new ArrayList<>();
        }
        return list;
    }

    private String getAutoTestLogBeanCache(Context mContext) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        return prefs.getString(LIST_KEY, "");
    }

    private void setAutoTestLogBeanCache(Context mContext, String str) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        prefs.edit().putString(LIST_KEY, str).apply();
    }

    public void saveSampleSizes(IntArrayList sampleSizes) {
        String jsonStr = gson.toJson(sampleSizes);
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        prefs.edit().putString(LIST_SampleSizes_KEY, jsonStr).apply();
    }

    public IntArrayList getSampleSizes() {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        String str = prefs.getString(LIST_SampleSizes_KEY, "");
        IntArrayList bean = gson.fromJson(str, new TypeToken<IntArrayList>() {
        }.getType());
        return bean;
    }

    public void saveIFRAMESizes(IntArrayList sampleSizes) {
        String jsonStr = gson.toJson(sampleSizes);
        Log.i("Tag", "saveIFRAMESizes:" + jsonStr);
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        prefs.edit().putString(LIST_IFRAME_KEY, jsonStr).apply();
    }

    public IntArrayList getIFRAMESizes() {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        String str = prefs.getString(LIST_IFRAME_KEY, "");
        Log.i("Tag", "getIFRAMESizes:" + str);
        IntArrayList bean = gson.fromJson(str, new TypeToken<IntArrayList>() {
        }.getType());
        return bean;
    }


    public void saveSampleDurationsList(List<TimeToSampleBox.TimeToSampleEntry> beanList) {
        String jsonStr = gson.toJson(beanList);
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        prefs.edit().putString(LIST_SampleDurations_KEY, jsonStr).apply();
    }

    public List<TimeToSampleBox.TimeToSampleEntry> getSampleDurationsList() {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        String str = prefs.getString(LIST_SampleDurations_KEY, "");
        ArrayList<TimeToSampleBox.TimeToSampleEntry> list = gson.fromJson(str, new TypeToken<ArrayList<TimeToSampleBox.TimeToSampleEntry>>() {
        }.getType());
        if (list == null) {
            list = new ArrayList<>();
        }
        return list;
    }

    public void saveChunkOffsets(LongArrayList beanList) {
        String jsonStr = gson.toJson(beanList);
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        prefs.edit().putString(LIST_chunkOffsets_KEY, jsonStr).apply();
    }

    public LongArrayList getChunkOffsets() {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        String str = prefs.getString(LIST_chunkOffsets_KEY, "");
        LongArrayList list = gson.fromJson(str, new TypeToken<LongArrayList>() {
        }.getType());
        return list;
    }

    public void saveLastIndex(long index) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        prefs.edit().putLong(LAST_INDEX, index).apply();
    }

    public long getLastIndex() {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        return prefs.getLong(LAST_INDEX, 0);
    }

    public void saveSPS(byte[] bytes) {
        String saveThis = Base64.encodeToString(bytes, Base64.DEFAULT);
        Log.i("Tag", "saveSPS:" + saveThis.length());
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        prefs.edit().putString(SPS, saveThis).apply();
    }

    public byte[] getSPS() {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        byte[] array = Base64.decode(prefs.getString(SPS, ""), Base64.DEFAULT);
        Log.i("Tag", "getSPS:" + array.length);
        return array;
    }

    public void savePPS(byte[] bytes) {
        String saveThis = Base64.encodeToString(bytes, Base64.DEFAULT);
        Log.i("Tag", "savePPS:" + saveThis.length());
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        prefs.edit().putString(PPS, saveThis).apply();
    }

    public byte[] getPPS() {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());
        byte[] array = Base64.decode(prefs.getString(PPS, ""), Base64.DEFAULT);
        Log.i("Tag", "getPPS:" + array.length);
        return array;
    }
}
