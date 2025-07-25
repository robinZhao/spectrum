package com.github.robinZhao.sound;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.github.robinZhao.sound.transformer.WavesurferTransformer;

public class Spectrum {
    private File file;
    private AudioInputStream audioInputStream;
    private AudioFormat format;
    private int bufferSize = 512;
    private AudioDoubleConverter audioFloatConverter;
    private int channels;
    private int sampleSize;
    private long frameLength;
    private double duration;
    private int gainDB = 0;
    private int rangeDB = 140;
    private Color frontColor = new Color(215, 0, 194);
    private Color backColor = new Color(0, 0, 0);
    private Color fontColor = new Color(255, 255, 255);
    private List<double[]>[] frequenciesData;
    private List<double[]>[] amplitudeData;;
    private List<Double> timeline = new ArrayList<>();
    private int overlap = 0;
    private boolean mergeChannel = true;
    private ColorMap colorMap = new ColorMap(ColorMap.Type.roseus);
    ScaleFilter scale;
    SpectrumTransformer spectrumTransformer;

    public static void main(String[] args) {
        int bufferSize = 512;
        String fileName = "test1.wav";
        File file = new File(fileName);
        Spectrum spc = new Spectrum(file, bufferSize,
                ScaleFilter.Type.mel,
                new WavesurferTransformer(bufferSize, "hann"));
        long frameLength = (file.length() - 44) / spc.format.getFrameSize();
        int overlap = Math.max(0, (int) Math.round((double) bufferSize - (double) frameLength / 300.0));
        spc.setOverlap(overlap);
        spc.mergeChannel=false;
        spc.run();
        spc.drawSpctrogram("test.png", 0, 8000, 668, 200, 0, 0, -1);
        spc.drawSpctrogramLineVideo("test", 0, spc.getAudioFormat().getSampleRate() / 2, 800, 400, 0, 0, -1);
        spc.drawAmplitudeLine("amp", 800, 400, 0, 0, -1);
        spc.drawDbLine("db", 800, 400, 0, 0, -1);
        spc.drawAmplitudeLineVideo("amp", 800, 400, 0, 0, -1);
        spc.drawDbLineVideo("db", 800, 400, 0, 0, -1);
    }

    /**
     * 创建频谱对象
     * 
     * @param audioFile   输入音频文件 wav
     * @param bufferSize  每个分段多少帧
     * @param scaleType   刻度类型 ScaleFilter.Type
     * @param transformer 归一化的double类型时域信号转换为频域数据的转换器
     */
    public Spectrum(File audioFile, int bufferSize, ScaleFilter.Type scaleType, SpectrumTransformer transformer) {
        this(new Supplier<AudioInputStream>() {
            @Override
            public AudioInputStream get() {
                try {
                    return AudioSystem.getAudioInputStream(audioFile);
                } catch (Exception e) {
                    throw new RuntimeException("读取音频文件失败" + audioFile, e);
                }
            }
        }.get(), bufferSize, scaleType, transformer);
        this.file = audioFile;
    }

    /**
     * 创建频谱对象
     * 
     * @param audioInputStream 输入音频流
     * @param bufferSize       每个分段多少帧
     * @param scaleType        刻度类型 ScaleFilter.Type
     * @param transformer      归一化的double类型时域信号转换为频域数据的转换器
     */
    public Spectrum(AudioInputStream audioInputStream, int bufferSize, ScaleFilter.Type scaleType,
            SpectrumTransformer transformer) {
        this.bufferSize = bufferSize;
        try {
            this.audioInputStream = audioInputStream;
            this.format = audioInputStream.getFormat();
            this.sampleSize = format.getSampleSizeInBits() / 8;
            this.channels = this.format.getFrameSize() / sampleSize;
            this.audioFloatConverter = AudioDoubleConverter.getConverter(this.format);
            this.scale = new ScaleFilter(scaleType, this.format.getFrameRate(), bufferSize);
            this.setSpectrumTransformer(transformer);
        } catch (Exception e) {
            throw new RuntimeException("音频文件读取失败", e);
        }

    }

