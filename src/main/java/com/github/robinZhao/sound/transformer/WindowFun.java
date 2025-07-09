package com.github.robinZhao.sound.transformer;

import java.util.Optional;

public abstract class WindowFun {
    protected static final double TWO_PI = Math.PI * 2;

    protected WindowFun() {
    }

    public void apply(double[] samples) {
        for (int n = 0; n < samples.length; ++n) {
            samples[n] *= this.getValue(samples.length, n);
        }

    }

    protected abstract double getValue(int length, int idx);

    public static class BartlettWindow extends WindowFun {
        public double getValue(int length, int index) {
            return (2 / (length - 1))
                    * ((length - 1) / 2d - Math.abs(index - (length - 1) / 2d));
        }
    }

    public static class BartlettHannWindow extends WindowFun {
        public double getValue(int length, int index) {
            return 0.62 - 0.48 * Math.abs(index / (length - 1) - 0.5)
                    - 0.38 * Math.cos((Math.PI * 2 * index) / (length - 1));
        }
    }

    public static class BlackmanWindow extends WindowFun {
        double alpha;

        public BlackmanWindow(Double alpha) {
            this.alpha = Optional.ofNullable(alpha).orElse(0.16d);
        }

        public BlackmanWindow() {
            this.alpha = 0.16d;
        }

        public double getValue(int length, int index) {
            return (1 - alpha) / 2 -
                    0.5d * Math.cos((Math.PI * 2 * index) / (length - 1)) +
                    (alpha / 2) * Math.cos((4 * Math.PI * index) / (length - 1));
        }
    }

    public static class CosineWindow extends WindowFun {
        public double getValue(int length, int index) {
            return Math.cos((Math.PI * index) / (length - 1) - Math.PI / 2);
        }
    }

    public static class GaussWindow extends WindowFun {
        double alpha;

        public GaussWindow(Double alpha) {
            this.alpha = Optional.ofNullable(alpha).orElse(0.25d);
        }

        public GaussWindow() {
            this.alpha = 0.25d;
        }

        public double getValue(int length, int index) {
            return Math.pow(
                    Math.E,
                    -0.5 * Math.pow((index - (length - 1) / 2.0d) / ((alpha * (length - 1)) / 2.0d), 2.0d));
        }
    }

    public static class HammingWindow extends WindowFun {
        public double getValue(int length, int index) {
            return 0.54 - 0.46 * Math.cos((Math.PI * 2 * index) / (length - 1));
        }
    }

    public static class HannWindow extends WindowFun {
        public double getValue(int length, int index) {
            return 0.5 * (1 - Math.cos((Math.PI * 2 * index) / (length - 1)));
        }
    }

    public static class LanczozWindow extends WindowFun {
        public double getValue(int length, int index) {
            return Math.sin(Math.PI * ((2d * index) / (length - 1) - 1))
                    / (Math.PI * ((2d * index) / (length - 1) - 1));
        }
    }

    public static class RectangularWindow extends WindowFun {
        public double getValue(int length, int index) {
            return 1;
        }
    }

    public static class TriangularWindow extends WindowFun {
        public double getValue(int length, int index) {
            return (2d / length) * (length / 2d - Math.abs(index - (length - 1d) / 2d));
        }
    }

    public static WindowFun getWindowFunction(String windowFunc, Double alpha) {
        if (null == windowFunc) {
            windowFunc = "hann";
        }
        switch (windowFunc) {
            case "bartlett":
                return new BartlettWindow();
            case "bartlettHann":
                return new BartlettHannWindow();
            case "blackman":
                return new BlackmanWindow(alpha);
            case "cosine":
                return new CosineWindow();
            case "gauss":
                return new GaussWindow(alpha);
            case "hamming":
                return new HammingWindow();
            case "hann":
                return new HannWindow();
            case "lanczoz":
                return new LanczozWindow();
            case "rectangular":
                return new RectangularWindow();
            case "triangular":
                return new TriangularWindow();
            default:
                throw new RuntimeException("No such window function " + windowFunc);
        }
    }

}
