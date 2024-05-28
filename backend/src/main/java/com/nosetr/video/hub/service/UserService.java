package com.nosetr.video.hub.service;

import com.google.firebase.auth.FirebaseAuthException;
import com.nosetr.video.hub.dto.UserDto;

import reactor.core.publisher.Mono;

public interface UserService {
	
	Mono<UserDto> createUser(UserDto userDto) throws FirebaseAuthException;

}
