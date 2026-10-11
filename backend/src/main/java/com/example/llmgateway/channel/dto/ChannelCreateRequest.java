package com.example.llmgateway.channel.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChannelCreateRequest(
        @NotBlank(message = "渠道名称不能为空")
        @Size(max = 100, message = "渠道名称不能超过 100 个字符")
        String name,

        @NotBlank(message = "上游地址不能为空")
        @Size(max = 255, message = "上游地址不能超过 255 个字符")
        String baseUrl,

        @Size(max = 512, message = "上游 Key 过长")
        String apiKey,

        @NotBlank(message = "模型名不能为空")
        String model,

        @Min(value = 1, message = "权重最小为 1")
        @Max(value = 100, message = "权重最大为 100")
        Integer weight
) {
}
