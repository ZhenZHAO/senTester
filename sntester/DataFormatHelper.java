package com.appzhen.sntester;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by Zhen on 16-07-12.
 */
//import org.apache.http.util.ByteArrayBufferNew; // doesnt support after sdk23

public class DataFormatHelper {
    public static int PRINTABLE_ASCII_MIN = 0x20; // ' '
    public static int PRINTABLE_ASCII_MAX = 0x7E; // '~'

    public static boolean isPrintableAscii(int c) {
        return c >= PRINTABLE_ASCII_MIN && c <= PRINTABLE_ASCII_MAX;
    }

    public static String bytesToHex(byte[] data) {
        return bytesToHex(data, 0, data.length);
    }

    public static String bytesToHex(byte[] data, int offset, int length) {
        if (length <= 0) {
            return "";
        }

        StringBuilder hex = new StringBuilder();
        for (int i = offset; i < offset + length; i++) {
            hex.append(String.format(" %02X", data[i] % 0xFF));
        }
        hex.deleteCharAt(0);
        return hex.toString();
    }

    public static String bytesToAsciiMaybe(byte[] data) {
        return bytesToAsciiMaybe(data, 0, data.length);
    }

    public static String bytesToAsciiMaybe(byte[] data, int offset, int length) {
        StringBuilder ascii = new StringBuilder();
        boolean zeros = false;
        for (int i = offset; i < offset + length; i++) {
            int c = data[i] & 0xFF;
            if (isPrintableAscii(c)) {
                if (zeros) {
                    return null;
                }
                ascii.append((char) c);
            } else if (c == 0) {
                zeros = true;
            } else {
                return null;
            }
        }
        return ascii.toString();
    }

    public static float bytesToSingleFloat(byte[] data){
        float f = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        return (float)(Math.round(f*100))/100;
        // mathematically, we can say "round to the nearest hundredth" or "round to 2 decimal places"
    }

    public static byte[] hexToBytes(String hex) {
        ByteArrayBufferNew bytes = new ByteArrayBufferNew(hex.length() / 2);
//        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (int i = 0; i < hex.length(); i++) {
            if (hex.charAt(i) == ' ') {
                continue;
            }

            String hexByte;
            if (i + 1 < hex.length()) {
                hexByte = hex.substring(i, i + 2).trim();
                i++;
            } else {
                hexByte = hex.substring(i, i + 1);
            }

            bytes.append(Integer.parseInt(hexByte, 16));
//            buffer.write(Integer.parseInt(hexByte, 16),0,i);
        }
        return bytes.buffer();
//        return buffer.toByteArray();
    }
}
