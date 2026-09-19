package com.example.llmgateway.channel.controller;

import com.example.llmgateway.channel.dto.ChannelCreateRequest;
import com.example.llmgateway.channel.dto.ChannelResponse;
import com.example.llmgateway.channel.service.UpstreamChannelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
public class UpstreamChannelController {

    private final UpstreamChannelService channelService;

    public UpstreamChannelController(UpstreamChannelService channelService) {
        this.channelService = channelService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelResponse create(
            @Valid @RequestBody ChannelCreateRequest request
    ) {
        return channelService.createChannel(request);
    }

    @GetMapping
    public List<ChannelResponse> list() {
        return channelService.listChannels();
    }

    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(
            @PathVariable Long id,
            @RequestParam String status
    ) {
        channelService.updateStatus(id, status);
    }
}
