package com.neostride.server.config;

import com.neostride.server.community.dto.AccountInfoResponse;
import com.neostride.server.community.dto.CommentResponse;
import com.neostride.server.community.dto.CommunityContentResponse;
import com.neostride.server.community.dto.FeedDetailResponse;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.FriendResponse;
import com.neostride.server.community.dto.SearchUserResponse;
import com.neostride.server.community.dto.TipDetailResponse;
import com.neostride.server.community.dto.TipListResponse;
import com.neostride.server.community.dto.TipUploadResponse;
import com.neostride.server.community.dto.UserProfileResponse;
import com.neostride.server.storage.ImageUrlResolver;
import java.util.List;
import org.springframework.core.MethodParameter;
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
		return rewrite(body);
	}

	private Object rewrite(Object body) {
		if (body == null) {
			return null;
		}
		if (body instanceof List<?> values) {
			return values.stream().map(this::rewrite).toList();
		}
		if (body instanceof TipListResponse response) {
			return new TipListResponse(tips(response.tips()));
		}
		if (body instanceof FeedUploadResponse response) {
			return feed(response);
		}
		if (body instanceof CommunityContentResponse response) {
			return communityContent(response);
		}
		if (body instanceof FeedDetailResponse response) {
			return feedDetail(response);
		}
		if (body instanceof TipUploadResponse response) {
			return tip(response);
		}
		if (body instanceof TipDetailResponse response) {
			return tipDetail(response);
		}
		if (body instanceof UserProfileResponse response) {
			return new UserProfileResponse(response.nickname(), imageUrlResolver.toPublicUrl(response.profilePhoto()),
					response.statusMessage(), response.friend(), response.blocked(), response.friendCount(),
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

	private FeedUploadResponse feed(FeedUploadResponse response) {
		return new FeedUploadResponse(response.feedId(), imageUrlResolver.toPublicUrl(response.profileImageUrl()),
				response.nickname(), response.createdAt(), response.title(), response.content(), response.taggedCount(),
				response.likeCount(), response.commentCount(), response.distance(), response.duration(), response.pace(),
				response.mapVisible(), imageUrlResolver.toPublicUrl(response.routeMapImageUri()),
				imageUrlResolver.toPublicUrls(response.imageUrls()), response.mine(), response.writerId());
	}

	private CommunityContentResponse communityContent(CommunityContentResponse response) {
		return new CommunityContentResponse(response.contentId(), response.contentTitle(), response.contentText(),
				response.totalDistance(), response.duration(), response.pace(), response.createdAt(),
				imageUrlResolver.toPublicUrl(response.profileImageUrl()),
				imageUrlResolver.toPublicUrls(response.imageUrls()));
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

	private TipUploadResponse tip(TipUploadResponse response) {
		return new TipUploadResponse(response.tipId(), response.writerId(), response.nickname(),
				imageUrlResolver.toPublicUrl(response.profileImageUrl()), response.badgeOwned(), response.badgeType(),
				response.category(), response.title(), response.content(), response.gpsVisible(),
				imageUrlResolver.toPublicUrl(response.routeMapImageUrl()),
				imageUrlResolver.toPublicUrls(response.imageUrls()), response.likeCount(), response.commentCount(),
				response.liked(), response.bookmarked(), response.commented(), response.mine(), response.createdAt());
	}

	private TipDetailResponse tipDetail(TipDetailResponse response) {
		return new TipDetailResponse(response.tipId(), response.writerId(), response.nickname(),
				imageUrlResolver.toPublicUrl(response.profileImageUrl()), response.badgeOwned(), response.badgeType(),
				response.category(), response.title(), response.content(), response.gpsVisible(),
				imageUrlResolver.toPublicUrl(response.routeMapImageUrl()), response.courseAddress(),
				imageUrlResolver.toPublicUrls(response.imageUrls()), response.likeCount(), response.commentCount(),
				response.liked(), response.bookmarked(), response.mine(), response.createdAt(), comments(response.comments()));
	}

	private List<TipUploadResponse> tips(List<TipUploadResponse> tips) {
		if (tips == null || tips.isEmpty()) {
			return List.of();
		}
		return tips.stream().map(this::tip).toList();
	}

	private List<CommentResponse> comments(List<CommentResponse> comments) {
		if (comments == null || comments.isEmpty()) {
			return List.of();
		}
		return comments.stream().map(this::comment).toList();
	}

	private CommentResponse comment(CommentResponse response) {
		return new CommentResponse(response.commentId(), response.writerId(), response.nickname(),
				imageUrlResolver.toPublicUrl(response.profileImageUrl()), response.content(), response.createdAt(),
				response.badgeOwned(), response.badgeType(), response.mine());
	}
}
