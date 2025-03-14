package org.metafilerenamer;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class Util {
    public static final List<String> EXTENSIONS = Arrays.asList(".jpg", ".mov", ".mp4", ".png", ".m4v",".heic",".hevc");

    public static String getFileExtension(File file) {
        String name = file.getName();
        int lastIndex = name.lastIndexOf('.');
        return (lastIndex == -1) ? "" : name.substring(lastIndex);
    }
}
