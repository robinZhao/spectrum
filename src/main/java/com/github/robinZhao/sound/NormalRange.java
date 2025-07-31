package com.github.robinZhao.sound;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NormalRange {
    private double minFreq;
    private double maxFreq;
    private double minDb;
    private double maxDb;

    public class AlarmInfo {
        public Set<Double> alarmTimes = new HashSet<>();
        public Set<Integer> alarmTimeIdxs = new HashSet<>();
        public List<Double> alarmFreqs = new ArrayList<>();
        public List<Integer> alarmFreqIdxs = new ArrayList<>();

        public void addAlarmIdx(int i) {
            this.alarmTimeIdxs.add(i);
        }

        public void addAlarmTime(double time) {
            this.alarmTimes.add(time);
        }

        public void addFreqIdx(int i) {
            this.alarmFreqIdxs.add(i);
        }

        public void addFreq(double freq) {
            this.alarmFreqs.add(freq);
        }
    }

    AlarmInfo[] alarmInfos;

    public NormalRange(double minFreq, double maxFreq, double minDb, double maxDb, int channels) {
        this.minFreq = minFreq;
        this.maxFreq = maxFreq;
        this.minDb = minDb;
        this.maxDb = maxDb;
        this.alarmInfos = new AlarmInfo[channels];
        for (int i = 0; i < channels; i++) {
            alarmInfos[i] = new AlarmInfo();
        }
    }

    public double getMinFreq() {
        return minFreq;
    }

    public void setMinFreq(double minFreq) {
        this.minFreq = minFreq;
    }

    public double getMaxFreq() {
        return maxFreq;
    }

    public void setMaxFreq(double maxFreq) {
        this.maxFreq = maxFreq;
    }

    public double getMinDb() {
        return minDb;
    }

    public void setMinDb(double minDb) {
        this.minDb = minDb;
    }

    public double getMaxDb() {
        return maxDb;
    }

    public void setMaxDb(double maxDb) {
        this.maxDb = maxDb;
    }

    public AlarmInfo getAlarmInfo(int c){
        return this.alarmInfos[c];
    }

}
