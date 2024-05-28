package com.nosetr.video.hub.config;

import java.security.Principal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

	private static final String BEARER_PREFIX = "Bearer ";

	@Bean
	public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
		return http
				// Disable CSRF protection as needed (be cautious with this)
				.csrf(csrf -> csrf.disable())
				.authorizeExchange(
						exchange -> exchange
								.pathMatchers(HttpMethod.OPTIONS)
								.permitAll()
								.anyExchange() // all other routes are not public
								.authenticated()
				)
				// throw exceptions
				.exceptionHandling(
						exceptionHandling -> exceptionHandling
								// customize how to request for authentication
								.authenticationEntryPoint((swe, e) -> {
									log.error("IN securityWebFilterChain - unauthorized error: {}", e.getMessage());
									return Mono.fromRunnable(
											() -> swe.getResponse()
													.setStatusCode(HttpStatus.UNAUTHORIZED)
									);
								})
								.accessDeniedHandler((swe, e) -> {
									log.error("IN securityWebFilterChain - access denied: {}", e.getMessage());
									return Mono.fromRunnable(
											() -> swe.getResponse()
													.setStatusCode(HttpStatus.FORBIDDEN)
									);
								})
				)
				.addFilterAt(authenticationFilter(), SecurityWebFiltersOrder.HTTP_BASIC)
				.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
				.build();
	}

	private AuthenticationWebFilter authenticationFilter() {
		AuthenticationWebFilter filter = new AuthenticationWebFilter(authenticationManager());
		filter.setServerAuthenticationConverter(serverAuthenticationConverter());
		return filter;
	}

	private ReactiveAuthenticationManager authenticationManager() {
		return authentication -> {
			String idToken = authentication.getCredentials()
					.toString();
			try {
				FirebaseToken decodedToken = FirebaseAuth.getInstance()
						.verifyIdToken(idToken);

				CustomPrincipal principal = new CustomPrincipal(
						decodedToken.getUid(), decodedToken.getEmail()
				);

				Authentication auth = new UsernamePasswordAuthenticationToken(principal, decodedToken, null);
				return Mono.just(auth);
			} catch (FirebaseAuthException e) {
				log.error("IN authenticationManager - FirebaseAuthException: {}", e.getMessage());
				return Mono.error(new SecurityException("Invalid token"));
			}
		};
	}

	private ServerAuthenticationConverter serverAuthenticationConverter() {
		return exchange -> {
			String idToken = exchange.getRequest()
					.getHeaders()
					.getFirst("Authorization");
			if (idToken != null && idToken.startsWith(BEARER_PREFIX)) {
				String substring = idToken.substring(BEARER_PREFIX.length());
				return Mono.just(new UsernamePasswordAuthenticationToken(substring, substring));
			} else {
				return Mono.empty();
			}
		};
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CustomPrincipal implements Principal {
		private String id;
		private String name; // as email
	}
}
