package com.seektop.common.aws.thunbnial.gif;

import lombok.Builder;
import lombok.Getter;

import java.awt.image.BufferedImage;

@Builder
@Getter
public class GifFrame {
    /**
     * 帧图片
     */
    private BufferedImage bufferedImage;
    /**
     * 对应的延时
     */
    private Integer delay;
}
