package org.metafilerenamer;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Util {
    public static final List<String> EXTENSIONS = Arrays.asList(".jpg", ".mov", ".mp4", ".png", ".m4v",".heic",".hevc");

    public static final List<String> imageContainingEXIF = Arrays.asList(".jpg",".png");

    public static final DateTimeFormatter EXIF_taken_dtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    public static String getFileExtension(File file) {
        String name = file.getName();
        int lastIndex = name.lastIndexOf('.');
        return (lastIndex == -1) ? "" : name.substring(lastIndex);
    }

    public static boolean isMov(File file) {
        return getFileExtension(file).toLowerCase().equals(".mov");
    }

    public static boolean containsExif(File file) {
        return imageContainingEXIF.contains(getFileExtension(file).toLowerCase());
    }

    public static boolean isHeic(File file) {
        return getFileExtension(file).toLowerCase().equals(".heic");
    }
}
