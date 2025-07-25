package com.github.robinZhao.sound;

import java.awt.image.BufferedImage;
import java.io.File;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

public class VideoCreator {
    private FFmpegFrameRecorder recorder;
    private Java2DFrameConverter converter = new Java2DFrameConverter();
    private FFmpegFrameGrabber audioGrabber;
    private long preTimeStamp = 0;

    public VideoCreator(String output, int width, int height, double rate, File audioFile) {
        this.audioGrabber = new FFmpegFrameGrabber(audioFile);
      
        this.recorder = new FFmpegFrameRecorder(
                output, width, height);
      
        System.out.println(recorder.getPixelFormat());
        try {
            //audioGrabber.setOption("acodec", "aac");
            audioGrabber.start();
            recorder.setFormat("mp4");
            recorder.setFrameRate(rate);
            // 关键质量参数
            // recorder.setVideoBitrate(100 * 1000); // 10 Mbps高码率（根据分辨率调整）
            recorder.setVideoQuality(18); // 最高质量（0-51，0为无损）
            recorder.setVideoOption("preset", "slow"); // 高质量编码预设
            recorder.setVideoOption("tune", "film"); // 适合清晰图像
            recorder.setVideoOption("crf", "0"); // 高质量CRF值（0-51，值越低质量越高）
            // GOP设置（关键帧间隔）
            //recorder.setGopSize((int) (1 * rate)); // 每3秒一个关键帧
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            recorder.setAudioChannels(audioGrabber.getAudioChannels());
            recorder.setSampleRate(audioGrabber.getSampleRate());

            recorder.setVideoOption("bEnableFrameSkip:v", "1");
            recorder.start();
            Frame frame = audioGrabber.grabSamples();
            while (null != frame) {
                recorder.record(frame);
                frame = audioGrabber.grabSamples();
            }
        } catch (Exception e) {
            throw new RuntimeException("创建视频失败", e);
        }
    }

    public VideoCreator(String output, int width, int height, double rate) {
        this.recorder = new FFmpegFrameRecorder(
                output, width, height);
        recorder.setFormat("mp4");
        recorder.setFrameRate(rate);
        // 关键质量参数
        // recorder.setVideoBitrate(100 * 1000); // 10 Mbps高码率（根据分辨率调整）
        recorder.setVideoQuality(18); // 最高质量（0-51，0为无损）
        // recorder.setVideoOption("preset", "slow"); // 高质量编码预设
        // recorder.setVideoOption("tune", "film"); // 适合清晰图像
        // recorder.setVideoOption("crf", "0"); // 高质量CRF值（0-51，值越低质量越高）

        // GOP设置（关键帧间隔）
        recorder.setGopSize((int) (1 * rate)); // 每3秒一个关键帧

        // 其他优化设置
        // recorder.setVideoOption("profile:v", "high"); // 高质量编码配置
        // recorder.setVideoOption("level", "4.2"); // H.264级别
        // recorder.setVideoOption("movflags", "+faststart"); // 流媒体优化

        // recorder.setVideoQuality(40);
        // System.out.println(recorder.getPixelFormat());
        recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        try {
            recorder.start();
        } catch (Exception e) {
            throw new RuntimeException("创建视频失败", e);
        }
    }

    public void addImage(BufferedImage image, long timeStamp) {
        try {
            Frame frame = converter.convert(image);
            recorder.setTimestamp(preTimeStamp);
            try{
                recorder.record(frame);
            }catch(Exception e){
                System.out.print(e.getStackTrace());
            }     
            this.preTimeStamp = timeStamp;
            BufferedImage img = converter.getBufferedImage(frame);
            // if(true) throw new RuntimeException("aa");
        } catch (Exception e) {
            throw new RuntimeException("添加frame失败", e);
        }
    }

    public void finsh() {
        try {
            recorder.stop();
            recorder.release();
        } catch (Exception e) {
            throw new RuntimeException("完成视频失败", e);
        }
    }

    public static void main(String[] args) throws org.bytedeco.javacv.FrameGrabber.Exception, Exception {
        FFmpegFrameGrabber audioGrabber = new FFmpegFrameGrabber("test.wav");
        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder("test.mp4", 500, 300, audioGrabber.getAudioChannels());
        recorder.setFrameRate(25.0);
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);  // 必须设置音频编码器
        //recorder.setAudioChannels(2);             // 明确声道数
              // 明确采样率
       // recorder.setAudioBitrate(128000);         // 设置比特率
       // recorder.setAudioQuality(0);              // 最高质量
        recorder.setFormat("mp4");  
        audioGrabber.start();
        recorder.setSampleRate(audioGrabber.getSampleRate()); 
        recorder.start();
        Frame frame = audioGrabber.grabSamples();
        while (null != frame) {
            //System.out.println(audioGrabber.getTimestamp());
            frame = audioGrabber.grabSamples();
            recorder.record(frame);
        }

    }

}