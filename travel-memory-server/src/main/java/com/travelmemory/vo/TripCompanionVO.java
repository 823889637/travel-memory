package com.travelmemory.vo;

import com.travelmemory.entity.TripCompanion;

public record TripCompanionVO(
        Long id,
        Long tripId,
        String name,
        String avatarUrl,
        boolean isSelf,
        Integer sortOrder,
        boolean active,
        long memoryCount
) {
    public static TripCompanionVO from(TripCompanion companion, long memoryCount) {
        return new TripCompanionVO(companion.getId(), companion.getTripId(), companion.getName(),
                companion.getAvatarUrl(), Boolean.TRUE.equals(companion.getIsSelf()), companion.getSortOrder(),
                Boolean.TRUE.equals(companion.getActive()), memoryCount);
    }
}
