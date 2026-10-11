package com.example.llmgateway.apikey.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.llmgateway.apikey.entity.DownstreamApiKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface DownstreamApiKeyMapper extends BaseMapper<DownstreamApiKey> {

    @Update("""
            UPDATE downstream_api_key
            SET used_amount = used_amount + 1,
                updated_at = NOW(3)
            WHERE id = #{id}
              AND status = 'ACTIVE'
              AND (quota_amount = 0 OR used_amount < quota_amount)
            """)
    int consumeQuota(@Param("id") Long id);
}
