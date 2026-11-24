package com.studyspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studyspace.entity.Violation;

/**
 * 违约记录 Mapper
 * <p>预约模块只负责写入（取消扣分、超时违约），查询和撤销留给违约管理模块</p>
 */
public interface ViolationMapper extends BaseMapper<Violation> {
}
