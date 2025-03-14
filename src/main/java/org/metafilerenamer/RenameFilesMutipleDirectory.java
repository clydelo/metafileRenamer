package org.metafilerenamer;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory;
import org.apache.commons.imaging.Imaging;
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
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.metafilerenamer.Util.EXIF_taken_dtf;

/**
 * Rename all valid files(based on extension) using minimum of creation,modified date and/or EXIF metadata taken date(for jpg,png)
 * Will iterate through subdirectories as well.
 * Deletes .aae files
 */
public class RenameFilesMutipleDirectory {
    private static final SimpleDateFormat renamedFileDateTimeFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

    private static final FilenameFilter filenameFilter = (dir, name) ->
            dir.isDirectory()
                    || Util.EXTENSIONS.contains(name);

    private static final Set<String> toDelete = new HashSet<>();

    static {
        toDelete.add(".aae");
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
                    System.out.println("Deleting " + file.getAbsolutePath() + " - " + file.delete());
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
        String newName = renamedFileDateTimeFormat.format(modifiedDate) + extension;

        File newFile = new File(directory, newName);
        int counter = 1;
        while (newFile.exists()) {
            newName = renamedFileDateTimeFormat.format(modifiedDate) + "_" + counter + extension;
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

    private static long getMillis(File file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            long fileModifiedMillis = attrs.lastModifiedTime().toMillis();
            long fileCreationMillis = attrs.creationTime().toMillis();
            long takenMillis = Long.MAX_VALUE;

            if (Util.containsExif(file)) {
                takenMillis = readJpegImageMeta(file);
            } else if (Util.isHeic(file)) {
                takenMillis = readPicExif(file);
            } else if (Util.isMov(file)) {
                takenMillis = readMOV(file);
            }
            return Math.min(Math.min(fileModifiedMillis, fileCreationMillis), takenMillis);

        } catch (IOException | ImageProcessingException e) {
            System.err.println("failed getting timestamp for " + file.getAbsolutePath() + " -> " + e.getMessage());
        }
        return Long.MAX_VALUE; //If we are here, an error occurred.
    }

    private static long readPicExif(File file) throws ImageProcessingException, IOException {
        Metadata metadata = ImageMetadataReader.readMetadata(file.getAbsoluteFile());

        ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        Date dateTaken = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
        return dateTaken != null ? dateTaken.toInstant().toEpochMilli() : Long.MAX_VALUE;
    }

    private static long readMOV(File file) throws ImageProcessingException, IOException {
        Metadata metadata = ImageMetadataReader.readMetadata(file.getAbsoluteFile());

        QuickTimeMetadataDirectory directory = metadata.getFirstDirectoryOfType(QuickTimeMetadataDirectory.class);
        if (directory == null) {
            return Long.MAX_VALUE;
        }
        Date created = directory.getDate(QuickTimeMetadataDirectory.TAG_CREATION_DATE);

        return created != null ? created.toInstant().toEpochMilli() : Long.MAX_VALUE;
    }

    private static long readJpegImageMeta(File file) {
        try {
            final ImageMetadata metadata = Imaging.getMetadata(file);
            if (metadata instanceof JpegImageMetadata) {
                final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
                String takenDateTime = getTagValue(jpegMetadata, ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
                return takenDateTime != null ? Instant.from(LocalDateTime.parse(takenDateTime.replace("'", ""), EXIF_taken_dtf).atZone(ZoneOffset.UTC)).toEpochMilli() :
                        Long.MAX_VALUE;
            }
        } catch (IOException e) {
            System.err.println("failed readJpegImageMeta for file " + file.getAbsolutePath() + e.getMessage());
        }
        return Long.MAX_VALUE;
    }

    /*
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
*/
    private static String getTagValue(final JpegImageMetadata jpegMetadata, final TagInfo tagInfo) {
        final TiffField field = jpegMetadata.findExifValueWithExactMatch(tagInfo);
        if (field != null) {
            return field.getValueDescription();
        }
        return null;
    }
}

