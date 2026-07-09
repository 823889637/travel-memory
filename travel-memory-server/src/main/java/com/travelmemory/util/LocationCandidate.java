package com.travelmemory.util;

public class LocationCandidate {

    private final String name;
    private final Source source;
    private final Integer distance;
    private final int index;

    public LocationCandidate(String name, Source source, Integer distance, int index) {
        this.name = name;
        this.source = source;
        this.distance = distance;
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public Source getSource() {
        return source;
    }

    public Integer getDistance() {
        return distance;
    }

    public int getIndex() {
        return index;
    }

    public enum Source {
        AOI_CONTAINS(100),
        BUILDING(90),
        NEIGHBORHOOD(80),
        POI(70),
        AOI_NEAR(60),
        BUSINESS_AREA(50),
        TOWNSHIP(40),
        ADDRESS(10);

        private final int rank;

        Source(int rank) {
            this.rank = rank;
        }

        public int getRank() {
            return rank;
        }
    }
}
