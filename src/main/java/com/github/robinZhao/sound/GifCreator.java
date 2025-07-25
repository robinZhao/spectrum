package com.github.robinZhao.sound;

import java.awt.image.BufferedImage;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegFrameRecorder.Exception;
import org.bytedeco.javacv.Java2DFrameConverter;

import cn.hutool.core.img.gif.AnimatedGifEncoder;

public class GifCreator {
    private AnimatedGifEncoder encoder;
    private long preTimeStamp=0;

    public GifCreator(String output, int width, int height, double rate) {
        encoder = new AnimatedGifEncoder();
        encoder.setRepeat(1); // 0表示无限循环
        encoder.start(output);
        //encoder.setDelay((int) (1 / rate * 1000d)); // 设置延迟时间
    }

    public void addImage(BufferedImage image,long timeStamp) {
        encoder.addFrame(image);
        encoder.setDelay((int) (timeStamp-preTimeStamp)/1000);
        preTimeStamp=timeStamp;
    }

    public void finsh() {
        encoder.finish();
    }

}