package com.neostride.server.config;

import com.neostride.server.community.dto.AccountInfoResponse;
import com.neostride.server.community.dto.CommentResponse;
import com.neostride.server.community.dto.CommunityContentResponse;
import com.neostride.server.community.dto.FeedDetailResponse;
import com.neostride.server.community.dto.FeedPageResponse;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.FriendResponse;
import com.neostride.server.community.dto.MyCommentActivityResponse;
import com.neostride.server.community.dto.SearchUserResponse;
import com.neostride.server.community.dto.TipDetailResponse;
import com.neostride.server.community.dto.TipListResponse;
import com.neostride.server.community.dto.TipUploadResponse;
import com.neostride.server.community.dto.UserProfileResponse;
import com.neostride.server.storage.ImageUrlResolver;
import java.util.List;
import java.util.Locale;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class ImageUrlResponseAdvice implements ResponseBodyAdvice<Object> {
	private final ImageUrlResolver imageUrlResolver;

	public ImageUrlResponseAdvice(ImageUrlResolver imageUrlResolver) {
		this.imageUrlResolver = imageUrlResolver;
	}

	@Override
	public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
		return true;
	}

	@Override
	public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
			Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
			ServerHttpResponse response) {
		boolean thumbnails = shouldUseThumbnails(request);
		return rewrite(body, thumbnails, thumbnails && shouldUseWebpThumbnails(request));
	}

	private Object rewrite(Object body, boolean thumbnails, boolean webpThumbnails) {
		if (body == null) {
			return null;
		}
		if (body instanceof List<?> values) {
			return values.stream().map(value -> rewrite(value, thumbnails, webpThumbnails)).toList();
		}
		if (body instanceof TipListResponse response) {
			return new TipListResponse(tips(response.tips(), thumbnails, webpThumbnails));
		}
		if (body instanceof FeedUploadResponse response) {
			return feed(response, thumbnails, webpThumbnails);
		}
		if (body instanceof FeedPageResponse response) {
			return new FeedPageResponse(feeds(response.items(), thumbnails, webpThumbnails), response.nextCursor(), response.hasMore());
		}
		if (body instanceof CommunityContentResponse response) {
			return communityContent(response, thumbnails, webpThumbnails);
		}
		if (body instanceof MyCommentActivityResponse response) {
			return myCommentActivity(response, thumbnails, webpThumbnails);
		}
		if (body instanceof FeedDetailResponse response) {
			return feedDetail(response);
		}
		if (body instanceof TipUploadResponse response) {
			return tip(response, thumbnails, webpThumbnails);
		}
		if (body instanceof TipDetailResponse response) {
			return tipDetail(response);
		}
		if (body instanceof UserProfileResponse response) {
			return new UserProfileResponse(response.nickname(), imageUrlResolver.toPublicUrl(response.profilePhoto()),
					response.statusMessage(), response.friend(), response.blocked(), response.sent(), response.friendCount(),
					response.postCount(), response.taggedCount(), response.commentedFeedCount(),
					response.likedFeedCount(), response.bookmarkedFeedCount());
		}
		if (body instanceof AccountInfoResponse response) {
			return new AccountInfoResponse(response.email(), response.nickname(),
					imageUrlResolver.toPublicUrl(response.profilePhoto()));
		}
		if (body instanceof SearchUserResponse response) {
			return new SearchUserResponse(response.userId(), response.nickname(),
					imageUrlResolver.toPublicUrl(response.profileImageUrl()), response.statusMessage(),
					response.friendCount(), response.badgeTier(), response.status());
		}
		if (body instanceof FriendResponse response) {
			return new FriendResponse(response.userId(), response.nickname(), response.badgeTier(),
					response.friendCount(), imageUrlResolver.toPublicUrl(response.profileImageUrl()), response.status());
		}
		if (body instanceof CommentResponse response) {
			return comment(response);
		}
		return body;
	}

	private boolean shouldUseThumbnails(ServerHttpRequest request) {
		if (request == null || request.getMethod() != HttpMethod.GET) {
			return false;
		}
		String path = request.getURI().getPath();
		return path.equals("/feeds")
				|| path.equals("/api/community/feeds")
				|| path.equals("/api/community/feeds/page")
				|| path.equals("/api/community/comments/me")
				|| path.equals("/api/community/search/feeds")
				|| path.startsWith("/community/contents/");
	}

	private boolean shouldUseWebpThumbnails(ServerHttpRequest request) {
		if (request == null) {
			return false;
		}
		String requestedFormat = request.getHeaders().getFirst("X-Neostride-Image-Format");
		if (requestedFormat != null && requestedFormat.trim().equalsIgnoreCase("webp")) {
			return true;
		}
		String query = request.getURI().getRawQuery();
		return query != null && query.toLowerCase(Locale.ROOT).matches(".*(^|&)(imageformat|image_format)=webp(&|$).*");
	}

	private FeedUploadResponse feed(FeedUploadResponse response, boolean thumbnails, boolean webpThumbnails) {
		return new FeedUploadResponse(response.feedId(), publicImage(response.profileImageUrl(), thumbnails, webpThumbnails),
				response.nickname(), response.badgeOwned(), response.badgeType(), response.createdAt(), response.title(),
				response.content(), response.taggedCount(), response.likeCount(), response.commentCount(), response.distance(),
				response.duration(), response.pace(), response.mapVisible(),
				publicImage(response.routeMapImageUri(), thumbnails, webpThumbnails),
				publicImages(response.imageUrls(), thumbnails, webpThumbnails), response.liked(), response.bookmarked(),
				response.commented(), response.tagged(), response.mine(), response.writerId());
	}

	private CommunityContentResponse communityContent(CommunityContentResponse response, boolean thumbnails, boolean webpThumbnails) {
		return new CommunityContentResponse(response.contentId(), response.userId(), response.nickname(), response.contentTitle(),
				response.contentText(), response.totalDistance(), response.duration(), response.pace(), response.createdAt(),
				publicImage(response.profileImageUrl(), thumbnails, webpThumbnails), publicImages(response.imageUrls(), thumbnails, webpThumbnails),
				response.likeCount(), response.commentCount(), response.tagCount(), response.liked(), response.bookmarked(),
				response.commented(), response.tagged(), response.badgeTier(), publicImage(response.routeMapUrl(), thumbnails, webpThumbnails));
	}

	private MyCommentActivityResponse myCommentActivity(MyCommentActivityResponse response, boolean thumbnails, boolean webpThumbnails) {
		return new MyCommentActivityResponse(response.contentType(), response.contentId(), response.writerId(),
				response.nickname(), publicImage(response.profileImageUrl(), thumbnails, webpThumbnails),
				response.badgeOwned(), response.badgeType(), response.category(), response.contentTitle(),
				response.contentText(), response.contentCreatedAt(), response.totalDistance(), response.duration(),
				response.pace(), response.gpsVisible(), publicImage(response.routeMapUrl(), thumbnails, webpThumbnails),
				publicImages(response.imageUrls(), thumbnails, webpThumbnails), response.likeCount(), response.commentCount(),
				response.tagCount(), response.liked(), response.bookmarked(), response.commented(), response.tagged(),
				response.contentMine(), response.commentId(), response.commentText(), response.commentCreatedAt(),
				response.commentMine());
	}

	private FeedDetailResponse feedDetail(FeedDetailResponse response) {
		return new FeedDetailResponse(response.feedId(), response.writerId(),
				imageUrlResolver.toPublicUrl(response.profileImageUrl()), response.nickname(), response.badgeOwned(),
				response.badgeType(), response.createdAt(), response.title(), response.content(),
				response.taggedCount(), response.likeCount(), response.commentCount(), response.liked(),
				response.bookmarked(), response.mine(), response.distance(), response.duration(), response.pace(),
				response.mapVisible(), imageUrlResolver.toPublicUrl(response.routeMapImageUri()),
				imageUrlResolver.toPublicUrls(response.imageUrls()), comments(response.comments()));
	}

	private TipUploadResponse tip(TipUploadResponse response, boolean thumbnails, boolean webpThumbnails) {
		return new TipUploadResponse(response.tipId(), response.writerId(), response.nickname(),
				publicImage(response.profileImageUrl(), thumbnails, webpThumbnails), response.badgeOwned(), response.badgeType(),
				response.category(), response.title(), response.content(), response.gpsVisible(),
				publicImage(response.routeMapImageUrl(), thumbnails, webpThumbnails), publicImages(response.imageUrls(), thumbnails, webpThumbnails),
				response.likeCount(), response.commentCount(), response.liked(), response.bookmarked(),
				response.commented(), response.mine(), response.createdAt());
	}

	private TipDetailResponse tipDetail(TipDetailResponse response) {
		return new TipDetailResponse(response.tipId(), response.writerId(), response.nickname(),
				imageUrlResolver.toPublicUrl(response.profileImageUrl()), response.badgeOwned(), response.badgeType(),
				response.category(), response.title(), response.content(), response.gpsVisible(),
				imageUrlResolver.toPublicUrl(response.routeMapImageUrl()), response.courseAddress(),
				imageUrlResolver.toPublicUrls(response.imageUrls()), response.likeCount(), response.commentCount(),
				response.liked(), response.bookmarked(), response.mine(), response.createdAt(), comments(response.comments()));
	}

	private List<FeedUploadResponse> feeds(List<FeedUploadResponse> feeds, boolean thumbnails, boolean webpThumbnails) {
		if (feeds == null || feeds.isEmpty()) {
			return List.of();
		}
		return feeds.stream().map(feed -> feed(feed, thumbnails, webpThumbnails)).toList();
	}

	private List<TipUploadResponse> tips(List<TipUploadResponse> tips, boolean thumbnails, boolean webpThumbnails) {
		if (tips == null || tips.isEmpty()) {
			return List.of();
		}
		return tips.stream().map(tip -> tip(tip, thumbnails, webpThumbnails)).toList();
	}

	private List<CommentResponse> comments(List<CommentResponse> comments) {
		if (comments == null || comments.isEmpty()) {
			return List.of();
		}
		return comments.stream().map(this::comment).toList();
	}

	private String publicImage(String value, boolean thumbnails, boolean webpThumbnails) {
		return thumbnails ? imageUrlResolver.toPublicThumbnailUrl(value, webpThumbnails) : imageUrlResolver.toPublicUrl(value);
	}

	private List<String> publicImages(List<String> values, boolean thumbnails, boolean webpThumbnails) {
		return thumbnails ? imageUrlResolver.toPublicThumbnailUrls(values, webpThumbnails) : imageUrlResolver.toPublicUrls(values);
	}

	private CommentResponse comment(CommentResponse response) {
		return new CommentResponse(response.commentId(), response.writerId(), response.nickname(),
				imageUrlResolver.toPublicUrl(response.profileImageUrl()), response.content(), response.createdAt(),
				response.badgeOwned(), response.badgeType(), response.mine());
	}
}
