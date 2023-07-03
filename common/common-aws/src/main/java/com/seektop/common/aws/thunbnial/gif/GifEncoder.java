package com.seektop.common.aws.thunbnial.gif;

import com.sun.imageio.plugins.gif.GIFImageWriter;
import com.sun.imageio.plugins.gif.GIFStreamMetadata;

import javax.imageio.*;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.List;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;

public class GifEncoder {
    private static void configureRootMetadata(int delay, boolean loop, IIOMetadata metadata) throws IIOInvalidTreeException {
        String metaFormatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);
        IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
        graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
        graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("delayTime", Integer.toString(delay));
        graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");
        IIOMetadataNode appExtensionsNode = getNode(root, "ApplicationExtensions");
        IIOMetadataNode child = new IIOMetadataNode("ApplicationExtension");
        child.setAttribute("applicationID", "NETSCAPE");
        child.setAttribute("authenticationCode", "2.0");
        int loopContinuously = loop ? 0 : 1;
        child.setUserObject(new byte[]{0x1, (byte) (loopContinuously & 0xFF), (byte) ((loopContinuously >> 8) & 0xFF)});
        appExtensionsNode.appendChild(child);
        metadata.setFromTree(metaFormatName, root);
    }
    private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
        int nNodes = rootNode.getLength();
        for (int i = 0; i < nNodes; i++) {
            if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                return (IIOMetadataNode) rootNode.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        rootNode.appendChild(node);
        return (node);
    }
    public static byte[]  writerImage(List<GifFrame> images, String formatName) throws Exception{
        Iterator<ImageWriter> imageWriters = ImageIO.getImageWritersByFormatName(formatName);
        if(imageWriters.hasNext()){
            GIFImageWriter writer = (GIFImageWriter) imageWriters.next();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageOutputStream ios = ImageIO.createImageOutputStream(byteArrayOutputStream);
            writer.setOutput(ios);
            ImageWriteParam params = writer.getDefaultWriteParam();
            IIOMetadata gifImageMetadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromBufferedImageType(TYPE_INT_RGB), params);
            GIFStreamMetadata gifStreamMetadata = new GIFStreamMetadata();
            if(writer.canWriteSequence()){
                writer.prepareWriteSequence(gifStreamMetadata);
                for (int i = 0; i < images.size(); i++) {
                    GifFrame gifFrame = images.get(i);
                    BufferedImage childImage = gifFrame.getBufferedImage();
                    configureRootMetadata(gifFrame.getDelay(),true,gifImageMetadata);
                    IIOImage image = new IIOImage(childImage, null , gifImageMetadata);
                    ImageWriteParam defaultWriteParam = writer.getDefaultWriteParam();
                    defaultWriteParam.setProgressiveMode(1);
                    writer.writeToSequence(image, defaultWriteParam);
                }
                writer.endWriteSequence();
            }else{
                for (int i = 0; i < images.size(); i++) {
                    writer.write(images.get(i).getBufferedImage());
                }
            }
            writer.dispose();
            ios.close();
            return byteArrayOutputStream.toByteArray();
        }
        return null;
    }
}
