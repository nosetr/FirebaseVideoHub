package com.nosetr.video.hub.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class VideoResponseDto {

	private String id;
	private String userId;
	private String title;
	private String day;
	private String from;
	private String till;
	private List<ScoreDto> ratings;
	private String creator;
	private double averageRating;
	
}
