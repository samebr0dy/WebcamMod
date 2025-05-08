package com.lichcode.webcam.video;

import com.lichcode.webcam.WebcamMod;
import com.lichcode.webcam.Video.PlayerVideo;
import com.lichcode.webcam.render.image.RenderableImage;
import com.github.sarxos.webcam.*;
import com.github.sarxos.webcam.ds.buildin.WebcamDefaultDevice;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.InvalidMarkException;
import java.util.Iterator;


public class VideoCamara {
    static Webcam webcam;

    public static void init() {
        for(Webcam wc : Webcam.getWebcams()) {
            try {
                wc.open();
                webcam = wc;
                WebcamMod.LOGGER.info("Using webcam: {}", wc.getName());
                return;
            } catch (WebcamException e) {
                WebcamMod.LOGGER.info("Webcam {} not usable, trying next one.", wc.getName());
            }
        }

        throw new WebcamLockException("All webcams in use!");
    }

    public static void release() {
        webcam.close();
    }

    public static void get(PlayerVideo playerVideo) throws IOException {
        BufferedImage image = webcam.getImage();
        // Resize image to defined size
        // TODO: maybe add x/y offset so user can define size
        image = resize(image, playerVideo.width, playerVideo.height);

        // Compress image using JPEG
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = writers.next();
        JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
        jpegParams.setCompressionMode(JPEGImageWriteParam.MODE_EXPLICIT);
        jpegParams.setCompressionQuality(1);
        writer.setOutput(ios);
        IIOImage outputImage = new IIOImage(image, null, null);

        writer.write(null, outputImage, jpegParams);
        writer.dispose();
        ios.close();
        // Save compressed image to object
        playerVideo.setFrame(baos.toByteArray());
    }

    public static BufferedImage resize(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();

        return resized;
    }
}
