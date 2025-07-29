package de.fx.mediaAge;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifImageDirectory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.file.FileSystemDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.mp4.media.Mp4MediaDirectory;
import com.drew.metadata.png.PngDirectory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MediaAge {

    public static void main(String[] args) {
        new MediaAge().run(args);
    }

    private void run(String[] args) {
        if (args != null && args.length > 0 && args[0] != null) {
            String directory = args[0].trim();
            Path directoryPath = getRealDirectory(directory);
            if (Files.exists(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
                rumMediaAgeUpdate(directoryPath);
            } else {
                System.out.println("directory not found: " + directoryPath.toUri());
            }
        }
    }

    private Path getRealDirectory(String wantedDirectoryValue) {
        String wantedDirectory = wantedDirectoryValue.replace("\"", "").replace("'", "");
        String userHome = System.getProperty("user.home");
        if (wantedDirectory.startsWith("~/")) {
            wantedDirectory = wantedDirectory.replaceFirst("~/", userHome + "/");
        }

        return Path.of(wantedDirectory);
    }

    public void rumMediaAgeUpdate(Path directory) {
        Comparator<Path> comparator = (o1, o2) -> o1.toString().compareToIgnoreCase(o2.toString());

        try (Stream<Path> pathStream = Files.walk(directory, FileVisitOption.FOLLOW_LINKS)) {
            pathStream
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(comparator)
                    .forEach(path -> upateMediaAge(path));

        } catch (Exception ex) {
        }
    }

    private void upateMediaAge(Path path) {
        try {
            LocalDateTime mediaAge = findMediaAge(path);

            if (mediaAge != null) {
                LocalDateTime lastModification = getFileLastModification(path);
                if (lastModification.isAfter(mediaAge)) {

                    FileTime fileTime = FileTime.from(mediaAge.atZone(ZoneId.systemDefault()).toInstant());

                    Files.setLastModifiedTime(path, fileTime);
                    System.out.println(path + " -> " + fileTime.toString());
                }
            } else {
                System.out.println("no date for " + path);
            }

        } catch (IOException | ImageProcessingException ex) {
            ex.printStackTrace();
        }
    }

    private LocalDateTime findMediaAge(Path path) throws IOException, ImageProcessingException {
        if (isImage(path)) {
            return findImageExifDate(path);
        }
        if (isVideo(path)) {
            return findVideoAge(path);
        }
        return null;
    }

    private boolean isImage(Path path) throws IOException {
        String mimeType = Files.probeContentType(path);
        return mimeType != null && mimeType.startsWith("image/");
    }

    private boolean isVideo(Path path) throws IOException {
        String mimeType = Files.probeContentType(path);
        return mimeType != null && mimeType.startsWith("video/");
    }

    private boolean isMp4(Path path) throws IOException {
        String mimeType = Files.probeContentType(path);
        return mimeType != null && "video/mp4".equals(mimeType);
    }

    private LocalDateTime findImageExifDate(Path path) throws ImageProcessingException, IOException {
        List<Date> imageDates = new ArrayList<>();
        Metadata metadata = ImageMetadataReader.readMetadata(path.toFile());

        Collection< ExifDirectoryBase> exifDirectoryBases = metadata.getDirectoriesOfType(ExifDirectoryBase.class);
        if (exifDirectoryBases != null) {
            for (ExifDirectoryBase exifDirectoryBase : exifDirectoryBases) {
                Date dateTime = exifDirectoryBase.getDate(ExifDirectoryBase.TAG_DATETIME, TimeZone.getTimeZone(ZoneId.systemDefault()));
                if (dateTime != null) {
                    imageDates.add(dateTime);
                }
            }
        }

        Collection<PngDirectory> pngDirs = metadata.getDirectoriesOfType(PngDirectory.class);
        if (pngDirs != null) {
            for (PngDirectory pngDir : pngDirs) {
                Date dateTime = pngDir.getDate(PngDirectory.TAG_LAST_MODIFICATION_TIME, TimeZone.getTimeZone(ZoneId.systemDefault()));
                if (dateTime != null) {
                    imageDates.add(dateTime);
                }
            }
        }

        ExifImageDirectory exifImageDirectory = metadata.getFirstDirectoryOfType(ExifImageDirectory.class);
        if (exifImageDirectory != null) {
            Date dateTime = exifImageDirectory.getDate(ExifImageDirectory.TAG_DATETIME, TimeZone.getTimeZone(ZoneId.systemDefault()));
            if (dateTime != null) {
                imageDates.add(dateTime);
            }
            Date dateTimeOriginal = exifImageDirectory.getDate(ExifIFD0Directory.TAG_DATETIME_ORIGINAL, TimeZone.getTimeZone(ZoneId.systemDefault()));
            if (dateTimeOriginal != null) {
                imageDates.add(dateTimeOriginal);
            }
        }

        ExifIFD0Directory exifIFD0Directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (exifIFD0Directory != null) {
            Date dateTime = exifIFD0Directory.getDate(ExifIFD0Directory.TAG_DATETIME, TimeZone.getTimeZone(ZoneId.systemDefault()));
            if (dateTime != null) {
                imageDates.add(dateTime);
            }

            Date dateTimeOriginal = exifIFD0Directory.getDate(ExifIFD0Directory.TAG_DATETIME_ORIGINAL, TimeZone.getTimeZone(ZoneId.systemDefault()));
            if (dateTimeOriginal != null) {
                imageDates.add(dateTimeOriginal);
            }
        }

        ExifSubIFDDirectory exifSubIFDDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (exifSubIFDDirectory != null) {
            Date dateTimeOriginal = exifSubIFDDirectory.getDateOriginal(TimeZone.getTimeZone(ZoneId.systemDefault()));
            if (dateTimeOriginal != null) {
                imageDates.add(dateTimeOriginal);
            }
        }

        FileSystemDirectory fileSystemDirectory = metadata.getFirstDirectoryOfType(FileSystemDirectory.class);
        if (fileSystemDirectory != null) {
            Date modifiedDate = fileSystemDirectory.getDate(FileSystemDirectory.TAG_FILE_MODIFIED_DATE, TimeZone.getTimeZone(ZoneId.systemDefault()));
            if (modifiedDate != null) {
                imageDates.add(modifiedDate);
            }
        }

        return imageDates.stream()
                .min(Date::compareTo)
                .map(img -> truncate(LocalDateTime.ofInstant(img.toInstant(), ZoneId.systemDefault())))
                .orElse(null);
    }

    private LocalDateTime findVideoAge(Path path) throws ImageProcessingException, IOException {
        List<LocalDateTime> dates = new ArrayList<>();
        if (isMp4(path)) {
            dates.addAll(findMp4VideoAge(path));
        }

        dates.addAll(findVideoDatesWithFFProbe(path));

        dates.addAll(findVideoFromFileName(path));

        return dates.stream()
                .filter(date -> isValidDate(date))
                .min(LocalDateTime::compareTo)
                .orElse(null);

    }

    private List<LocalDateTime> findMp4VideoAge(Path path) {
        List<LocalDateTime> dates = new ArrayList<>();
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(path.toFile());

            Collection<Mp4Directory> mp4Directories = metadata.getDirectoriesOfType(Mp4Directory.class);
            for (Mp4Directory mp4Directory : mp4Directories) {
                Date mp4CreateTime = mp4Directory.getDate(Mp4MediaDirectory.TAG_CREATION_TIME, TimeZone.getTimeZone(ZoneId.systemDefault()));
                if (mp4CreateTime != null) {
                    LocalDateTime createTime = LocalDateTime.ofInstant(mp4CreateTime.toInstant(), ZoneId.systemDefault());
                    if (isValidDate(createTime)) {
                        dates.add(truncate(createTime));
                    }
                }

                Date dateTime = mp4Directory.getDate(Mp4Directory.TAG_CREATION_TIME, TimeZone.getTimeZone(ZoneId.systemDefault()));
                if (dateTime != null) {
                    LocalDateTime createTime = LocalDateTime.ofInstant(dateTime.toInstant(), ZoneId.systemDefault());
                    if (isValidDate(createTime)) {
                        dates.add(truncate(createTime));
                    }
                }
            }
        } catch (ImageProcessingException | IOException ignore) {
        }
        return dates;
    }

    private List<LocalDateTime> findVideoDatesWithFFProbe(Path path) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("ffprobe", "-v", "quiet", "-print_format", "flat", "-show_format", path.toString());
        Process process = pb.start();
        try (InputStream is = process.getInputStream()) {

            return new BufferedReader(new InputStreamReader(is))
                    .lines()
                    .filter(line -> line.contains("format.tags.date") || line.contains("format.tags.creation_time"))
                    .map(line -> {
                        String lastPart = line.substring(line.indexOf("\"") + 1, line.length() - 1);
                        try {
                            return LocalDateTime.parse(lastPart, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSX"));
                        } catch (DateTimeParseException ignore) {
                        }
                        try {
                            return LocalDateTime.parse(lastPart, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        } catch (DateTimeParseException ignore) {
                        }
                        try {
                            return LocalDateTime.parse(lastPart, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        } catch (DateTimeParseException ignore) {
                        }
                        try {
                            return LocalDateTime.parse(lastPart, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        } catch (DateTimeParseException ignore) {
                        }
                        try {
                            return LocalDateTime.parse(lastPart, DateTimeFormatter.ISO_ZONED_DATE_TIME);
                        } catch (DateTimeParseException ignore) {
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .filter(localDate -> isValidDate(localDate))
                    .collect(Collectors.toList());
        }
    }

    private List<LocalDateTime> findVideoFromFileName(Path path) {
        List<LocalDateTime> dates = new ArrayList<>();
        try {
            String fileName = path.getFileName().toString();
            fileName = fileName.replaceFirst(".*([0-9]{8}_[0-9]{6}).*", "$1");

            dates.add(LocalDateTime.parse(fileName, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        } catch (Exception ignore) {
        }

        return dates;
    }

    private boolean isValidDate(LocalDateTime localDate) {
        if (localDate != null) {
            boolean isFirstFirst1970 = localDate.getYear() == 1970 && localDate.getMonth() == Month.JANUARY && localDate.getDayOfMonth() == 1;
            boolean isFistFirst1904 = localDate.getYear() == 1904 && localDate.getMonth() == Month.JANUARY && localDate.getDayOfMonth() == 1;
            return !isFirstFirst1970 && !isFistFirst1904;
        }
        return false;
    }

    private LocalDateTime getFileLastModification(Path path) throws IOException {
        FileTime fileTime = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS);
        LocalDateTime createTime = LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.systemDefault());
        return truncate(createTime);
    }

    private LocalDateTime truncate(LocalDateTime dateTime) {
        return dateTime.truncatedTo(ChronoUnit.SECONDS);
    }

}
