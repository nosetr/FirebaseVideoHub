package com.nosetr.video.hub.service;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.springframework.http.ResponseEntity;

import com.nosetr.video.hub.dto.ScoreDto;
import com.nosetr.video.hub.dto.VideoDto;
import com.nosetr.video.hub.dto.VideoResponseDto;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VideoService {

	Mono<ResponseEntity<String>> setVideo(VideoDto videoDto);

	Mono<Object> addRating(String videoId, ScoreDto scoreDto);

	Flux<VideoResponseDto> getAll() throws InterruptedException, ExecutionException;

	Mono<List<VideoResponseDto>> getVideosForWeek(int weekOfYear, int year)
			throws InterruptedException, ExecutionException;

}
