package com.github.robinZhao.sound.transformer;

import com.github.robinZhao.sound.SpectrumTransformer;

public class SunFFTTransformer implements SpectrumTransformer {
    SunFFT fft;
    int bufferSize;
    WindowFun windowFun;

    public SunFFTTransformer(int bufferSize,String windowFun) {
        this.bufferSize = bufferSize;
        this.fft = new SunFFT(this.bufferSize, -1);
        this.windowFun = WindowFun.getWindowFunction(windowFun, null);
    }

    @Override
    public double[] transform(double[] audioDoubleBuffer) {
        double[] temp = new double[audioDoubleBuffer.length];
        System.arraycopy(audioDoubleBuffer, 0, temp, 0, audioDoubleBuffer.length);
        //windowFun.apply(temp);
        double[] transformData = new double[bufferSize * 2];
        for (int i = 0; i < bufferSize; i++) {
            transformData[2 * i] = temp[i];
            transformData[2 * i + 1] = 0;
        }
        windowFun.apply(transformData);
        fft.transform(transformData);
        double[] amplitudes = new double[bufferSize / 2];
        for (int i = 0; i < amplitudes.length; i++) {
            amplitudes[i] = 2d / this.bufferSize
                    * Math.sqrt(Math.pow(transformData[2 * i], 2) + Math.pow(transformData[2 * i + 1], 2));
        }
        return amplitudes;
    }

}
