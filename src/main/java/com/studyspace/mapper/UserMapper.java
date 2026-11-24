package com.studyspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studyspace.entity.User;

/**
 * 用户 Mapper
 * <p>继承 BaseMapper 就有单表增删改查，不需要写 XML</p>
 */
public interface UserMapper extends BaseMapper<User> {
}
