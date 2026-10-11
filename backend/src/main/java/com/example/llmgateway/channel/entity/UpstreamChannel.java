package com.example.llmgateway.channel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("upstream_channel")
public class UpstreamChannel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String baseUrl;
    private String apiKeyCiphertext;
    private String model;
    private Integer weight;
    private String status;
    private String healthStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
