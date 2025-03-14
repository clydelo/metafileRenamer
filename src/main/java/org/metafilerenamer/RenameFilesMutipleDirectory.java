package org.metafilerenamer;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Rename all valid files(based on extension) using minimum of creation,modified date and/or EXIF metadata taken date(for jpg,png)
 * Will iterate through subdirectories as well.
 * Deletes .aae files
 */
public class RenameFilesMutipleDirectory {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter EXIF_taken_dtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
    private static final FilenameFilter filenameFilter = (dir, name) ->
            dir.isDirectory()
            || Util.EXTENSIONS.contains(name);

    private static final Set<String> toDelete = new HashSet<>();
    static {
        toDelete.add(".aae");
    }

    private static final Set<String> imageContainingEXIF = new HashSet<>();
    static {
        imageContainingEXIF.add(".jpg");
        imageContainingEXIF.add(".png");
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java RenameFiles <directory>");
            return;
        }

        File directory = new File(args[0]);
        if (!directory.isDirectory()) {
            System.out.println("The provided path is not a directory.");
            return;
        }

        File[] files = directory.listFiles(filenameFilter);

        if (files == null || files.length == 0) {
            System.out.println("No .jpg, .png or .mov files found in the directory.");
            return;
        }
        int processed = 0;

        processed = processFiles(files, directory, processed);
        System.out.println("## processed " + processed + " files.");
    }

    private static int processFiles(File[] files, File directory, int processed) {
        if (files == null) {
            return processed;
        }
        for (File file : files) {
            try {
                if (file.isDirectory()) {
                    File[] subFiles = file.listFiles(filenameFilter);
                    processed = processFiles(subFiles, file, processed);
                } else if (toDelete.contains(Util.getFileExtension(file).toLowerCase())) {
                    String name = file.getAbsolutePath();
                    System.out.println("Deleting " + name + " - " + file.delete());
                } else {
                    processed = renameFile(file, directory, processed);
                }
            } catch (IOException e) {
                System.err.println("Error reading file attributes: " + file.getName());
                e.printStackTrace();
            }
        }
        return processed;
    }

    private static int renameFile(File file, File directory, int processed) throws IOException {
        long millis = getMillis(file);
        Date modifiedDate = new Date(millis);

        String extension = Util.getFileExtension(file);
        String newName = dateFormat.format(modifiedDate) + extension;

        File newFile = new File(directory, newName);
        int counter = 1;
        while (newFile.exists()) {
            newName = dateFormat.format(modifiedDate) + "_" + counter + extension;
            newFile = new File(directory, newName);
            counter++;
        }

        if (file.renameTo(newFile)) {
            System.out.println("Renamed: " + file.getAbsolutePath() + " -> " + newFile.getAbsolutePath());
            processed++;
        } else {
            System.err.println("Failed to rename: " + file.getName());
        }
        return processed;
    }

    private static long getMillis(File file) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        long fileModifiedMillis = attrs.lastModifiedTime().toMillis();
        long fileCreationMillis = attrs.creationTime().toMillis();
        long takenMillis = Long.MAX_VALUE;
        if (imageContainingEXIF.contains(Util.getFileExtension(file).toLowerCase())) {
            try {
                final ImageMetadata metadata = Imaging.getMetadata(file);
                if (metadata instanceof JpegImageMetadata) {
                    final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;

                    String takenDateTime = getTagValue(jpegMetadata, ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
                    if (takenDateTime != null) {
                        takenMillis = Instant.from(LocalDateTime.parse(takenDateTime.replace("'", ""), EXIF_taken_dtf).atZone(ZoneOffset.UTC)).toEpochMilli();
                    }
                }
            } catch (ImagingException e) {
                //Do nothing
            }
        }
        return Math.min(Math.min(fileModifiedMillis, fileCreationMillis), takenMillis);
    }

    private static String getTagValue(final JpegImageMetadata jpegMetadata, final TagInfo tagInfo) {
        final TiffField field = jpegMetadata.findExifValueWithExactMatch(tagInfo);
        if (field != null) {
            return field.getValueDescription();
        }
        return null;
    }
}

