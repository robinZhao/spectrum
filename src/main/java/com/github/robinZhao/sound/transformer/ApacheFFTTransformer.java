package com.github.robinZhao.sound.transformer;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import com.github.robinZhao.sound.SpectrumTransformer;

public class ApacheFFTTransformer implements SpectrumTransformer {

    private FastFourierTransformer fft;
    private WindowFun windowFun;

    public ApacheFFTTransformer(int bufferSize,String windowFun) {
        this.fft = new FastFourierTransformer(DftNormalization.STANDARD);
        this.windowFun = WindowFun.getWindowFunction(windowFun, null);
    }

    @Override
    public double[] transform(double[] audioDoubleBuffer) {
        int bufferSize = audioDoubleBuffer.length;
        double[] amplitudes = new double[audioDoubleBuffer.length / 2];
        windowFun.apply(audioDoubleBuffer);
        Complex[] result = fft.transform(audioDoubleBuffer, TransformType.FORWARD);
        for (int i = 0; i < amplitudes.length; i++) {
            amplitudes[i] = 2d / bufferSize
                    * Math.sqrt(Math.pow(result[i].getReal(), 2) + Math.pow(result[i].getImaginary(), 2));
        }
        return amplitudes;
    }

}
