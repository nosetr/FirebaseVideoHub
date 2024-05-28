package com.nosetr.video.hub.service.impl;

import org.springframework.stereotype.Service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.nosetr.video.hub.dto.UserDto;
import com.nosetr.video.hub.service.UserService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

	private final FirebaseAuth firebaseAuth;

	@Override
	public Mono<UserDto> createUser(UserDto userDto) throws FirebaseAuthException {

		CreateRequest request = new CreateRequest();
		request.setEmail(userDto.getEmail());
		request.setPassword(userDto.getPassword());
		request.setEmailVerified(Boolean.TRUE);

		return Mono.fromCallable(() -> firebaseAuth.createUser(request))
				.map(
						userVideo -> new UserDto().toBuilder()
								.id(userVideo.getUid())
								.email(userVideo.getEmail())
								.emailVerified(userVideo.isEmailVerified())
								.build()
				)
				.onErrorMap(FirebaseAuthException.class, e -> {
					return new RuntimeException("Failed to create user", e);
				});
	}
}
