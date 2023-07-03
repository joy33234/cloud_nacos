package com.seektop.common.utils;

import java.io.*;
import java.util.zip.GZIPInputStream;

public class GZipUtils {

    public static byte[] decode(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        decompress(bais, baos);
        baos.flush();
        baos.close();
        bais.close();
        return baos.toByteArray();
    }

    public static void decompress(InputStream is, OutputStream os) throws IOException {
        GZIPInputStream gis = new GZIPInputStream(is);
        int count;
        byte[] data = new byte[1024];
        while ((count = gis.read(data, 0, 1024)) != -1) {
            os.write(data, 0, count);
        }
        gis.close();
    }

}
