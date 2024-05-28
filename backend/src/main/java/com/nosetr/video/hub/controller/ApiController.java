package com.nosetr.video.hub.controller;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.firebase.auth.FirebaseAuthException;
import com.nosetr.video.hub.config.SecurityConfig.CustomPrincipal;
import com.nosetr.video.hub.dto.ScoreDto;
import com.nosetr.video.hub.dto.VideoDto;
import com.nosetr.video.hub.dto.VideoResponseDto;
import com.nosetr.video.hub.dto.UserDto;
import com.nosetr.video.hub.service.VideoService;
import com.nosetr.video.hub.service.UserService;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

	private final UserService userService;
	private final VideoService videoService;

	@GetMapping("/user")
	public Mono<CustomPrincipal> getUserInfo(Authentication authentication) {
		CustomPrincipal customPrincipal = (CustomPrincipal) authentication.getPrincipal();

		return Mono.just(customPrincipal);
	}

	@PostMapping("/register")
	public Mono<UserDto> register(@RequestBody @NotNull UserDto userDto) throws FirebaseAuthException {
		// Call registrations service
		return userService.createUser(userDto);
	}

	@PostMapping("/video")
	public Mono<ResponseEntity<String>> saveVideo(
			@RequestBody VideoDto videoDto, Authentication authentication
	) {

		Date today = new Date();
		SimpleDateFormat formattedDate = new SimpleDateFormat("yyyy-MM-dd");
		String formatToday = formattedDate.format(today);

		if (
			videoDto.getDay()
					.compareTo(formatToday) > 0
		) {
			return Mono.just(
					ResponseEntity.badRequest()
							.body("The date can not be after today")
			); // 400 Bad Request
		}

		CustomPrincipal customPrincipal = (CustomPrincipal) authentication.getPrincipal();
		videoDto.setCreator(customPrincipal.getId());

		if (
			videoDto.getUserId() == null || videoDto.getUserId()
					.isEmpty()
		)
			videoDto.setUserId(customPrincipal.getId());

		return videoService.setVideo(videoDto);
	}

	@GetMapping("/video")
	public Flux<VideoResponseDto> getVideoList() throws InterruptedException, ExecutionException {
		return videoService.getAll();
	}

	@PostMapping("/video/{videoId}")
	public Mono<Object> addRating(
			@PathVariable String videoId, @RequestBody ScoreDto scoreDto, Authentication authentication
	) {
		CustomPrincipal customPrincipal = (CustomPrincipal) authentication.getPrincipal();

		return videoService.addRating(
				videoId, scoreDto.toBuilder()
						.voter(customPrincipal.getId())
						.build()
		);
	}

	@GetMapping("/by-week/{year}/{week}")
	public Mono<List<VideoResponseDto>> getVideosForWeek(@PathVariable int year, @PathVariable int week)
			throws InterruptedException, ExecutionException {
		return videoService.getVideosForWeek(week, year);
	}
}
