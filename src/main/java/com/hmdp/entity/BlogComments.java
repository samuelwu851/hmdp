package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
@TableName("tb_blog_comments")
public class BlogComments implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(value = "id", type = IdType.AUTO)
    private Long id;


    private Long userId;


    private Long blogId;


    private Long parentId;

    /**
     * commenter's id
     */
    private Long answerId;

    /**
     * comment content
     */
    private String content;

    /**
     * liked number of comments
     */
    private Integer liked;

    /**
     * status，0：normal，1：reported，2：forbidden
     */
    private Boolean status;


    private LocalDateTime createTime;


    private LocalDateTime updateTime;


}
