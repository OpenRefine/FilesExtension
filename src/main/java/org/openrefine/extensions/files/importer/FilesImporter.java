package org.openrefine.extensions.files.importer;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.refine.ProjectMetadata;
import com.google.refine.importers.ImportingParserBase;
import com.google.refine.importers.SeparatorBasedImporter;
import com.google.refine.importing.ImportingJob;
import com.google.refine.model.Project;
import com.google.refine.util.JSONUtilities;
import com.google.refine.util.ParsingUtilities;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

public class FilesImporter {
    private static final Logger logger = LoggerFactory.getLogger("FilesImporter");
    private static final int fileContentSizeLimit = 1024;

    public static String[] restrictedDirectories = {
            "System32",
            "Startup",
            "Programs",
            "Program Files",
            "Program Files (x86)",
            "Windows",
            "usr",
            "etc",
            "var",
            "proc",
            "sys",
            "dev",
            "boot",
            "bin",
            "sbin",
            "lib",
            "opt",
            "tmp",
            "Volumes",
            "System",
            "Applications",
            "Library"
    };


    public static long generateFileList(File file, ObjectNode options) throws IOException {
        JsonNode directoryInput = options.get("directoryJsonValue");
        try {
            FileWriter writer = new FileWriter(file);
            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT);
            for (JsonNode directoryPath : directoryInput) {
                getFileList(directoryPath.get("directory").asText(), csvPrinter);
            }
            csvPrinter.flush();
            writer.close();
            return file.length();
        } catch (IOException e) {
            throw new IOException("Failed to generate file list");
        }
    }

    public static void loadData(Project project, ProjectMetadata metadata, ImportingJob job, ArrayNode fileRecords) throws Exception {
        ObjectNode options = ParsingUtilities.mapper.createObjectNode();
        JSONUtilities.safePut(options, "includeArchiveFileName", true);
        JSONUtilities.safePut(options, "includeFileSources", false);
        ArrayNode columns = ParsingUtilities.mapper.createArrayNode();
        columns.add("fileName");
        columns.add("fileSize(KB)");
        columns.add("fileExtension");
        columns.add("lastModifiedTime");
        columns.add("creationTime");
        columns.add("author");
        columns.add("filePath");
        columns.add("filePermissions");
        columns.add("sha256");
        JSONUtilities.safePut(options, "columnNames", columns);
        JSONUtilities.safePut(options, "separator", ",");


        ImportingParserBase parser = new SeparatorBasedImporter();
        List<Exception> exceptions = new ArrayList<Exception>();

        parser.parse(
                project,
                metadata,
                job,
                JSONUtilities.getObjectList(fileRecords),
                "csv",
                -1,
                options,
                exceptions);

        if(exceptions.size() > 0) {
            throw new Exception("Failed to process file list");
        }
        project.update();
    }

    private static void getFileList(String directoryPath, CSVPrinter csvPrinter) throws IOException {
        int depth = 1;
        try {
                Path rootPath = Paths.get(directoryPath);
                Files.walkFileTree(rootPath, EnumSet.noneOf(FileVisitOption.class), depth, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String fileRecord = "";
                    try {
                        if (!attrs.isDirectory()) {
                            String fileName = file.getFileName().toString();
                            String filePath = file.toAbsolutePath().toString();
                            String author = "";
                            try {
                                author = Files.getOwner(file).getName(); // File owner (may not always be available)
                            } catch (Exception e) {
                                // ignore
                            }
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                            String dateCreated = sdf.format(attrs.creationTime().toMillis());
                            String dateModified = sdf.format(attrs.lastModifiedTime().toMillis());
                            long fileSize = (long) Math.ceil(attrs.size() / 1024.0);
                            String fileExt = getFileExt(fileName);
                            String filePermissions = getFilePermissions(file);
                            String fileChecksum = calculateFileChecksum(file, "SHA-256");

                            csvPrinter.printRecord(fileName, fileSize, fileExt, dateModified, dateCreated, author, filePath, filePermissions, fileChecksum);
                        }
                    } catch (Exception e) {
                        logger.info("--- importDirectory. Error processing file: " + file + " - " + e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            logger.info("--- importDirectory. Error reading directory: " + e.getMessage());
        }
    }

    private static String getFileExt(String fileName) {
        String fileExt = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            fileExt = fileName.substring(dotIndex + 1);
        }
        return fileExt;
    }

    private static String getFilePermissions(Path path) {
        String filePermissions = "";
        try {
            if (Files.exists(path)) {
                FileStore store = Files.getFileStore(path);
                if (!store.supportsFileAttributeView(PosixFileAttributeView.class)) {
                    logger.info("--- importDirectory. POSIX file attributes are not supported on this system.");
                }
                else {
                    Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
                    filePermissions = PosixFilePermissions.toString(permissions);
                }
            }
        } catch (Exception e) {
            logger.info("--- importDirectory. Failed to retrieve file permissions: " + e.getMessage());
        }
        return filePermissions;
    }

    private static String calculateFileChecksum(Path path, String algorithm) throws Exception {
        if (Files.exists(path)) {
            try (var fileChannel = FileChannel.open(path, StandardOpenOption.READ);
                 var lock = fileChannel.lock(0, Long.MAX_VALUE, true)) {
                    MessageDigest digest = MessageDigest.getInstance(algorithm);
                    try (var inputStream = Files.newInputStream(path)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            digest.update(buffer, 0, bytesRead);
                        }
                    }
                    return bytesToHex(digest.digest());
            }
        }
        return "";
    }

    private static String bytesToHex(byte[] bytes) {
        try (Formatter formatter = new Formatter()) {
            for (byte b : bytes) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        }
    }

    private static String getFileContent(Path path) {
        if (Files.exists(path) && Files.isReadable(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                StringBuilder contentBuilder = new StringBuilder();
                char[] buffer = new char[1024];
                int bytesRead;
                int totalBytesRead = 0;
                int maxBytes = fileContentSizeLimit;

                while ((bytesRead = reader.read(buffer, 0, Math.min(buffer.length, maxBytes - totalBytesRead))) != -1) {
                    contentBuilder.append(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (totalBytesRead >= maxBytes) {
                        break;
                    }
                }

                String content = contentBuilder.toString();
                if (canIncludeFileContent(content)) {
                    return content;
                }
            } catch (IOException e) {
                logger.info("--- importDirectory. Failed to read file content: " + e.getMessage());
            }
        }
        return "";
    }

    private static boolean canIncludeFileContent(String content) {
            int lengthToCheck = Math.min(content.length(), fileContentSizeLimit);
            int nonPrintableCount = 0;
            for (int i = 0; i < lengthToCheck; i++) {
               if ( !Character.isDefined(content.charAt(i)) ||
                       (!(content.charAt(i) == '\r' || content.charAt(i) == '\n' || content.charAt(i) == '\t') &&
                        Character.isISOControl(content.charAt(i))) )
                {
                    nonPrintableCount++;
                }
            }
            return (nonPrintableCount / (double) lengthToCheck) <= 0.05;
    }

    public static List<String> getRootDirectories() {
        Iterable<Path> rootDirectories = FileSystems.getDefault().getRootDirectories();
        List<String> rootFS = new ArrayList<>();
        for (Path root : rootDirectories) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                for (Path child : stream) {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(child, BasicFileAttributes.class);
                        if ( attrs.isDirectory() && ! Arrays.stream(restrictedDirectories).anyMatch(restrictedDirName -> child.getFileName().toString().equalsIgnoreCase(restrictedDirName))) {
                            rootFS.add(child.toString());
                        }
                    } catch (Exception e) {
                        // do nothing
                    }
                }
            } catch (Exception e) {
                // do nothing
                rootFS.add(root.toString());
            }
        }
        rootFS.sort((dir1, dir2) -> dir1.compareToIgnoreCase(dir2));
        return rootFS;
    }

    public static void generateDirectoryTree(String directoryPath, Path outputFile) throws IOException {
        Path dir = Paths.get(directoryPath);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("The provided path must be a directory.");
        }

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writer().without(SerializationFeature.INDENT_OUTPUT);
        try (JsonGenerator jsonGenerator = objectMapper.getFactory().createGenerator(outputFile.toFile(), JsonEncoding.UTF8)) {
            jsonGenerator.writeStartObject();
            buildDirectoryNode(dir, jsonGenerator);
            jsonGenerator.writeEndObject();
        } catch (Exception e) {
            logger.info("--- directoryHierarchy - Failed to write directory structure to file: " + e.getMessage());
        }
    }

    private static void buildDirectoryNode(Path dir, JsonGenerator jsonGenerator) throws IOException {
        try {
            String dirName = "unknown";
            String dirPath = "unknown";
            try {
                dirName = dir.getFileName().toString();
                dirPath = dir.toAbsolutePath().toString();
            } catch (Exception e) {}

            jsonGenerator.writeFieldName("name");
            jsonGenerator.writeString(dirName);

            jsonGenerator.writeFieldName("path");
            jsonGenerator.writeString(dirPath);

            jsonGenerator.writeFieldName("children");
            jsonGenerator.writeStartArray();

            List<Path> children = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path child : stream) {
                    if (Files.isDirectory(child)) {
                        children.add(child);
                    }
                }
            } catch (Exception e) {
                // do nothing - Security exception
            }

            children.sort((p1, p2) -> p1.getFileName().toString().compareToIgnoreCase(p2.getFileName().toString()));
            for (Path child : children) {
                jsonGenerator.writeStartObject();
                buildDirectoryNode(child, jsonGenerator);
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();
        } catch (Exception e) {
            logger.info("--- directoryHierarchy - Failed to process directory: " + e.getMessage());
        }
    }

    public static String generateProjectName(ArrayNode directoryInput)  {
        if ( directoryInput == null || directoryInput.isEmpty() ) {
            return "folder-details";
        }
        String folder1 = Paths.get(directoryInput.get(0).get("directory").asText()).getFileName().toString();
        String folder2 = directoryInput.size() > 1 ? Paths.get(directoryInput.get(1).get("directory").asText()).getFileName().toString() : null;

        if (folder2 == null) {
            return String.format("folder-details_%s", folder1);
        } else if (directoryInput.size() > 2) {
            return String.format("folder-details_%s_%s_and_more", folder1, folder2);
        } else {
            return String.format("folder-details_%s_%s", folder1, folder2);
        }
    }

}
