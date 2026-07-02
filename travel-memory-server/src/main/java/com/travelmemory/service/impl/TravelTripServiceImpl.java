package com.travelmemory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.entity.TravelTrip;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.mapper.TravelTripMapper;
import com.travelmemory.service.TravelTripService;
import com.travelmemory.vo.TravelTripListVO;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TravelTripServiceImpl extends ServiceImpl<TravelTripMapper, TravelTrip> implements TravelTripService {

    private final TravelTripMapper travelTripMapper;
    private final TravelMemoryMapper travelMemoryMapper;

    public TravelTripServiceImpl(TravelTripMapper travelTripMapper, TravelMemoryMapper travelMemoryMapper) {
        this.travelTripMapper = travelTripMapper;
        this.travelMemoryMapper = travelMemoryMapper;
    }

    @Override
    public List<TravelTripListVO> listForHome() {
        List<TravelTrip> trips = travelTripMapper.selectList(new LambdaQueryWrapper<TravelTrip>()
                .orderByDesc(TravelTrip::getStartDate)
                .orderByDesc(TravelTrip::getCreateTime));
        return trips.stream().map(this::toListVO).toList();
    }

    @Override
    public TravelTrip getById(Long id) {
        TravelTrip travelTrip = travelTripMapper.selectById(id);
        if (travelTrip == null) {
            throw new BusinessException(404, "Trip not found");
        }
        return travelTrip;
    }

    @Override
    @Transactional
    public TravelTrip create(TravelTrip travelTrip) {
        validateDateRange(travelTrip);
        travelTripMapper.insert(travelTrip);
        return getById(travelTrip.getId());
    }

    @Override
    @Transactional
    public TravelTrip update(Long id, TravelTrip travelTrip) {
        getById(id);
        validateDateRange(travelTrip);
        travelTrip.setId(id);
        travelTripMapper.updateById(travelTrip);
        return getById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getById(id);
        travelTripMapper.deleteById(id);
    }

    private void validateDateRange(TravelTrip travelTrip) {
        if (travelTrip.getStartDate() == null || travelTrip.getEndDate() == null) {
            return;
        }
        if (travelTrip.getEndDate().isBefore(travelTrip.getStartDate())) {
            throw new BusinessException(400, "End date cannot be earlier than start date");
        }
    }

    private TravelTripListVO toListVO(TravelTrip trip) {
        TravelTripListVO vo = new TravelTripListVO();
        vo.setId(trip.getId());
        vo.setTitle(trip.getTitle());
        vo.setDescription(trip.getDescription());
        vo.setDestination(trip.getDestination());
        vo.setStartDate(trip.getStartDate());
        vo.setEndDate(trip.getEndDate());
        vo.setCoverPhotoUrl(resolveCoverPhotoUrl(trip));
        vo.setMemoryCount(countMemories(trip.getId()));
        return vo;
    }

    private String resolveCoverPhotoUrl(TravelTrip trip) {
        if (trip.getCoverPhotoUrl() != null && !trip.getCoverPhotoUrl().isBlank()) {
            return trip.getCoverPhotoUrl();
        }

        TravelMemory firstPhotoMemory = travelMemoryMapper.selectOne(new LambdaQueryWrapper<TravelMemory>()
                .eq(TravelMemory::getTripId, trip.getId())
                .isNotNull(TravelMemory::getPhotoUrl)
                .ne(TravelMemory::getPhotoUrl, "")
                .orderByAsc(TravelMemory::getRecordTime)
                .orderByAsc(TravelMemory::getCreateTime)
                .last("LIMIT 1"));
        return firstPhotoMemory == null ? null : firstPhotoMemory.getPhotoUrl();
    }

    private Long countMemories(Long tripId) {
        return travelMemoryMapper.selectCount(new LambdaQueryWrapper<TravelMemory>()
                .eq(TravelMemory::getTripId, tripId));
    }
}
