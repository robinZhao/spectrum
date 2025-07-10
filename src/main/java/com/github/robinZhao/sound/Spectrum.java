package com.github.robinZhao.sound;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.github.robinZhao.sound.transformer.WavesurferTransformer;

public class Spectrum {
    private AudioInputStream audioInputStream;
    private AudioFormat format;
    private int bufferSize = 1024;
    byte[] audioByteBuffer;
    private AudioDoubleConverter audioFloatConverter;
    private int channels;
    private int sampleSize;
    private int byteBufferLength;
    private byte[][] channelsBytes;
    private int gainDB = 20;
    private int rangeDB = 80;
    private List<double[]>[] frequenciesData;
    private ColorMap colorMap = new ColorMap(ColorMap.Type.roseus);
    ScaleFilter scale;
    SpectrumTransformer spectrumTransformer;

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        int bufferSize = 1024;
        Spectrum spc = new Spectrum(new File("20250710_164040.wav"), bufferSize,
                ScaleFilter.Type.mel,
                new WavesurferTransformer(bufferSize, "hann"));
        spc.run();
        System.out.println(System.currentTimeMillis() - startTime);
        spc.drawSpctrogram(0,spc.getAudioFormat().getSampleRate()/2,1024, 800, 40,20,-1);
    }

    public Spectrum(File audioFile, int bufferSize, ScaleFilter.Type scaleType, SpectrumTransformer transformer) {
        this.bufferSize = bufferSize;
        try {
            audioInputStream = AudioSystem.getAudioInputStream(audioFile);
            this.format = audioInputStream.getFormat();
            this.sampleSize = format.getSampleSizeInBits() / 8;
            this.byteBufferLength = bufferSize * this.format.getFrameSize();
            this.audioByteBuffer = new byte[this.byteBufferLength];
            this.channels = this.format.getFrameSize() / sampleSize;
            this.audioFloatConverter = AudioDoubleConverter.getConverter(this.format);
            this.frequenciesData = new List[this.channels];
            this.scale = new ScaleFilter(scaleType, this.format.getFrameRate(), bufferSize);
            for (int i = 0; i < this.channels; i++) {
                this.frequenciesData[i] = new ArrayList<>();
            }
            this.setSpectrumTransformer(transformer);
        } catch (Exception e) {
            throw new RuntimeException("音频文件读取失败", e);
        }

    }

    public Spectrum(File audioFile, int bufferSize, ScaleFilter.Type scaleType) {
        this(audioFile, bufferSize, scaleType, new WavesurferTransformer(bufferSize, "hann"));
    }

    public void setSpectrumTransformer(SpectrumTransformer transformer) {
        this.spectrumTransformer = transformer;
    }

    public void run() {
        while (true) {
            try {
                int bytesRead = this.audioInputStream.read(this.audioByteBuffer);
                if (bytesRead == -1) {
                    break;
                }
                channelSplit();
                for (int i = 0; i < channels; i++) {
                    double[] audioDoubleBuffer = new double[bufferSize];
                    audioFloatConverter.toDoubleArray(this.channelsBytes[0], audioDoubleBuffer);
                    this.processStep(audioDoubleBuffer, i);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void channelSplit() {
        this.channelsBytes = new byte[channels][byteBufferLength / channels];
        for (int i = 0; i < byteBufferLength / channels/sampleSize; i++) {
            for (int j = 0; j < channels; j++) {
                System.arraycopy(audioByteBuffer, (i * channels + j) * sampleSize, channelsBytes[j], i * sampleSize,
                        sampleSize);
            }
        }
    }

    private int[][] convertToColorMap(List<double[]> oldMatrix, int columnsNumber) {
        // db转颜色，按比例转为0-255的颜色序号
        double[][] colorMatrix = new double[oldMatrix.size()][oldMatrix.get(0).length];
        for (int i = 0; i < oldMatrix.size(); i++) {
            for (int j = 0; j < oldMatrix.get(i).length; j++) {
                double valueDB = oldMatrix.get(i)[j];
                if (valueDB < -this.rangeDB) {
                    valueDB = -this.rangeDB;
                } else if (valueDB > 0) {
                    valueDB = 0;
                }
                colorMatrix[i][j] = valueDB / this.rangeDB * 255 + 255;
            }
        }
        //
        double oldPiece = 1d / colorMatrix.length;
        double newPiece = 1d / columnsNumber;
        int oldColumnsNumber = colorMatrix[0].length;
        int[][] newMatrix = new int[columnsNumber][oldColumnsNumber];
        for (int i = 0; i < columnsNumber; i++) {
            double[] column = new double[oldColumnsNumber];

            for (int j = 0; j < colorMatrix.length; j++) {
                double oldStart = j * oldPiece;
                double oldEnd = oldStart + oldPiece;
                double newStart = i * newPiece;
                double newEnd = newStart + newPiece;
                double overlap = Math.max(0d, Math.min(oldEnd, newEnd) - Math.max(oldStart, newStart));

                /* eslint-disable max-depth */
                if (overlap > 0) {
                    for (int k = 0; k < oldColumnsNumber; k++) {
                        column[k] += (overlap / newPiece) * colorMatrix[j][k];
                    }
                }
                /* eslint-enable max-depth */
            }

            int[] intColumn = new int[oldColumnsNumber];

            for (int m = 0; m < oldColumnsNumber; m++) {
                intColumn[m] = (int) Math.round(column[m]);
            }

            newMatrix[i] = intColumn;
        }

        return newMatrix;
    }

    public void processStep(double[] audioDoubleBuffer, int channelIdx) {
        double[] amplitudes = this.spectrumTransformer.transform(audioDoubleBuffer);
        if (null != this.scale) {
            amplitudes = this.scale.applyFilterBank(amplitudes);
        }
        double[] array = new double[amplitudes.length];
        for (int j = 0; j < array.length; j++) {
            // Based on: https://manual.audacityteam.org/man/spectrogram_view.html
            double magnitude = Math.max(amplitudes[j], 1e-12);
            double valueDB = 20 * Math.log10(magnitude);
            array[j] = valueDB + this.gainDB;
        }
        this.frequenciesData[channelIdx].add(array);
    }

    private void drawFreqMark(Graphics g,double frequencyMin,double frequencyMax,int y, int height) {
        //int maxY = height > 0 ? height : 512;
        double stepHeight = 256d/5;
        double stepCount = height / stepHeight;
        double scaleMin = this.scale.hzToScale(frequencyMin);
        double scaleMax = this.scale.hzToScale(frequencyMax);


        for (int i = 0; i <= stepCount; i++) {
            double hz =  this.scale.scaleToHz(scaleMin + (i / stepCount) * (scaleMax - scaleMin));
            int lableY = y + height - (int)(i*stepHeight);
            if (lableY <= y) {
                lableY = y + 10;
            }
            if (lableY >= y + height) {
                lableY = y + height;
            }
            g.setColor(colorMap.getColor(255));
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.drawString(this.labelText(hz), 0, lableY);
        }
    }

    private void drawTimeMark(Graphics g,double duration,int picWidth,int y,int markWidth) {
        int durationWidth = picWidth-markWidth;
        double stepCount = duration;
        for(int i=0;i<=stepCount;i++){
            //double second = i/stepCount*duration;
            g.setColor(colorMap.getColor(255));
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.drawString(String.format("%ds", i), markWidth+(int)(i*durationWidth/stepCount), y);
        }
    }

     /**
     * @param frequencyMin  图像中最小频率
     * @param frequencyMin  图像中最大频率
     * @param width      图片宽度
     * @param height     图片高度
     * @param channelIdx 绘制的声道,小于0代表全部声道，大于等于0代表指定声道
     */
    public void drawSpctrogram(double frequencyMin,double frequencyMax,int width, int height, int channelIdx) {
        this.drawSpctrogram(frequencyMin, frequencyMax, width, height, 0,-1, channelIdx);
    }

    /**
     * @param frequencyMin  图像中最小频率
     * @param frequencyMin  图像中最大频率
     * @param width      图片宽度
     * @param height     图片高度
     * @param markLeftWidth  左侧刻度占用的宽度
     * @param markBottomHeight  底部刻度占用的高度
     * @param channelIdx 绘制的声道,小于0代表全部声道，大于等于0代表指定声道
     */
    public void drawSpctrogram(double frequencyMin,double frequencyMax,int picWidth, int picHeight, int markLeftWidth,int markBottomHeight,int channelIdx) {
        if (null == this.frequenciesData || this.frequenciesData.length == 0) {
            throw new RuntimeException("频谱图绘制错误，无数据");
        }
        if (channelIdx >= this.frequenciesData.length) {
            throw new RuntimeException("频谱图绘制错误，声道" + channelIdx + "不存在");
        }
        // Maximum frequency represented in `frequenciesData`
        int freqFrom = (int) this.format.getSampleRate() / 2;
        int width=markLeftWidth>0?picWidth-markLeftWidth:picWidth;
        int height = markBottomHeight>0?picHeight-markBottomHeight:picHeight;
        int innerHeight = height / frequenciesData.length;
        if (channelIdx >= 0) {
            innerHeight = height;
        }
        // Minimum and maximum frequency we want to draw
        BufferedImage image = new BufferedImage(picWidth, picHeight, BufferedImage.TYPE_INT_BGR);
        Graphics spectrCc = image.getGraphics();

        if (frequencyMax > freqFrom) {
            // Draw background since spectrogram will not fill the entire canvas
            Color bgColor = this.colorMap.getColor(0);
            spectrCc.setColor(bgColor);
            spectrCc.fillRect(0, 0, width, height);
        }

        for (int c = 0; c < frequenciesData.length; c++) {
            // for each channel
            if (channelIdx >= 0 && channelIdx != c) {
                continue;
            }
            int[][] pixels = this.convertToColorMap(frequenciesData[c], width);
            int bitmapHeight = pixels[0].length;
            BufferedImage cImage = new BufferedImage(width, bitmapHeight, BufferedImage.TYPE_INT_RGB);
            Graphics cg = cImage.getGraphics();
            for (int i = 0; i < pixels.length; i++) {
                for (int j = 0; j < pixels[i].length; j++) {
                    int hIndex = (bitmapHeight - j - 1);
                    Color color = this.colorMap.getColor(pixels[i][j]);
                    cg.setColor(color);
                    cg.drawLine(i, hIndex, i, hIndex);
                }
            }

            // The relative positions of `freqMin` and `freqMax` in `imageData`
            double rMin = this.scale.hzToScale(frequencyMin) / this.scale.hzToScale(freqFrom);
            double rMax = this.scale.hzToScale(frequencyMax) / this.scale.hzToScale(freqFrom);

            // Only relevant if `freqMax > freqFrom`
            double rMax1 = Math.min(1, rMax);
            int dx0 = picWidth-width;
            int dy0 = (int) (innerHeight * (c + 1 - rMax1 / rMax));
            int dx1 = dx0+width;
            int dy1 = (c + 1) * (int) (innerHeight);

            spectrCc.drawImage(cImage, dx0, dy0, dx1, dy1,
                    0, (int) Math.round(bitmapHeight * (1 - rMax1)), width,
                    (int) Math.round(bitmapHeight * (rMax1 - rMin)), null);

            if(markLeftWidth>=0)drawFreqMark(spectrCc, frequencyMin,frequencyMax,innerHeight * c, innerHeight);
        }
        if(markBottomHeight>=0)drawTimeMark(spectrCc,this.audioInputStream.getFrameLength()/this.format.getSampleRate(),picWidth,picHeight-markBottomHeight/2,markLeftWidth>=0?markLeftWidth:0);
        try {
            ImageIO.write(image, "png", new File("test.png"));
        } catch (IOException e) {
            throw new RuntimeException("图片写入失败", e);
        }

    }

    private String labelText(double freq) {
        return freq >= 1000 ? String.format("%.1f kHz", (freq / 1000))
                : String.valueOf(Math.round(freq)) + " Hz";
    }

    public List<double[]>[] getFrequenciesData() {
        return this.frequenciesData;
    }

    
    public double idxToHz(int idx, int length) {
        return this.scale
                .scaleToHz((double) (idx) / length * (this.scale.hzToScale(this.format.getSampleRate() / 2)));
    }

    /**
     * hz转换为数组，分辨率问题，可能会存在1hz的误差,计算时上加+1hz来算范围
     * 
     * @param hz
     * @param length
     * @return
     */
    public int hzToIdx(double hz, int length) {
        return (int) Math.round((double) length
                * (this.scale.hzToScale(hz) / this.scale.hzToScale(this.format.getSampleRate() / 2)));
    }

    public AudioFormat getAudioFormat(){
        return this.format;
    }

}
