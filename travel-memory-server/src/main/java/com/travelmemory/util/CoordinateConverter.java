package com.travelmemory.util;

import java.math.BigDecimal;

public final class CoordinateConverter {

    private static final double PI = Math.PI;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private CoordinateConverter() {
    }

    public static boolean isInChina(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();
        return lng >= 72.004 && lng <= 137.8347 && lat >= 0.8293 && lat <= 55.8271;
    }

    public static Coordinate wgs84ToGcj02(BigDecimal latitude, BigDecimal longitude) {
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();
        if (!isInChina(latitude, longitude)) {
            return new Coordinate(lat, lng);
        }

        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new Coordinate(lat + dLat, lng + dLng);
    }

    public static Coordinate gcj02ToWgs84(BigDecimal latitude, BigDecimal longitude) {
        double gcjLat = latitude.doubleValue();
        double gcjLng = longitude.doubleValue();
        if (!isInChina(latitude, longitude)) {
            return new Coordinate(gcjLat, gcjLng);
        }

        // The inverse transform has no simple closed form. Iterating from the GCJ-02
        // point keeps stored WGS84 values aligned with the map display conversion.
        double wgsLat = gcjLat;
        double wgsLng = gcjLng;
        for (int index = 0; index < 6; index++) {
            Coordinate converted = wgs84ToGcj02(BigDecimal.valueOf(wgsLat), BigDecimal.valueOf(wgsLng));
            wgsLat -= converted.latitude() - gcjLat;
            wgsLng -= converted.longitude() - gcjLng;
        }
        return new Coordinate(wgsLat, wgsLng);
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y
                + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y
                + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    public record Coordinate(double latitude, double longitude) {
    }
}
