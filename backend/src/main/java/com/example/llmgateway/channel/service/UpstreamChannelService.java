package com.example.llmgateway.channel.service;

import com.example.llmgateway.channel.dto.ChannelCreateRequest;
import com.example.llmgateway.channel.dto.ChannelResponse;

import java.util.List;

public interface UpstreamChannelService {

    ChannelResponse createChannel(ChannelCreateRequest request);

    List<ChannelResponse> listChannels();

    void updateStatus(Long id, String status);
}
