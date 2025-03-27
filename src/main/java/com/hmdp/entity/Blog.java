package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog")
public class Blog implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long shopId;

    private Long userId;
    /**
     * user icon
     */
    @TableField(exist = false)
    private String icon;
    /**
     * username
     */
    @TableField(exist = false)
    private String name;
    /**
     * if the blog had been liked
     */
    @TableField(exist = false)
    private Boolean isLike;


    private String title;

    private String images;


    private String content;

    /**
     * liked number
     */
    private Integer liked;


    private Integer comments;


    private LocalDateTime createTime;


    private LocalDateTime updateTime;


}
