package org.metafilerenamer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.metafilerenamer.Util.EXTENSIONS;

/**
 * Copies all valid files with extension from input dir, iterating through subdirectories and copies all to output directory
 */
public class FileMover {
    private static final String INPUT_DIR = "c:/dev/in";
    private static final String OUTPUT_DIR = "c:/dev/out";
    private static int counter = 0;

    public static void main(String[] args) {
        File inputDir = new File(INPUT_DIR);
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            System.out.println("Invalid input directory path.");
            return;
        }

        moveFiles(inputDir);
    }

    private static void moveFiles(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    moveFiles(file);
                } else {
                    moveFileIfMatching(file);
                    counter++;
                }
            }
        }
        System.out.println("### moved " + counter + " files.");
    }

    private static void moveFileIfMatching(File file) {
        if (EXTENSIONS.contains(Util.getFileExtension(file).toLowerCase())) {
            moveFile(file, OUTPUT_DIR);
        }
    }

    private static void moveFile(File file, String outputDirPath) {
        try {
            Path outputPath = Paths.get(outputDirPath, file.getName());
            Files.createDirectories(outputPath.getParent());
            Files.move(file.toPath(), outputPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Moved: " + file.getAbsolutePath() + " to " + outputPath.toString());
        } catch (IOException e) {
            System.err.println("Failed to move file: " + file.getAbsolutePath());
            e.printStackTrace();
        }
    }
}

