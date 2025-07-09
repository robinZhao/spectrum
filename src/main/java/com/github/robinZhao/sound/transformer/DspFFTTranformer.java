package com.github.robinZhao.sound.transformer;

import java.util.Optional;

import com.github.robinZhao.sound.SpectrumTransformer;

import be.tarsos.dsp.util.fft.BartlettHannWindow;
import be.tarsos.dsp.util.fft.BartlettWindow;
import be.tarsos.dsp.util.fft.BlackmanWindow;
import be.tarsos.dsp.util.fft.CosineWindow;
import be.tarsos.dsp.util.fft.FFT;
import be.tarsos.dsp.util.fft.GaussWindow;
import be.tarsos.dsp.util.fft.HammingWindow;
import be.tarsos.dsp.util.fft.HannWindow;
import be.tarsos.dsp.util.fft.LanczosWindow;
import be.tarsos.dsp.util.fft.RectangularWindow;
import be.tarsos.dsp.util.fft.TriangularWindow;
import be.tarsos.dsp.util.fft.WindowFunction;

public class DspFFTTranformer implements SpectrumTransformer {

    private FFT fft;
    int bufferSize;
    private double alpha;

    public DspFFTTranformer(int bufferSize, String windowFun) {
        this.bufferSize = bufferSize;
        this.fft = new be.tarsos.dsp.util.fft.FFT(bufferSize, this.getWindowFunction(windowFun));
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public WindowFunction getWindowFunction(String windowFunc) {
        switch (windowFunc) {
            case "bartlett":
                return new BartlettWindow();
            case "bartlettHann":
                return new BartlettHannWindow();
            case "blackman":
                float alpha = Optional.ofNullable((float) this.alpha).orElse(0.16f);
                return new BlackmanWindow(alpha);
            case "cosine":
                return new CosineWindow();
            case "gauss":
                alpha = Optional.ofNullable((float) this.alpha).orElse(0.25f);
                return new GaussWindow(alpha);
            case "hamming":
                return new HammingWindow();
            case "hann":
            case "":
                return new HannWindow();
            case "lanczoz":
                return new LanczosWindow();
            case "rectangular":
                return new RectangularWindow();
            case "triangular":
                return new TriangularWindow();
            default:
                throw new RuntimeException("No such window function " + windowFunc);
        }
    }

    @Override
    public double[] transform(double[] audioDoubleBuffer) {
        float[] transformData = new float[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            transformData[i] = (float) audioDoubleBuffer[i];
        }
        fft.forwardTransform(transformData);
        float[] famplitudes = new float[bufferSize / 2];
        fft.modulus(transformData, famplitudes);
        double[] amplitudes = new double[famplitudes.length];
        for (int i = 0; i < famplitudes.length; i++) {
            amplitudes[i] = 2d / bufferSize * famplitudes[i];
        }
        return amplitudes;
    }

}
