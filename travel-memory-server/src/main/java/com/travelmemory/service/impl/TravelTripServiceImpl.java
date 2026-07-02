package com.travelmemory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travelmemory.entity.TravelTrip;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.TravelTripMapper;
import com.travelmemory.service.TravelTripService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TravelTripServiceImpl extends ServiceImpl<TravelTripMapper, TravelTrip> implements TravelTripService {

    private final TravelTripMapper travelTripMapper;

    public TravelTripServiceImpl(TravelTripMapper travelTripMapper) {
        this.travelTripMapper = travelTripMapper;
    }

    @Override
    public List<TravelTrip> list() {
        return travelTripMapper.selectList(new LambdaQueryWrapper<TravelTrip>()
                .orderByDesc(TravelTrip::getStartDate)
                .orderByDesc(TravelTrip::getCreateTime));
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
}
