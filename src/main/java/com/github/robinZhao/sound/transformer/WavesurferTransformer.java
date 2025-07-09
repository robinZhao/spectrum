package com.github.robinZhao.sound.transformer;

import com.github.robinZhao.sound.SpectrumTransformer;

public class WavesurferTransformer implements SpectrumTransformer {
    int bufferSize;
    String windowFun;
    WavesurferFFT fft;

    public WavesurferTransformer(int bufferSize, String windowFun) {
        this.bufferSize = bufferSize;
        this.windowFun = windowFun;
        fft = new WavesurferFFT(bufferSize, windowFun, null);
    }

    @Override
    public double[] transform(double[] audioDoubleBuffer) {
        return fft.calculateSpectrum(audioDoubleBuffer);
    }

}
