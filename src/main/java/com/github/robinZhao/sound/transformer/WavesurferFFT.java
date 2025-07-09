package com.github.robinZhao.sound.transformer;

import java.util.Optional;

public class WavesurferFFT {
    static final double LN2 = Math.log(2.0d);
    int bufferSize;
    double sampleRate;
    double bandwidth;
    double[] sinTable;
    double[] cosTable;
    double[] windowValues;
    long[] reverseTable;
    double peak;
    int peakBand;

    /**
     * Calculate FFT - Based on https://github.com/corbanbrook/dsp.js
     */

    public WavesurferFFT(int bufferSize, String windowFunc, Double alpha) {
        this.bufferSize = bufferSize;
        this.sinTable = new double[bufferSize];
        this.cosTable = new double[bufferSize];
        this.windowValues = new double[bufferSize];
        this.reverseTable = new long[bufferSize];

        this.peakBand = 0;
        this.peak = 0;

        switch (windowFunc) {
            case "bartlett":
                for (int i = 0; i < bufferSize; i++) {
                    this.windowValues[i] = (2 / (bufferSize - 1))
                            * ((bufferSize - 1) / 2 - Math.abs(i - (bufferSize - 1) / 2));
                }
                break;
            case "bartlettHann":
                for (int i = 0; i < bufferSize; i++) {
                    this.windowValues[i] = 0.62 - 0.48 * Math.abs(i / (bufferSize - 1) - 0.5)
                            - 0.38 * Math.cos((Math.PI * 2 * i) / (bufferSize - 1));
                }
                break;
            case "blackman":
                alpha = Optional.ofNullable(alpha).orElse(0.16d);
                for (int i = 0; i < bufferSize; i++) {
                    this.windowValues[i] = (1 - alpha) / 2 -
                            0.5 * Math.cos((Math.PI * 2 * i) / (bufferSize - 1)) +
                            (alpha / 2) * Math.cos((4 * Math.PI * i) / (bufferSize - 1));
                }
                break;
            case "cosine":
                for (int i = 0; i < bufferSize; i++) {
                    this.windowValues[i] = Math.cos((Math.PI * i) / (bufferSize - 1) - Math.PI / 2);
                }
                break;
            case "gauss":
                alpha = Optional.ofNullable(alpha).orElse(0.25d);
                for (int i = 0; i < bufferSize; i++) {
                    this.windowValues[i] = Math.pow(
                            Math.E,
                            -0.5 * Math.pow((i - (bufferSize - 1) / 2) / ((alpha * (bufferSize - 1)) / 2), 2));
                }
                break;
            case "hamming":
                for (int i = 0; i < bufferSize; i++) {
                    this.windowValues[i] = 0.54 - 0.46 * Math.cos((Math.PI * 2 * i) / (bufferSize - 1));
                }
                break;
            case "hann":
            case "":
                for (int i = 0; i < bufferSize; i++) {
                    this.windowValues[i] = 0.5 * (1 - Math.cos((Math.PI * 2 * i) / (bufferSize - 1)));
                }
                break;
            case "lanczoz":
                for (int i = 0; i < bufferSize; i++) {
                    this.windowValues[i] = Math.sin(Math.PI * ((2 * i) / (bufferSize - 1) - 1))
                            / (Math.PI * ((2 * i) / (bufferSize - 1) - 1));
                }
                break;
            case "rectangular":
                for (int i = 0; i < bufferSize; i++) {
                    this.windowValues[i] = 1;
                }
                break;
            case "triangular":
                for (int i = 0; i < bufferSize; i++) {
                    this.windowValues[i] = (2 / bufferSize) * (bufferSize / 2 - Math.abs(i - (bufferSize - 1) / 2));
                }
                break;
            default:
                throw new RuntimeException("No such window function " + windowFunc);
        }

        int limit = 1;
        int bit = bufferSize >> 1;

        while (limit < bufferSize) {
            for (int i = 0; i < limit; i++) {
                this.reverseTable[i + limit] = this.reverseTable[i] + bit;
            }

            limit = limit << 1;
            bit = bit >> 1;
        }

        for (int i = 0; i < bufferSize; i++) {
            this.sinTable[i] = Math.sin(-Math.PI / i);
            this.cosTable[i] = Math.cos(-Math.PI / i);
        }
    }

    public double[] calculateSpectrum(double[] buffer) {
        double[] real = new double[bufferSize];
        double[] imag = new double[bufferSize];
        double bSi = 2d / this.bufferSize;
        double rval;
        double ival;
        double mag;
        double[] spectrum = new double[bufferSize / 2];

        double k = Math.floor(Math.log(bufferSize) / LN2);

        if (Math.pow(2d, k) != bufferSize) {
            throw new RuntimeException("Invalid buffer size, must be a power of 2.");
        }
        if (bufferSize != buffer.length) {
            throw new RuntimeException(
                    "Supplied buffer is not the same size as defined FFT. FFT Size: " +
                            bufferSize +
                            " Buffer Size: " +
                            buffer.length);
        }

        long halfSize = 1;
        double phaseShiftStepReal,
                phaseShiftStepImag,
                currentPhaseShiftReal,
                currentPhaseShiftImag;
        long off;
        double tr,
                ti,
                tmpReal;

        for (int i = 0; i < bufferSize; i++) {
            real[i] = buffer[(int) reverseTable[i]] * this.windowValues[(int) reverseTable[i]];
            imag[i] = 0;
        }

        while (halfSize < bufferSize) {
            phaseShiftStepReal = cosTable[(int) halfSize];
            phaseShiftStepImag = sinTable[(int) halfSize];

            currentPhaseShiftReal = 1;
            currentPhaseShiftImag = 0;

            for (int fftStep = 0; fftStep < halfSize; fftStep++) {
                long i = fftStep;

                while (i < bufferSize) {
                    off = i + halfSize;
                    tr = currentPhaseShiftReal * real[(int) off] - currentPhaseShiftImag * imag[(int) off];
                    ti = currentPhaseShiftReal * imag[(int) off] + currentPhaseShiftImag * real[(int) off];

                    real[(int) off] = real[(int) i] - tr;
                    imag[(int) off] = imag[(int) i] - ti;
                    real[(int) i] += tr;
                    imag[(int) i] += ti;

                    i += halfSize << 1;
                }

                tmpReal = currentPhaseShiftReal;
                currentPhaseShiftReal = tmpReal * phaseShiftStepReal - currentPhaseShiftImag * phaseShiftStepImag;
                currentPhaseShiftImag = tmpReal * phaseShiftStepImag + currentPhaseShiftImag * phaseShiftStepReal;
            }

            halfSize = halfSize << 1;
        }

        for (int i = 0, N = bufferSize / 2; i < N; i++) {
            rval = real[i];
            ival = imag[i];
            mag = bSi * Math.sqrt(rval * rval + ival * ival);

            if (mag > this.peak) {
                this.peakBand = i;
                this.peak = mag;
            }
            spectrum[i] = mag;
        }
        return spectrum;
    }
}
