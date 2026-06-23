package com.neostride.server.config;

import com.neostride.server.community.dto.CommentCursorResponse;
import com.neostride.server.community.dto.MyCommentActivityPageResponse;
import com.neostride.server.community.dto.MyCommentActivityResponse;
import com.neostride.server.storage.ImageUrlResolver;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageUrlResponseAdviceCommentActivityTest {
	private final ImageUrlResponseAdvice advice = new ImageUrlResponseAdvice(
			new ImageUrlResolver("https://api.neostride.test"));

	@Test
	void beforeBodyWrite_rewritesMyCommentActivityImageUrls() {
		MyCommentActivityResponse response = new MyCommentActivityResponse("FEED", 10L, 2L, "runner",
				"/uploads/profile/runner.jpg", false, "NONE", null, "title", "body",
				"2026-06-02T10:00:00", new BigDecimal("5.25"), 1800, 343, true,
				"/uploads/routes/feed.png", List.of("/uploads/community/feed.jpg", "content://local"),
				3, 2, 0, false, true, true, false, false, 99L, "nice",
				"2026-06-02T10:05:00", true);

		MyCommentActivityResponse result = (MyCommentActivityResponse) advice.beforeBodyWrite(response, null, null, null, null, null);

		assertThat(result.profileImageUrl()).isEqualTo("https://api.neostride.test/uploads/profile/runner.jpg");
		assertThat(result.routeMapUrl()).isEqualTo("https://api.neostride.test/uploads/routes/feed.png");
		assertThat(result.imageUrls()).containsExactly("https://api.neostride.test/uploads/community/feed.jpg");
	}

	@Test
	void beforeBodyWrite_rewritesMyCommentActivityPageItems() {
		MyCommentActivityResponse response = new MyCommentActivityResponse("FEED", 10L, 2L, "runner",
				"/uploads/profile/runner.jpg", false, "NONE", null, "title", "body",
				"2026-06-02T10:00:00", new BigDecimal("5.25"), 1800, 343, true,
				"/uploads/routes/feed.png", List.of("/uploads/community/feed.jpg"),
				3, 2, 0, false, true, true, false, false, 99L, "nice",
				"2026-06-02T10:05:00", true);
		MyCommentActivityPageResponse page = new MyCommentActivityPageResponse(
				List.of(response), new CommentCursorResponse("2026-06-02T10:05:00", 99L), false);

		MyCommentActivityPageResponse result = (MyCommentActivityPageResponse) advice.beforeBodyWrite(page, null, null, null, null, null);

		assertThat(result.items()).hasSize(1);
		assertThat(result.items().getFirst().profileImageUrl()).isEqualTo("https://api.neostride.test/uploads/profile/runner.jpg");
		assertThat(result.nextCursor()).isSameAs(page.nextCursor());
	}
}
