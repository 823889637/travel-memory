package com.travelmemory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelmemory.entity.AppUser;
import org.apache.ibatis.annotations.Select;

public interface AppUserMapper extends BaseMapper<AppUser> {
    @Select("SELECT * FROM app_user WHERE id = #{id} FOR UPDATE")
    AppUser selectByIdForUpdate(Long id);
}
