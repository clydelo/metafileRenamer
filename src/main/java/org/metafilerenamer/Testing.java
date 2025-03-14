package org.metafilerenamer;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.regex.Pattern;

import static org.metafilerenamer.Util.EXIF_taken_dtf;
import static org.metafilerenamer.Util.imageContainingEXIF;


public class Testing {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private static final Pattern pattern = Pattern.compile(".*", Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) throws IOException, ImageProcessingException {
        File file = new File("C:/Users/clyde/DATA/temp\\aaa.mov");

        int processed = 0;

        processed = renameFile(file, file.getParentFile(), processed);
        System.out.println("## processed " + processed + " files.");
    }

    private static int renameFile(File file, File directory, int processed) throws IOException, ImageProcessingException {
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

   /*     if (file.renameTo(newFile)) {
            System.out.println("Renamed: " + file.getAbsolutePath() + " -> " + newFile.getAbsolutePath());
            processed++;
        } else {
            System.err.println("Failed to rename: " + file.getName());
        }*/
        return processed;
    }


    private static long getMillis(File file) throws IOException, ImageProcessingException {
        BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        long fileModifiedMillis = attrs.lastModifiedTime().toMillis();
        long fileCreationMillis = attrs.creationTime().toMillis();
        long takenMillis = Long.MAX_VALUE;
        if (Util.containsExif(file)) {
            takenMillis = readPicExif(file);
        }else if (Util.isMov(file)) {
            takenMillis = readMOV(file);
        }
        return Math.min(Math.min(fileModifiedMillis, fileCreationMillis), takenMillis);
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
        if(directory==null){
            return Long.MAX_VALUE;
        }
        Date created = directory.getDate(QuickTimeMetadataDirectory.TAG_CREATION_DATE);

        return created != null ? created.toInstant().toEpochMilli() : Long.MAX_VALUE;
    }

    private static String getTagValue(final JpegImageMetadata jpegMetadata, final TagInfo tagInfo) {
        final TiffField field = jpegMetadata.findExifValueWithExactMatch(tagInfo);
        if (field != null) {
            return field.getValueDescription();
        }
        return null;
    }

    /**
     * Write all extracted values to stdout.
     */
    private static void print(Metadata metadata)
    {
        System.out.println();
        System.out.println("-------------------------------------------------");


        //
        // A Metadata object contains multiple Directory objects
        //
        for (Directory directory : metadata.getDirectories()) {

            //
            // Each Directory stores values in Tag objects
            //
            for (Tag tag : directory.getTags()) {
                System.out.println(tag);
            }

            //
            // Each Directory may also contain error messages
            //
            for (String error : directory.getErrors()) {
                System.err.println("ERROR: " + error);
            }
        }
    }

}
