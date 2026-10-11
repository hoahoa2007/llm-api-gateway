package com.example.llmgateway.channel.service;

import com.example.llmgateway.channel.dto.ChannelCreateRequest;
import com.example.llmgateway.channel.dto.ChannelResponse;
import com.example.llmgateway.channel.dto.ChannelUpdateRequest;

import java.util.List;

public interface UpstreamChannelService {

    ChannelResponse createChannel(ChannelCreateRequest request);

    List<ChannelResponse> listChannels();

    ChannelResponse updateChannel(Long id, ChannelUpdateRequest request);

    void updateStatus(Long id, String status);

    void deleteChannel(Long id);
}
