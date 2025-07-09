音频数据读取并生成频谱图
生成的样式参考[wavesurfer.js](https://wavesurfer.xyz/examples/?spectrogram.js)
wavesurfer.js没有默认使用音频文件的采样率，手动修改采样率和音频文件的采样率相同后，和本程序生成的图像相同

示例
```java
public static void main(String[] args){
    Spectrum spc = new Spectrum(new File("test.wav"), bufferSize,
            //梅尔坐标系处理
            ScaleFilter.Type.mel,
            //应用汉宁窗，并使用SunFFTTransformer做傅立叶变换，
            new SunFFTTransformer(bufferSize, "hann"));
    spc.run();
    //获取频谱数据，下标为坐标系后的频率坐标，可以转换为频率
    //hz = spc.indexToHz(hz,length);
    //index = spc.hzToIndex(idx,length);
    spc.getFrequenceData();
    //绘制频谱图
    //采样率除以2为最高频率
    spc.drawSpctrogram(0, spc.getAudioFormat().getSampleRate()/2, 1024, 800, -1);
}
```