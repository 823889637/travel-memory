package com.travelmemory.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CoordinateConverterTest {

    @Test
    void convertsGcj02BackToTheOriginalWgs84Coordinate() {
        BigDecimal latitude = new BigDecimal("39.9042000");
        BigDecimal longitude = new BigDecimal("116.4074000");

        CoordinateConverter.Coordinate gcj02 = CoordinateConverter.wgs84ToGcj02(latitude, longitude);
        CoordinateConverter.Coordinate restored = CoordinateConverter.gcj02ToWgs84(
                BigDecimal.valueOf(gcj02.latitude()),
                BigDecimal.valueOf(gcj02.longitude())
        );

        assertTrue(Math.abs(restored.latitude() - latitude.doubleValue()) < 0.000001);
        assertTrue(Math.abs(restored.longitude() - longitude.doubleValue()) < 0.000001);
    }

    @Test
    void leavesCoordinatesOutsideChinaUntouched() {
        BigDecimal latitude = new BigDecimal("48.8566000");
        BigDecimal longitude = new BigDecimal("2.3522000");

        CoordinateConverter.Coordinate converted = CoordinateConverter.gcj02ToWgs84(latitude, longitude);

        assertEquals(latitude.doubleValue(), converted.latitude());
        assertEquals(longitude.doubleValue(), converted.longitude());
    }
}
