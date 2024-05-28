package com.nosetr.video.hub.controller;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nosetr.video.hub.config.SecurityConfig.CustomPrincipal;
import com.nosetr.video.hub.dto.ScoreDto;
import com.nosetr.video.hub.dto.UserDto;
import com.nosetr.video.hub.dto.VideoDto;
import com.nosetr.video.hub.dto.VideoResponseDto;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(OrderAnnotation.class)
class ApiControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private ObjectMapper objectMapper;

	private static final String urlString = "/api";
	private static String bearer_token;
	private static String userID;
	private static ScoreDto scoreDto;
	private static HashSet<String> ownVideosList = new HashSet<>();
	private static HashSet<String> videosList = new HashSet<>();

	@Value("${firebase.key}")
	private String firebaseKey;
	@Value("${firebase.userId}")
	private String firebaseUserId;
	@Value("${firebase.email}")
	private String firebaseEmail;
	@Value("${firebase.password}")
	private String firebasePassword;

	/**
	 * Helper to get a random int
	 */
	private static int randomScore() {
		Random random = new Random();
		return random.nextInt(5) + 1; // between 1 and 5
	}

	/**
	 * Helper to create formattedDate
	 */
	private static String createDate(int days) {
		SimpleDateFormat formattedDate = new SimpleDateFormat("yyyy-MM-dd");
		Calendar c = Calendar.getInstance();
		c.add(Calendar.DATE, days);
		return formattedDate.format(c.getTime());
	}

	@Test
	@Order(1)
	void getUserInfo_withoutAuth_withError() throws Exception {

		webTestClient.get()
				.uri(urlString + "/user")
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus()
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@Order(2)
	void getUserInfo_withAuth_withSuccess() throws Exception {

		String uriString = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword";

		Map<String, Object> userMap = Map.of(
				"email", firebaseEmail, "password", firebasePassword, "returnSecureToken", true
		);

		// Get FIREBASE idToken:
		webTestClient.post()
				.uri(
						new URIBuilder(uriString)
								.addParameter("key", firebaseKey)
								.build()
				)
				.contentType(MediaType.APPLICATION_JSON)
				.body(
						BodyInserters.fromValue(userMap)
				)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$.idToken")
				.value(t -> {
					bearer_token = "Bearer " + t; // Set global bearer_token for next tests if we need

					// Make request for test
					webTestClient.get()
							.uri(urlString + "/user")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + t)
							.accept(MediaType.APPLICATION_JSON)
							.exchange()
							.expectStatus()
							.isOk()
							.expectBody(CustomPrincipal.class)
							.consumeWith(response -> {
								CustomPrincipal userDto = response.getResponseBody();

								Assertions.assertNotNull(userDto);

								Assertions.assertEquals(firebaseUserId, userDto.getId());
								Assertions.assertEquals(firebaseEmail, userDto.getName());
							});
				});
	}

	@Test
	@Order(3)
	void setVideo_withAuth_withSuccess() throws Exception {

		VideoDto videoDto = new VideoDto()
				.toBuilder()
				.day(createDate(-1)) // yesterday
				.from("09:00")
				.till("17:00")
				.title("study")
				.build();

		String valueAsString = objectMapper.writeValueAsString(videoDto);

		webTestClient.post()
				.uri(urlString + "/video")
				.header(HttpHeaders.AUTHORIZATION, bearer_token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromValue(valueAsString))
				.exchange()
				.expectStatus()
				.isOk();

	}

	@Test
	@Order(4)
	void getListOfVideos_withAuth_withSuccess() throws Exception {
		List<VideoResponseDto> videos = webTestClient.get()
				.uri(urlString + "/video")
				.header(HttpHeaders.AUTHORIZATION, bearer_token)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBodyList(VideoResponseDto.class)
				.returnResult()
				.getResponseBody();

		Assertions.assertNotNull(videos, "The list of scores should not be zero");

		Assertions.assertFalse(videos.isEmpty(), "The list of scores should not be empty");

		for (VideoResponseDto video : videos) {
			Assertions.assertNotNull(video.getId(), "Each video should have an ID");
			Assertions.assertNotNull(video.getUserId(), "Each video should have a user_id");

			if (firebaseUserId.equals(video.getUserId())) {
				ownVideosList.add(video.getId());
			} else {
				videosList.add(video.getId());
			}
		}
	}

	@Test
	@Order(5)
	void setScoreToHimself_withAuth_withError() throws Exception {

		scoreDto = new ScoreDto();
		scoreDto.setScore(randomScore());
		scoreDto.setText("my voting");
		scoreDto.setVoter(firebaseUserId);

		String valueAsString = objectMapper.writeValueAsString(scoreDto);

		for (String videoId : ownVideosList) {
			webTestClient.post()
					.uri(urlString + "/video/" + videoId)
					.header(HttpHeaders.AUTHORIZATION, bearer_token)
					.contentType(MediaType.APPLICATION_JSON)
					.body(
							BodyInserters.fromValue(valueAsString)
					)
					.exchange()
					.expectStatus()
					.isEqualTo(HttpStatusCode.valueOf(500))
					.expectBody()
					.jsonPath("$.message")
					.isEqualTo("You can't vote for yourself.");
		}
	}

	@Test
	@Order(6)
	void createNewUser_withSuccess() throws Exception {
		UserDto userDto = new UserDto();
		userDto.setEmail((UUID.randomUUID() + "@user.com").substring(24));
		userDto.setPassword(
				UUID.randomUUID()
						.toString()
		);

		String valueAsString = objectMapper.writeValueAsString(userDto);

		webTestClient.post()
				.uri(urlString + "/register")
				.header(HttpHeaders.AUTHORIZATION, bearer_token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(
						BodyInserters.fromValue(valueAsString)
				)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody(UserDto.class)
				.consumeWith(response -> {
					UserDto newUser = response.getResponseBody();

					Assertions.assertNotNull(newUser);

					Assertions.assertEquals(newUser.getEmail(), userDto.getEmail());
					Assertions.assertTrue(newUser.isEmailVerified());

					userID = newUser.getId();
				});
	}

	@Test
	@Order(7)
	void setVideoToNewUser_withAuth_withError() throws Exception {

		VideoDto videoDto = new VideoDto()
				.toBuilder()
				.day(createDate(1)) // tomorrow
				.from("08:00")
				.till("13:00")
				.title("traineeship")
				.build();

		String valueAsString = objectMapper.writeValueAsString(videoDto);

		webTestClient.post()
				.uri(urlString + "/video")
				.header(HttpHeaders.AUTHORIZATION, bearer_token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromValue(valueAsString))
				.exchange()
				.expectStatus()
				.isEqualTo(HttpStatus.BAD_REQUEST)
				.expectBody(String.class)
				.isEqualTo("The date can not be after today");
	}

	@Test
	@Order(8)
	void setVideoToNewUser_withAuth_withSuccess() throws Exception {

		VideoDto videoDto = new VideoDto()
				.toBuilder()
				.userId(userID)
				.day(createDate(0)) // today
				.from("08:00")
				.till("13:00")
				.title("traineeship")
				.build();

		String valueAsString = objectMapper.writeValueAsString(videoDto);

		webTestClient.post()
				.uri(urlString + "/video")
				.header(HttpHeaders.AUTHORIZATION, bearer_token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromValue(valueAsString))
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody(String.class)
				.isEqualTo("Video successfully set.");
	}

	@Test
	@Order(9)
	void getSecondListOfVideos_withAuth_withSuccess() throws Exception {
		List<VideoResponseDto> videos = webTestClient.get()
				.uri(urlString + "/video")
				.header(HttpHeaders.AUTHORIZATION, bearer_token)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBodyList(VideoResponseDto.class)
				.returnResult()
				.getResponseBody();

		Assertions.assertNotNull(videos, "The list of videos should not be zero");

		Assertions.assertFalse(videos.isEmpty(), "The list of videos should not be empty");

		videosList = new HashSet<>();
		for (VideoResponseDto video : videos) {
			Assertions.assertNotNull(video.getId(), "Each video should have an ID");
			Assertions.assertNotNull(video.getUserId(), "Each video should have a user_id");

			if (firebaseUserId.equals(video.getUserId())) {
				ownVideosList.add(video.getId());
			} else {
				if (video.getRatings() == null)
					videosList.add(video.getId());
			}
		}
	}

	@Test
	@Order(10)
	void setScoreToAnotherUser_withAuth_withSuccess() throws Exception {

		scoreDto = new ScoreDto();
		scoreDto.setScore(randomScore());
		scoreDto.setText("my next voting");

		String valueAsString = objectMapper.writeValueAsString(scoreDto);

		for (String videoId : videosList) {
			webTestClient.post()
					.uri(urlString + "/video/" + videoId)
					.header(HttpHeaders.AUTHORIZATION, bearer_token)
					.contentType(MediaType.APPLICATION_JSON)
					.body(
							BodyInserters.fromValue(valueAsString)
					)
					.exchange()
					.expectStatus()
					.isOk();
		}
	}

	@Test
	@Order(11)
	void getVideosOfTheWeek_withAuth_withSuccess() throws Exception {
		LocalDate now = LocalDate.now();
		int year = now.getYear();
		int week = now.get(
				WeekFields.of(Locale.getDefault())
						.weekOfYear()
		);

		List<VideoResponseDto> videos = webTestClient.get()
				.uri(String.format("%s/by-week/%d/%d", urlString, year, week))
				.header(HttpHeaders.AUTHORIZATION, bearer_token)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBodyList(VideoResponseDto.class)
				.returnResult()
				.getResponseBody();

		Assertions.assertNotNull(videos, "The list of videos should not be zero");

		Assertions.assertFalse(videos.isEmpty(), "The list of videos should not be empty");

		for (VideoResponseDto video : videos) {
			Assertions.assertNotNull(video.getId(), "Each video should have an ID");
			Assertions.assertNotNull(video.getUserId(), "Each video should have a user_id");

			if (firebaseUserId.equals(video.getUserId())) {
				ownVideosList.add(video.getId());
			} else {
				videosList.add(video.getId());
			}
		}
	}
}
