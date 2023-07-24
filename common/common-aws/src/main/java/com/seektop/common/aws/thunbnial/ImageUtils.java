package com.seektop.common.aws.thunbnial;

import com.seektop.common.aws.thunbnial.gif.GifDecoder;
import com.seektop.common.aws.thunbnial.gif.GifFrame;
import net.coobird.thumbnailator.Thumbnails;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ImageUtils {
    public static void generateThumbnail(InputStream source, Float scale, OutputStream dest) throws IOException {
        Thumbnails.of(source)
                .scale(scale)
                .outputFormat("png")
                .toOutputStream(dest);
    }

    /**
     * 读取gif图片并对每一帧进行缩放
     * @param source
     * @throws IOException
     */
    public static List<BufferedImage> readGif(InputStream source) throws IOException {
        GifDecoder.GifImage gifImage = GifDecoder.read(source);
        List<BufferedImage> bufferedImages = new ArrayList<>();
        // 获取gif帧数
        int frameCount = gifImage.getFrameCount();
        for (int i = 0; i < frameCount; i++) {
            BufferedImage frame = gifImage.getFrame(i);
            bufferedImages.add(frame);
            //获取每一帧的延迟
        }
        return bufferedImages;
    }
    public static List<GifFrame> readGifFrames(InputStream source,Float scale) throws IOException {
        GifDecoder.GifImage gifImage = GifDecoder.read(source);
        List<GifFrame> frames = new ArrayList<>();
        // 获取gif帧数
        int frameCount = gifImage.getFrameCount();
        for (int i = 0; i < frameCount; i++) {
            BufferedImage frame = gifImage.getFrame(i);
            int delay = gifImage.getDelay(i);
            BufferedImage scaleImage = Thumbnails.of(frame)
                    .scale(scale)
                    .outputQuality(0.5)
                    .asBufferedImage();
            frames.add(GifFrame.builder().bufferedImage(scaleImage).delay(delay).build());
        }
        return frames;
    }

}
