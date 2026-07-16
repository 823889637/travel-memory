package com.travelmemory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmemory.config.AmapProperties;
import com.travelmemory.dto.CitySearchResult;
import com.travelmemory.util.LocationNameSelector;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocationServiceImplCityParsingTest {

    @Test
    void preservesCountryAndAdministrativeHierarchyForCityCandidates() throws Exception {
        String response = """
                {
                  "status": "1",
                  "districts": [
                    {
                      "adcode": "120000",
                      "name": "天津市",
                      "level": "province",
                      "center": "117.200983,39.084158",
                      "districts": [
                        {
                          "adcode": "120101",
                          "name": "和平区",
                          "level": "district",
                          "center": "117.214713,39.116884",
                          "districts": []
                        }
                      ]
                    }
                  ]
                }
                """;
        LocationServiceImpl service = new LocationServiceImpl(
                new ObjectMapper(), new AmapProperties(), new LocationNameSelector());

        List<CitySearchResult> results = service.parseDistrictSearchResponse(response);

        assertEquals(2, results.size());
        assertEquals("中国", results.get(0).countryName());
        assertEquals("天津市", results.get(0).provinceName());
        assertEquals("天津市", results.get(0).cityName());
        assertEquals("中国", results.get(1).countryName());
        assertEquals("天津市", results.get(1).provinceName());
        assertEquals("天津市", results.get(1).cityName());
        assertEquals("和平区", results.get(1).districtName());
    }
}
