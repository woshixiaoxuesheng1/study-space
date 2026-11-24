package com.studyspace.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 自动填充 createTime / updateTime
 * <p>实体字段上标了 @TableField(fill = ...) 的，插入和更新时由这里统一赋值，
 * 业务代码不用每次手动 set，也不会忘了填。</p>
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    // 钩子函数执行insert和save方法前设置当前时间
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    //执行update前更新当前时间
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
