package com.nosetr.video.hub.util;

import java.util.concurrent.CompletableFuture;

import com.google.api.core.ApiFuture;

/**
 * Helper function to convert ApiFuture to CompletableFuture
 */
public class ApiFutureUtil {
	public static <T> CompletableFuture<T> toCompletableFuture(ApiFuture<T> apiFuture) {
		CompletableFuture<T> completableFuture = new CompletableFuture<>();
		apiFuture.addListener(
				() -> {
					try {
						completableFuture.complete(apiFuture.get());
					} catch (Exception e) {
						completableFuture.completeExceptionally(e);
					}
				}, Runnable::run
		);
		return completableFuture;
	}
}