    public Spectrum(AudioInputStream audioInputStream, int bufferSize, ScaleFilter.Type scaleType) {
        this(audioInputStream, bufferSize, scaleType, new WavesurferTransformer(bufferSize, "hann"));
    }

    public Spectrum(File audioFile, int bufferSize, ScaleFilter.Type scaleType) {
        this(audioFile, bufferSize, scaleType, new WavesurferTransformer(bufferSize, "hann"));
    }

    public void setSpectrumTransformer(SpectrumTransformer transformer) {
        this.spectrumTransformer = transformer;
    }

    public void run() {
        if (this.mergeChannel) {
            this.frequenciesData = new List[1];
            this.amplitudeData = new List[1];
        } else {
            this.frequenciesData = new List[this.channels];
            this.amplitudeData = new List[this.channels];
        }
        for (int i = 0; i < this.frequenciesData.length; i++) {
            this.frequenciesData[i] = new ArrayList<>();
            this.amplitudeData[i] = new ArrayList<>();
        }
        if (overlap >= bufferSize) {
            throw new RuntimeException("overlap过大,overlap必须小于bufferSize");
        }
        long totalBytes = 0;
        int onceFrameCount = this.bufferSize - this.overlap;
        int onceSampleCount = onceFrameCount * channels;
        int onceReadBytes = onceFrameCount * this.format.getFrameSize();
        int bytesRead = 0;
        byte[] audioByteBuffer = new byte[onceReadBytes];
        double[] audioDoubleBuffer = new double[bufferSize * channels];
        double[] tempDoubleBuffer = new double[onceSampleCount];
        while (true) {
            try {
                // 读取readBytes个字节，追加到结尾
                bytesRead = this.audioInputStream.read(audioByteBuffer);
                if (bytesRead == -1) {
                    break;
                }
                totalBytes += bytesRead;
                audioFloatConverter.toDoubleArray(audioByteBuffer, tempDoubleBuffer);
                // 已读采样数小于一次需要读取的样本数，补0
                int readSampleCount = bytesRead / this.sampleSize;
                while (readSampleCount < onceSampleCount) {
                    tempDoubleBuffer[readSampleCount++] = 0.0d;
                }
                // 把后overlap个元素往前移到开头
                System.arraycopy(audioDoubleBuffer, onceSampleCount, audioDoubleBuffer, 0, overlap);
                System.arraycopy(tempDoubleBuffer, 0, audioDoubleBuffer, overlap, onceSampleCount);
                this.processStep(audioDoubleBuffer, bytesRead,
                        totalBytes / this.format.getFrameSize() / this.format.getSampleRate());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        this.frameLength = totalBytes / this.format.getFrameSize();
        this.duration = frameLength / this.format.getSampleRate();
    }

    public double[][] channelSplit(double[] audioDoubleBuffer) {
        double[][] channelDoubles = new double[channels][bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            for (int j = 0; j < channels; j++) {
                channelDoubles[j][i] = audioDoubleBuffer[channels * i + j];
            }
        }
        return channelDoubles;
    }

    public double[] channelMerge(double[] audioDoubleBuffer) {
        double[] channelDoubles = new double[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            double total = 0;
            for (int j = 0; j < channels; j++) {
                total += audioDoubleBuffer[channels * i + j];
            }
            channelDoubles[i] = total / channels;
        }
        return channelDoubles;
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

    public void processStep(double[] audioDoubleBuffer, int framesRead, double time) {
        this.timeline.add(time);
        if (this.mergeChannel) {
            double[] channelDoubles = channelMerge(audioDoubleBuffer);
            this.processStepChannel(channelDoubles, 0, framesRead);
        } else {
            double[][] channelDoubles = channelSplit(audioDoubleBuffer);
            for (int i = 0; i < channels; i++) {
                this.processStepChannel(channelDoubles[i], i, framesRead);
            }
        }
    }

    public void processStepChannel(double[] audioDoubleBuffer, int channelIdx, int framesRead) {
        double[] amplitudes = this.spectrumTransformer.transform(audioDoubleBuffer);
        if (null != this.scale) {
            amplitudes = this.scale.applyFilterBank(amplitudes);
        }
        double[] array = new double[amplitudes.length];
        for (int j = 0; j < array.length; j++) {
            // Based on: https://manual.audacityteam.org/man/spectrogram_view.html
            double magnitude = Math.max(Math.abs(amplitudes[j]), 1e-12);
            double valueDB = 20 * Math.log10(magnitude);
            array[j] = valueDB + this.gainDB;
        }
        this.amplitudeData[channelIdx].add(audioDoubleBuffer);
        this.frequenciesData[channelIdx].add(array);
    }

    private void drawFreqMark(Graphics g, int x, int y, int height, double frequencyMin, double frequencyMax) {
        // int maxY = height > 0 ? height : 512;
        double stepHeight = 256d / 5;
        double scaleMin = this.scale.hzToScale(frequencyMin);
        double scaleMax = this.scale.hzToScale(frequencyMax);

        for (double markY = 0; markY < height; markY += stepHeight) {
            double hz = this.scale.scaleToHz(scaleMin + (markY / height) * (scaleMax - scaleMin));
            g.setColor(colorMap.getColor(255));
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.drawString(this.labelText(hz), x, y + height - (int) markY);
        }
    }

    private void drawFreqMarkH(Graphics g, int x, int y, int width, double frequencyMin, double frequencyMax) {
        // int maxY = height > 0 ? height : 512;
        double scaleMin = this.scale.hzToScale(frequencyMin);
        double scaleMax = this.scale.hzToScale(frequencyMax);
        // double scaleFrom = this.scale.hzToScale(8000);
        for (int i = 0; i <= width; i += 50) {
            double hz = this.scale.scaleToHz(scaleMin + (scaleMax - scaleMin) * (double) i / width);
            g.setColor(colorMap.getColor(255));
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.drawString(this.labelText(hz), x + i, y);
        }

        // g.drawString("8KH", (int)(width*scaleFrom/scaleMax), -10);
    }

    private void drawTimeMark(Graphics g, int x, int y, int width, double duration, double startTime) {
        g.setColor(fontColor);
        for (int i = 0; i <= width; i += 50) {
            // double second = i/stepCount*duration;
            double timeScale = startTime + duration * i / width;
            g.setColor(colorMap.getColor(255));
            g.setFont(new Font("Arial", Font.BOLD, 12));
            if (timeScale > 0) {
                g.drawString(String.format("%.2fs", timeScale), x + i, y);
            }
        }
    }

    private void drawDomainMark(Graphics g, int x, int y, int height, double max, boolean reverse) {
        g.setColor(fontColor);
        for (int i = 0; i < height; i += 50) {
            int up = y - i + 5;
            up = up < 10 ? 10 : up;
            int down = y + i + 5;
            down = down < 10 ? 10 : down;
            g.drawString(String.format("%.2f", max * i / height * (i == 0 || reverse ? 1 : -1)), x,
                    reverse ? up : down);
        }
    }

    /**
     * @param frequencyMin 图像中最小频率
     * @param frequencyMin 图像中最大频率
     * @param width        图片宽度
     * @param height       图片高度
     * @param channelIdx   绘制的声道,小于0代表全部声道，大于等于0代表指定声道
     */
    public void drawSpctrogram(String output, double frequencyMin, double frequencyMax, int width, int height,
            int channelIdx) {
        this.drawSpctrogram(output, frequencyMin, frequencyMax, width, height, 0, -1, channelIdx);
    }

    /**
     * @param frequencyMin     图像中最小频率
     * @param frequencyMin     图像中最大频率
     * @param width            图片宽度
     * @param height           图片高度
     * @param markLeftWidth    左侧刻度占用的宽度
     * @param markBottomHeight 底部刻度占用的高度
     * @param channelIdx       绘制的声道,小于0代表全部声道，大于等于0代表指定声道
     */
    public void drawSpctrogram(String output, double frequencyMin, double frequencyMax, int picWidth, int picHeight,
            int markLeftWidth,
            int markBottomHeight, int channelIdx) {
        if (null == this.frequenciesData || this.frequenciesData.length == 0) {
            throw new RuntimeException("频谱图绘制错误，无数据");
        }
        if (channelIdx >= this.frequenciesData.length) {
            throw new RuntimeException("频谱图绘制错误，声道" + channelIdx + "不存在");
        }
        // Maximum frequency represented in `frequenciesData`
        int freqFrom = (int) this.format.getSampleRate() / 2;
        int width = markLeftWidth > 0 ? picWidth - markLeftWidth : picWidth;
        int height = markBottomHeight > 0 ? picHeight - markBottomHeight : picHeight;
        int innerHeight = height / frequenciesData.length;
        if (channelIdx >= 0) {
            innerHeight = height;
        }
        // Minimum and maximum frequency we want to draw
        BufferedImage image = new BufferedImage(picWidth, picHeight, BufferedImage.TYPE_3BYTE_BGR);
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
            BufferedImage cImage = new BufferedImage(width, bitmapHeight, BufferedImage.TYPE_3BYTE_BGR);
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
            int dx0 = picWidth - width;
            int dy0 = (int) (innerHeight * (c + 1 - rMax1 / rMax));
            int dx1 = dx0 + width;
            int dy1 = (c + 1) * (int) (innerHeight);

            spectrCc.drawImage(cImage, dx0, dy0, dx1, dy1,
                    0, (int) Math.round(bitmapHeight * (1 - rMax1)), width,
                    (int) Math.round(bitmapHeight * (1 - rMin)), null);

            if (markLeftWidth >= 0) {
                spectrCc.setColor(fontColor);
                drawFreqMark(spectrCc, 0, innerHeight * c, innerHeight, frequencyMin, frequencyMax);
            }
        }
        if (markBottomHeight >= 0)
            drawTimeMark(spectrCc, markLeftWidth, picHeight - markBottomHeight / 2, width,
                    (frameLength > 0 ? frameLength : audioInputStream.getFrameLength()) / this.format.getSampleRate(),
                    0);
        try {
            ImageIO.write(image, "png", new File(output));
        } catch (IOException e) {
            throw new RuntimeException("图片写入失败", e);
        }

    }

    /**
     * @param frequencyMin     图像中最小频率
     * @param frequencyMin     图像中最大频率
     * @param width            图片宽度
     * @param height           图片高度
     * @param markLeftWidth    左侧刻度占用的宽度
     * @param markBottomHeight 底部刻度占用的高度
     * @param channelIdx       绘制的声道,小于0代表全部声道，大于等于0代表指定声道
     */
    public void drawSpctrogramLineVideo(String output, double frequencyMin, double frequencyMax, int picWidth,
            int picHeight,
            int markLeftWidth, int markBottomHeight, int channelIdx) {
        if (null == this.frequenciesData || this.frequenciesData.length == 0) {
            throw new RuntimeException("频谱图绘制错误，无数据");
        }
        if (channelIdx >= this.frequenciesData.length) {
            throw new RuntimeException("频谱图绘制错误，声道" + channelIdx + "不存在");
        }
        // Maximum frequency represented in `frequenciesData`
        int freqFrom = (int) this.format.getSampleRate() / 2;
        int width = markLeftWidth > 0 ? picWidth - markLeftWidth : picWidth;
        int height = markBottomHeight > 0 ? picHeight - markBottomHeight : picHeight;
        int innerHeight = height / frequenciesData.length;
        if (channelIdx >= 0) {
            innerHeight = height;
        }

        // double rate = timeline.size()/timeline.get(timeline.size()-1);
        // VideoCreator Video = new VideoCreator(output+".Video", picWidth, picHeight, rate);
        VideoCreator video = new VideoCreator(output + ".mp4", picWidth, picHeight, 60.0, file);
        for (int i = 0; i < frequenciesData[0].size(); i++) {
            // Minimum and maximum frequency we want to draw
            BufferedImage image = new BufferedImage(picWidth, picHeight, BufferedImage.TYPE_3BYTE_BGR);
            Graphics spectrCc = image.getGraphics();
            // ((Graphics2D)spectrCc).setComposite(AlphaComposite.Src);
            spectrCc.setColor(backColor);
            spectrCc.fillRect(0, 0, width, height);
            for (int c = 0; c < frequenciesData.length; c++) {
                // for each channel
                if (channelIdx >= 0 && channelIdx != c) {
                    continue;
                }
                // int[][] pixels = this.convertToColorMap(frequenciesData[c], width);
                int bitmapWidth = frequenciesData[c].get(0).length;
                BufferedImage cImage = new BufferedImage(bitmapWidth, innerHeight, BufferedImage.TYPE_3BYTE_BGR);
                Graphics cg = cImage.getGraphics();
                int[] x = new int[bitmapWidth + 2];
                int[] y = new int[bitmapWidth + 2];
                x[0] = 0;
                x[bitmapWidth + 1] = bitmapWidth - 1;
                y[0] = innerHeight;
                y[bitmapWidth + 1] = innerHeight;
                for (int j = 0; j < frequenciesData[c].get(i).length; j++) {
                    double doubleDb = frequenciesData[c].get(i)[j];
                    x[j + 1] = j;
                    y[j + 1] = (int) (innerHeight * (1 - (rangeDB + doubleDb) / (double) rangeDB));
                }
                cg.setColor(frontColor);
                y[0] = y[1];
                y[bitmapWidth + 1] = y[bitmapWidth];
                cg.drawPolyline(x, y, x.length);
                // The relative positions of `freqMin` and `freqMax` in `imageData`
                double rMin = this.scale.hzToScale(frequencyMin) / this.scale.hzToScale(freqFrom);
                double rMax = this.scale.hzToScale(frequencyMax) / this.scale.hzToScale(freqFrom);

                // Only relevant if `freqMax > freqFrom`
                double rMax1 = Math.min(1, rMax);
                int dx0 = picWidth - width;
                int dy0 = c * innerHeight;
                int dx1 = (int) (dx0 + width * (rMax1 / rMax));
                int dy1 = (c + 1) * (int) (innerHeight);
                Image subImage = cImage.getSubimage((int) Math.round(bitmapWidth * rMin), 0,
                        (int) Math.round(bitmapWidth * (rMax1 - rMin)), innerHeight);
                Image scaledImage = subImage.getScaledInstance(dx1 - dx0, innerHeight, Image.SCALE_SMOOTH);
                spectrCc.drawImage(scaledImage, dx0, dy0, null);
                if (markLeftWidth >= 0) {
                    drawDomainMark(spectrCc, 0, c*innerHeight, innerHeight, rangeDB, false);
                }
            }



            if (markBottomHeight >= 0) {
                spectrCc.setColor(fontColor);
                // spectrCc.translate(picWidth - width, picHeight);
                drawFreqMarkH(spectrCc, markLeftWidth, picHeight, width, frequencyMin, frequencyMax);
                // spectrCc.translate(-(picWidth - width), -picHeight);
            }
            double endTime = this.timeline.get(i);
            double startTime = i == 0 ? 0 : endTime - bufferSize / this.format.getSampleRate();
            spectrCc.drawString(String.format("%.2fs", startTime), picWidth - 40, 10);
            // Video.addImage(image,(long)(endTime*1000));
            video.addImage(image, (long) (startTime * 1000000));
            if (i == this.frequenciesData[0].size() - 1) {
                video.addImage(image, (long)duration*1000000);
            }
        }
        // Video.finsh();
        video.finsh();
    }

    public void drawAmplitudeLine(String filePath, int picWidth, int picHeight, int markLeftWidth, int markBottomHeight,
            int channelIdx) {
        if (channelIdx > this.amplitudeData.length - 1) {
            throw new RuntimeException("要绘制的通道不存在");
        }
        int drawChannels = channelIdx < 0 ? this.amplitudeData.length : 1;
        int channelHeight = (picHeight - markBottomHeight) / drawChannels;
        int channelWidth = amplitudeData[0].size();
        BufferedImage image = new BufferedImage(picWidth, picHeight,
                BufferedImage.TYPE_3BYTE_BGR);
        Graphics g = image.getGraphics();
        g.setColor(backColor);
        g.fillRect(0, 0, picWidth, picHeight);
        for (int c = 0; c < this.amplitudeData.length; c++) {
            if (c != channelIdx && channelIdx >= 0)
                continue;
            BufferedImage cImage = new BufferedImage(channelWidth, channelHeight, BufferedImage.TYPE_3BYTE_BGR);
            Graphics g1 = cImage.getGraphics();
            g1.translate(0, channelHeight / 2);
            g1.setColor(frontColor);
            int[] x = new int[this.amplitudeData[c].size() + 2];
            int[] y = new int[this.amplitudeData[c].size() + 2];
            int[] y1 = new int[this.amplitudeData[c].size() + 2];
            x[0] = 0;
            y[0] = 0;
            y1[0] = 0;
            x[x.length - 1] = x.length - 3;
            y[x.length - 1] = 0;
            y1[x.length - 1] = 0;
            for (int i = 0; i < this.amplitudeData[c].size(); i++) {
                x[i + 1] = i;
                double[] temp = new double[amplitudeData[c].get(i).length];
                System.arraycopy(amplitudeData[c].get(i), 0, temp, 0, temp.length);
                // WindowFun.getWindowFunction("hann", null).apply(temp);
                double ampValue = Math.sqrt(Arrays.stream(temp).map(d -> Math.pow(d, 2)).average().orElse(0));
                int vy = (int) (channelHeight / 2 * ampValue);
                y[i + 1] = vy;
                y1[i + 1] = -vy;
            }
            g1.fillPolygon(x, y, x.length);
            g1.fillPolygon(x, y1, x.length);
            Image scaledImage = cImage.getScaledInstance(picWidth-markLeftWidth, channelHeight, Image.SCALE_REPLICATE);
            g.drawImage(scaledImage, markLeftWidth, c * channelHeight, null);
        }
        for (int c = 0; c < drawChannels; c++) {
            this.drawDomainMark(g, 0, channelHeight * c + channelHeight / 2, channelHeight / 2, 1, false);
            this.drawDomainMark(g, 0, channelHeight * c + channelHeight / 2, channelHeight / 2, 1, true);
        }
        if (markBottomHeight >= 0)
            drawTimeMark(g, markLeftWidth, picHeight, picWidth - markLeftWidth,
                    (frameLength > 0 ? frameLength : audioInputStream.getFrameLength()) / this.format.getSampleRate(),
                    0);
        try {
            ImageIO.write(image, "png", new File(filePath + ".png"));
        } catch (Exception e) {
            throw new RuntimeException("write iamge failed");
        }
    }

    public void drawDbLine(String filePath, int picWidth, int picHeight, int markLeftWidth, int markBottomHeight,
            int channelIdx) {
        if (channelIdx > this.amplitudeData.length - 1) {
            throw new RuntimeException("要绘制的通道不存在");
        }
        int drawChannels = channelIdx < 0 ? this.amplitudeData.length : 1;
        int channelHeight = (picHeight - markBottomHeight) / drawChannels;
        int channelWidth = amplitudeData[0].size();
        BufferedImage image = new BufferedImage(picWidth, picHeight,
                BufferedImage.TYPE_3BYTE_BGR);
        Graphics g = image.getGraphics();
        g.setColor(backColor);
        g.fillRect(0, 0, picWidth, picHeight);
        for (int c = 0; c < this.amplitudeData.length; c++) {
            if (c != channelIdx && channelIdx >= 0)
                continue;
            BufferedImage cImage = new BufferedImage(channelWidth, channelHeight, BufferedImage.TYPE_3BYTE_BGR);
            Graphics g1 = cImage.getGraphics();
            g1.setColor(frontColor);
            int[] x = new int[this.amplitudeData[c].size() + 2];
            int[] y1 = new int[this.amplitudeData[c].size() + 2];
            x[0] = 0;
            y1[0] = channelHeight;
            x[x.length - 1] = x.length - 3;
            y1[x.length - 1] = channelHeight;
            for (int i = 0; i < this.amplitudeData[c].size(); i++) {
                x[i + 1] = i;
                double[] temp = new double[amplitudeData[c].get(i).length];
                System.arraycopy(amplitudeData[c].get(i), 0, temp, 0, temp.length);
                // WindowFun.getWindowFunction("hann", null).apply(temp);
                double ampValue = Math.sqrt(Arrays.stream(temp).map(d -> Math.pow(d, 2)).average().orElse(0));
                double valueDB = 20 * Math.log10(Math.max(Math.abs(ampValue), 1e-12));
                valueDB += this.gainDB;
                if (valueDB < -this.rangeDB) {
                    valueDB = -this.rangeDB;
                } else if (valueDB > 0) {
                    valueDB = 0;
                }
                int vy = (int) (channelHeight * valueDB / rangeDB);

                y1[i + 1] = -vy;
            }
            g1.fillPolygon(x, y1, x.length);
            Image scaledImage = cImage.getScaledInstance(picWidth-markLeftWidth, channelHeight, Image.SCALE_REPLICATE);
            g.drawImage(scaledImage, markLeftWidth, c * channelHeight, null);
        }
        for (int c = 0; c < drawChannels; c++) {
            this.drawDomainMark(g, markLeftWidth, channelHeight * c, channelHeight, rangeDB, false);
        }
        if (markBottomHeight >= 0)
            drawTimeMark(g, markLeftWidth, picHeight, picWidth - markLeftWidth,
                    duration,
                    0);
        try {
            ImageIO.write(image, "png", new File(filePath + ".png"));
        } catch (Exception e) {
            throw new RuntimeException("write iamge failed");
        }
    }

    public void drawAmplitudeLineVideo(String filePath, int picWidth, int picHeight, int markLeftWidth, int markBottomHeight,
            int channelIdx) {
        if (channelIdx > this.amplitudeData.length - 1) {
            throw new RuntimeException("要绘制的通道不存在");
        }
        int drawChannels = channelIdx < 0 ? this.amplitudeData.length : 1;
        int channelHeight = (picHeight - markBottomHeight) / drawChannels;
        int channelWidth = this.amplitudeData[0].get(0).length;
        VideoCreator video = new VideoCreator(filePath + ".mp4", picWidth, picHeight, 60.0, file);
        for (int j = 0; j < this.amplitudeData[0].size(); j++) {
            BufferedImage image = new BufferedImage(picWidth, picHeight,
                    BufferedImage.TYPE_3BYTE_BGR);
            Graphics g = image.getGraphics();
            g.setColor(backColor);
            g.fillRect(0, 0, picWidth, picHeight);
            for (int c = 0; c < this.amplitudeData.length; c++) {
                if (c != channelIdx && channelIdx >= 0)
                    continue;
                BufferedImage cImage = new BufferedImage(channelWidth, channelHeight, BufferedImage.TYPE_3BYTE_BGR);
                Graphics g1 = cImage.getGraphics();
                g1.translate(0, channelHeight / 2);
                g1.setColor(frontColor);
                int[] x = new int[this.amplitudeData[c].get(j).length + 2];
                int[] y = new int[this.amplitudeData[c].get(j).length + 2];
                int[] y1 = new int[this.amplitudeData[c].get(j).length + 2];
                x[0] = 0;
                y[0] = 0;
                y1[0] = 0;
                x[x.length - 1] = x.length - 3;
                y[x.length - 1] = 0;
                y1[x.length - 1] = 0;
                for (int i = 0; i < this.amplitudeData[c].get(j).length; i++) {
                    x[i + 1] = i;
                    double ampValue = this.amplitudeData[c].get(j)[i];
                    int vy = (int) (channelHeight / 2 * ampValue);
                    y[i + 1] = vy;
                    y1[i + 1] = -vy;
                }
                g1.fillPolygon(x, y, x.length);
                g1.fillPolygon(x, y1, x.length);
                Image scaledImage = cImage.getScaledInstance(picWidth-markLeftWidth, channelHeight, Image.SCALE_REPLICATE);
                g.drawImage(scaledImage, markLeftWidth, c * channelHeight, null);
            }

            for (int c = 0; c < drawChannels; c++) {
                this.drawDomainMark(g, 0, channelHeight * c + channelHeight / 2, channelHeight / 2, 1, false);
                this.drawDomainMark(g, 0, channelHeight * c + channelHeight / 2, channelHeight / 2, 1, true);
            }
            double endTime = this.timeline.get(j);
            double startTime = endTime - this.bufferSize / this.format.getSampleRate();
            if (markBottomHeight >= 0)
                drawTimeMark(g, markLeftWidth, picHeight, picWidth - markLeftWidth,
                        endTime - startTime, startTime);
            g.drawString(String.format("%.2fs", startTime), picWidth - 40, 10);
            // Video.addImage(image,(long)(endTime*1000));
            video.addImage(image, (long) (startTime * 1000000));
            if (j == this.amplitudeData[0].size() - 1){
                video.addImage(image, (long) (endTime * 1000000));
            }
        }
        video.finsh();
    }

    public void drawDbLineVideo(String filePath, int picWidth, int picHeight, int markLeftWidth, int markBottomHeight,
            int channelIdx) {
        if (channelIdx > this.amplitudeData.length - 1) {
            throw new RuntimeException("要绘制的通道不存在");
        }
        int drawChannels = channelIdx < 0 ? this.amplitudeData.length : 1;
        int channelHeight = (picHeight - markBottomHeight) / drawChannels;
        int channelWidth = this.amplitudeData[0].get(0).length;
        VideoCreator video = new VideoCreator(filePath + ".mp4", picWidth, picHeight, 60.0, file);
        for (int j = 0; j < this.amplitudeData[0].size(); j++) {
            BufferedImage image = new BufferedImage(picWidth, picHeight,
                    BufferedImage.TYPE_3BYTE_BGR);
            Graphics g = image.getGraphics();
            g.setColor(backColor);
            g.fillRect(0, 0, picWidth, picHeight);
            for (int c = 0; c < this.amplitudeData.length; c++) {
                if (c != channelIdx && channelIdx >= 0)
                    continue;
                BufferedImage cImage = new BufferedImage(channelWidth, channelHeight, BufferedImage.TYPE_3BYTE_BGR);
                Graphics g1 = cImage.getGraphics();
                g1.setColor(frontColor);
                int[] x = new int[this.amplitudeData[c].get(j).length + 2];
                int[] y1 = new int[this.amplitudeData[c].get(j).length + 2];
                x[0] = 0;
                y1[0] = channelHeight;
                x[x.length - 1] = x.length - 3;
                y1[x.length - 1] = channelHeight;
                for (int i = 0; i < this.amplitudeData[c].get(j).length; i++) {
                    double valueDB = 20 * Math.log10(Math.max(Math.abs(amplitudeData[c].get(j)[i]), 1e-12));
                    valueDB += this.gainDB;
                    if (valueDB < -this.rangeDB) {
                        valueDB = -this.rangeDB;
                    } else if (valueDB > 0) {
                        valueDB = 0;
                    }
                    int vy = (int) (channelHeight * valueDB / rangeDB);
                    x[i + 1] = i;
                    y1[i + 1] = -vy;
                }
                g1.fillPolygon(x, y1, x.length);
                Image scaledImage = cImage.getScaledInstance(picWidth-markLeftWidth, channelHeight, Image.SCALE_REPLICATE);
                g.drawImage(scaledImage, markLeftWidth, c * channelHeight, null);
            }

            for (int c = 0; c < drawChannels; c++) {
                this.drawDomainMark(g, markLeftWidth, channelHeight * c, channelHeight, rangeDB, false);
            }
            double endTime = this.timeline.get(j);
            double startTime = endTime - this.bufferSize / this.format.getSampleRate();
            if (markBottomHeight >= 0)
                drawTimeMark(g, markLeftWidth, picHeight, picWidth - markLeftWidth,
                        endTime - startTime, startTime);
            g.drawString(String.format("%.2fs", startTime), picWidth - 40, 10);
            // Video.addImage(image,(long)(endTime*1000));
            video.addImage(image, (long) (endTime * 1000000));
            if (j == this.amplitudeData[0].size() - 1){
                video.addImage(image, (long) (endTime * 1000000));
            }
        }
        video.finsh();
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

    public void setOverlap(int overlap) {
        this.overlap = overlap;
    }

    public AudioInputStream getAudioInputStream() {
        return this.audioInputStream;
    }

    public AudioFormat getAudioFormat() {
        return this.format;
    }
}
