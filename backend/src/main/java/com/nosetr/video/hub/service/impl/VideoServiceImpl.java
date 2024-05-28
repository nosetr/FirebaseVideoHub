package com.nosetr.video.hub.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.nosetr.video.hub.dto.ScoreDto;
import com.nosetr.video.hub.dto.VideoDto;
import com.nosetr.video.hub.dto.VideoResponseDto;
import com.nosetr.video.hub.service.VideoService;
import com.nosetr.video.hub.util.ApiFutureUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class VideoServiceImpl implements VideoService {

	private static final Firestore db = FirestoreClient.getFirestore();

	@Override
	public Mono<ResponseEntity<String>> setVideo(VideoDto videoDto) {

		return Mono.fromCallable(() -> {
			db.collection("videos")
					.add(videoDto);
			return "Video successfully set.";
		})
				.map(message -> ResponseEntity.ok(message)) // 200 OK
				.onErrorResume(e -> {
					return Mono.just(
							ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
									.body("Failed to set video: " + e.getMessage())
					); // 500 Internal Server Error
				});
	}

	@Override
	public Mono<Object> addRating(String videosId, ScoreDto scoreDto) {
		DocumentReference reference = db.collection("videos")
				.document(videosId);

		return Mono.fromFuture(ApiFutureUtil.toCompletableFuture(reference.get()))
				.flatMap(documentSnapshot -> {
					if (!documentSnapshot.exists()) { return Mono.error(new RuntimeException("Videos not found.")); }

					VideoDto videoDto = documentSnapshot.toObject(VideoDto.class);

					if (
						scoreDto.getVoter()
								.equals(videoDto.getUserId())
					) {
						return Mono.error(
								new RuntimeException("You can't vote for yourself.")
						);
					}

					List<ScoreDto> ratings = (videoDto.getRatings() == null)
							? new ArrayList<>()
							: videoDto.getRatings();

					if (!ratings.isEmpty()) {
						boolean userAlreadyVoted = ratings.stream()
								.anyMatch(
										r -> r.getVoter()
												.equals(scoreDto.getVoter())
								);

						if (userAlreadyVoted) { return Mono.error(new RuntimeException("User has already voted.")); }
					}

					ratings.add(scoreDto);
					videoDto.setRatings(ratings);
					
					int size = ratings.size();
					int score = 0;
					for (ScoreDto rating : ratings) {
						score += rating.getScore();
					}
					
					videoDto.setAverageRating(score / size);

					return Mono.fromFuture(ApiFutureUtil.toCompletableFuture(reference.set(videoDto)))
							.thenReturn("Rating added successfully");
				});
	}

	@Override
	public Flux<VideoResponseDto> getAll() throws InterruptedException, ExecutionException {

		ApiFuture<QuerySnapshot> apiFuture = db.collection("videos")
				.get();
		List<QueryDocumentSnapshot> documents = apiFuture.get()
				.getDocuments();

		return Flux.fromStream(
				documents.stream()
						.map(doc -> {
							VideoResponseDto dto = doc.toObject(VideoResponseDto.class);
							dto.setId(doc.getId());
							return dto;
						})
		);

	}

	@Override
	public Mono<List<VideoResponseDto>> getVideosForWeek(int weekOfYear, int year) throws InterruptedException, ExecutionException {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		return getAll()
				.filter(video -> {
					LocalDate date = LocalDate.parse(video.getDay(), formatter);
					int videoWeek = date.get(
							WeekFields.of(Locale.getDefault())
									.weekOfYear()
					);
					int videoYear = date.getYear();
					return videoWeek == weekOfYear && videoYear == year;
				})
				.sort((r1, r2) -> {
					LocalDate date1 = LocalDate.parse(r1.getDay(), formatter);
					LocalDate date2 = LocalDate.parse(r2.getDay(), formatter);
					return date1.compareTo(date2);
				})
				.collectList();
	}

}
