package com.github.robinZhao.sound;

import java.util.function.Function;

public class ScaleFilter {
    private int fftSamples;
    private Type scaleType;
    private int numFilters ;
    private double sampleRate;

    public static enum Type{
        mel,logarithmic,bark,erb,linear
    }

    static final double ERB_A = (1000 * Math.log(10)) / (24.7 * 4.37);

    public ScaleFilter(Type scale, double sampleRate,int fftSamples,int numFilters) {
        this.scaleType = scale;
        this.sampleRate=sampleRate;
        this.fftSamples = fftSamples;
        this.numFilters = numFilters;
    }

    public ScaleFilter(Type scale, double sampleRate,int fftSamples) {
        this(scale, sampleRate,fftSamples, fftSamples/2);
    }

    private double[][] createFilterBank(
            int numFilters,
            double sampleRate,
            Function<Double, Double> hzToScale,
            Function<Double, Double> scaleToHz) {
        double filterMin = hzToScale.apply(0d);
        double filterMax = hzToScale.apply(sampleRate / 2);
        double[][] filterBank = new double[numFilters][(int) (this.fftSamples / 2 + 1)];
        double scale = sampleRate / this.fftSamples;
        for (int i = 0; i < numFilters; i++) {
            double hz = scaleToHz.apply(filterMin + ((double)i / numFilters) * (filterMax - filterMin));
            int j = (int) Math.floor(hz / scale);
            double hzLow = j * scale;
            double hzHigh = (j + 1) * scale;
            double r = (hz - hzLow) / (hzHigh - hzLow);
            filterBank[i][j] = 1 - r;
            filterBank[i][j + 1] = r;
        }
        return filterBank;
    }

    private double[][] createFilterBank(
            int numFilters,
            double sampleRate) {
        return this.createFilterBank(numFilters, sampleRate, this::hzToScale, this::scaleToHz);
    }

    private double hzToMel(double hz) {
        return 2595 * Math.log10(1 + hz / 700);
    }

    private double melToHz(double mel) {
        return 700 * (Math.pow(10, mel / 2595) - 1);
    }

    private double[][] createMelFilterBank(int numMelFilters, double sampleRate) {
        return this.createFilterBank(numMelFilters, sampleRate, this::hzToMel, this::melToHz);
    }

    private double hzToLog(double hz) {
        return Math.log10(Math.max(1, hz));
    }

    private double logToHz(double log) {
        return Math.pow(10, log);
    }

    private double[][] createLogFilterBank(int numLogFilters, double sampleRate) {
        return this.createFilterBank(numLogFilters, sampleRate, this::hzToLog, this::logToHz);
    }

    private double hzToBark(double hz) {
        // https://www.mathworks.com/help/audio/ref/hz2bark.html#function_hz2bark_sep_mw_06bea6f7-353b-4479-a58d-ccadb90e44de;
        double bark = (26.81 * hz) / (1960 + hz) - 0.53;
        if (bark < 2) {
            bark += 0.15 * (2 - bark);
        }
        if (bark > 20.1) {
            bark += 0.22 * (bark - 20.1);
        }
        return bark;
    }

    private double barkToHz(double bark) {
        // https://www.mathworks.com/help/audio/ref/bark2hz.html#function_bark2hz_sep_mw_bee310ea-48ac-4d95-ae3d-80f3e4149555;
        if (bark < 2) {
            bark = (bark - 0.3) / 0.85;
        }
        if (bark > 20.1) {
            bark = (bark + 4.422) / 1.22;
        }
        return 1960 * ((bark + 0.53) / (26.28 - bark));
    }

    private double[][] createBarkFilterBank(int numBarkFilters, double sampleRate) {
        return this.createFilterBank(numBarkFilters, sampleRate, this::hzToBark, this::barkToHz);
    }

    private double hzToErb(double hz) {
        // https://www.mathworks.com/help/audio/ref/hz2erb.html#function_hz2erb_sep_mw_06bea6f7-353b-4479-a58d-ccadb90e44de;
        return ERB_A * Math.log10(1 + hz * 0.00437);
    }

    private double erbToHz(double erb) {
        // https://it.mathworks.com/help/audio/ref/erb2hz.html?#function_erb2hz_sep_mw_bee310ea-48ac-4d95-ae3d-80f3e4149555;
        return (Math.pow(10, erb / ERB_A) - 1) / 0.00437;
    }

    private double[][] createErbFilterBank(int numErbFilters, double sampleRate) {
        return this.createFilterBank(numErbFilters, sampleRate, this::hzToErb, this::erbToHz);
    }

    public double hzToScale(double hz) {
        switch (this.scaleType) {
            case mel:
                return this.hzToMel(hz);
            case logarithmic:
                return this.hzToLog(hz);
            case bark:
                return this.hzToBark(hz);
            case erb:
                return this.hzToErb(hz);
        }
        return hz;
    }

    public double scaleToHz(double scale) {
        switch (this.scaleType) {
            case mel:
                return this.melToHz(scale);
            case logarithmic:
                return this.logToHz(scale);
            case bark:
                return this.barkToHz(scale);
            case erb:
                return this.erbToHz(scale);
        }
        return scale;
    }

    public double[] applyFilterBank(double[] fftPoints,double[][] filterBank) {
        int numFilters = filterBank.length;
        double[] logSpectrum = new double[numFilters];
        for (int i = 0; i < numFilters; i++) {
            for (int j = 0; j < fftPoints.length; j++) {
                logSpectrum[i] += fftPoints[j] * filterBank[i][j];
            }
        }
        return logSpectrum;
    }

    public double[] applyFilterBank(double[] fftPoints) {
        double[][] filterBank = this.createFilterBank(this.numFilters, this.sampleRate);
        return this.applyFilterBank(fftPoints,filterBank);
    }
}
