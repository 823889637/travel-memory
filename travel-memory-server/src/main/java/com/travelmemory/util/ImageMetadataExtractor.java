package com.travelmemory.util;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ImageMetadataExtractor {

    private static final Logger log = LoggerFactory.getLogger(ImageMetadataExtractor.class);

    public ImageMetadataInfo extract(String imagePath) {
        ImageMetadataInfo info = new ImageMetadataInfo();
        if (imagePath == null || imagePath.isBlank()) {
            return info;
        }

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new File(imagePath));
            fillTakenTime(metadata, info);
            fillLocation(metadata, info);
        } catch (Exception ex) {
            log.info("Failed to read image EXIF metadata from {}", imagePath, ex);
        }

        return info;
    }

    private void fillTakenTime(Metadata metadata, ImageMetadataInfo info) {
        ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        Date date = null;
        if (directory != null) {
            date = directory.getDateOriginal();
            if (date == null) {
                date = directory.getDateDigitized();
            }
        }

        if (date == null) {
            ExifIFD0Directory ifd0Directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0Directory != null) {
                date = ifd0Directory.getDate(ExifIFD0Directory.TAG_DATETIME);
            }
        }

        if (date == null) {
            return;
        }

        info.setPhotoTakenTime(LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
    }

    private void fillLocation(Metadata metadata, ImageMetadataInfo info) {
        GpsDirectory directory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (directory == null) {
            return;
        }

        GeoLocation geoLocation = directory.getGeoLocation();
        if (geoLocation == null || geoLocation.isZero()) {
            return;
        }

        info.setLatitude(BigDecimal.valueOf(geoLocation.getLatitude()));
        info.setLongitude(BigDecimal.valueOf(geoLocation.getLongitude()));
    }
}
