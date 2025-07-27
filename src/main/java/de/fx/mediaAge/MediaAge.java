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
import com.drew.metadata.png.PngDirectory;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
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
                System.out.println("no exif date for " + path);
            }

        } catch (IOException | ImageProcessingException ex) {
            System.getLogger(MediaAge.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }

    private LocalDateTime findMediaAge(Path path) throws IOException, ImageProcessingException {
        if (isImage(path)) {
            return findImageExifDate(path);
        }
        if (isMp4(path)) {
            return findVideoAge(path);
        }
        return null;
    }

    private boolean isImage(Path path) throws IOException {
        String mimeType = Files.probeContentType(path);
        if (mimeType != null && mimeType.startsWith("image/")) {
            return true;
        }
        return false;
    }

    private boolean isMp4(Path path) throws IOException {
        String mimeType = Files.probeContentType(path);
        return mimeType != null && "video/mp4".equals(mimeType);
    }

    private LocalDateTime findImageExifDate(Path path) throws ImageProcessingException, IOException {
        List<Date> imageDates = new ArrayList();
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
        if (isMp4(path)) {
            return findMp4VideoAge(path);
        }
        return null;
    }

    private LocalDateTime findMp4VideoAge(Path path) throws ImageProcessingException, IOException {
        List<LocalDateTime> dates = new ArrayList<>();
        Metadata metadata = ImageMetadataReader.readMetadata(path.toFile());
        Collection<Mp4Directory> mp4Directories = metadata.getDirectoriesOfType(Mp4Directory.class);
        for (Mp4Directory mp4Directory : mp4Directories) {
            Date dateTime = mp4Directory.getDate(Mp4Directory.TAG_CREATION_TIME, TimeZone.getTimeZone(ZoneId.systemDefault()));
            if (dateTime != null) {
                LocalDateTime createTime = LocalDateTime.ofInstant(dateTime.toInstant(), ZoneId.systemDefault());
                boolean isFirstFirst1970 = createTime.getYear() == 1970 && createTime.getMonth() == Month.JANUARY && createTime.getDayOfMonth() == 1;
                boolean isFistFirst1904 = createTime.getYear() == 1904 && createTime.getMonth() == Month.JANUARY && createTime.getDayOfMonth() == 1;
                if (!isFirstFirst1970 && !isFistFirst1904) {
                    dates.add(truncate(createTime));
                }
            }
        }
        return dates.stream()
                .min(LocalDateTime::compareTo)
                .orElse(null);
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
