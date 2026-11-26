package com.studyspace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 违约记录实体（FR-05-05 / FR-05-06）
 *
 * <p>这张表同时承担两件事：</p>
 * <ol>
 *   <li><b>违约事实</b>：谁、哪次预约、什么类型、什么时候发生的；</li>
 *   <li><b>信用分流水</b>：这次扣了多少分、有没有被撤销。</li>
 * </ol>
 *
 * <p>之所以不单独建 credit_log 表：本系统信用分只有"违约扣分"和"撤销返还"两种变动，
 * 每一次变动都必然对应一条违约记录，deduct_score + revoked 已经能完整还原分数变化；
 * 需求第 6 章约定数据库只有 6 张表，多一张表反而和文档对不上。</p>
 *
 * <p>撤销采用逻辑撤销（revoked = 1），不做物理删除，
 * 满足"信用分变动记录不可删除"的要求，答辩时也能演示撤销前后的对比。</p>
 */
@Data
@TableName("violation")
public class Violation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 违约人 id */
    private Long userId;

    /** 关联预约 id；管理员手动扣分没有对应预约，这里为空 */
    private Long bookingId;

    /** 违约类型，取值见 ViolationType：NO_SHOW / LATE_CANCEL / MANUAL */
    private String type;

    /** 本次扣减的信用分，撤销时按这个值原额返还 */
    private Integer deductScore;

    /** 违约发生时间（不是记录写入时间：手动扣分可能补录昨天的违约） */
    private LocalDateTime happenTime;

    /** 给用户看的违约说明；撤销后会在末尾追加撤销说明 */
    private String reason;

    /** 是否已撤销：0 否 / 1 是 */
    private Integer revoked;

    /** 撤销时间 */
    private LocalDateTime revokeTime;

    /** 操作人（撤销或手动扣分的管理员）id；系统自动判违约时为空 */
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
