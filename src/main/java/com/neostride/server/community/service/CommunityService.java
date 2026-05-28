package com.neostride.server.community.service;

import com.neostride.server.auth.exception.DuplicateUserFieldException;
import com.neostride.server.community.dto.*;
import com.neostride.server.community.repository.CommunityRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityService {
	private static final int MAX_SEARCH_KEYWORD_LENGTH = 50;
	private static final Pattern SQL_INJECTION_META_PATTERN = Pattern.compile("('|\"|;|--|/\\*|\\*/|#|`)");
	private static final Pattern SQL_INJECTION_KEYWORD_PATTERN = Pattern.compile("\\b(select|insert|update|delete|drop|alter|truncate|union|or|and|exec|execute)\\b", Pattern.CASE_INSENSITIVE);
	private final CommunityRepository repository;
	public CommunityService(CommunityRepository repository) { this.repository = repository; }
	public UserProfileResponse getUserProfile(long userId) { validatePositive(userId, "user_id"); return repository.getUserProfile(userId); }
	public UserProfileResponse getUserProfile(Long viewerUserId, long targetUserId) { validatePositive(targetUserId, "user_id"); if (viewerUserId != null) validatePositive(viewerUserId, "viewer_user_id"); return repository.getUserProfile(viewerUserId, targetUserId); }
	public AccountInfoResponse getAccountInfo(long userId) { validatePositive(userId, "user_id"); return repository.getAccountInfo(userId); }
	@Transactional
	public void updateStatusMessage(long userId, Map<String, String> body) { validatePositive(userId, "user_id"); requireBody(body); repository.updateStatusMessage(userId, body.get("status_message")); }
	@Transactional
	public void updateNickname(long userId, Map<String, String> body) {
		validatePositive(userId, "user_id");
		requireBody(body);
		String nickname = body.get("nickname");
		if (repository.existsByCommunityProfileNameExcludingUserId(nickname, userId)) {
			throw DuplicateUserFieldException.nickname();
		}
		repository.updateNickname(userId, nickname);
	}
	@Transactional
	public void deleteAccount(long userId) { validatePositive(userId, "user_id"); repository.deleteAccount(userId); }
	@Transactional
	public void updateProfileImage(long userId, String profileImageUrl) { validatePositive(userId, "user_id"); repository.updateProfileImage(userId, profileImageUrl); }
	@Transactional
	public void deleteProfileImage(long userId) { validatePositive(userId, "user_id"); repository.deleteProfileImage(userId); }
	public List<CommunityContentResponse> getMyFeeds(long userId) { validatePositive(userId, "user_id"); return repository.myFeeds(userId); }
	public List<CommunityContentResponse> getTaggedFeeds(long userId) { validatePositive(userId, "user_id"); return repository.taggedFeeds(userId); }
	public List<CommunityContentResponse> getCommentedFeeds(long userId) { validatePositive(userId, "user_id"); return repository.interactedFeeds(userId, "COMMENT"); }
	public List<CommunityContentResponse> getLikedFeeds(long userId) { validatePositive(userId, "user_id"); return repository.interactedFeeds(userId, "LIKE"); }
	public List<CommunityContentResponse> getBookmarkedFeeds(long userId) { validatePositive(userId, "user_id"); return repository.interactedFeeds(userId, "BOOKMARK"); }
	public List<CommunityContentResponse> getUserFeeds(long userId) { return getUserFeeds(null, userId); }
	public List<CommunityContentResponse> getUserFeeds(Long viewerUserId, long userId) { validatePositive(userId, "user_id"); if (viewerUserId != null) validatePositive(viewerUserId, "viewer_user_id"); return repository.feedsByUserForViewer(viewerUserId, userId); }
	@Transactional
	public Map<String, String> toggleBookmark(long userId, long contentId) { validatePositive(userId, "user_id"); validatePositive(contentId, "content_id"); boolean bookmarked = repository.toggleBookmark(userId, contentId); return Map.of("status", "success", "bookmarked", String.valueOf(bookmarked)); }
	public BadgeDetailResponse getBadgeDetail(long userId) { validatePositive(userId, "user_id"); return repository.getBadgeDetail(userId); }
	public List<FriendResponse> getFriendList(long userId, String status) { validatePositive(userId, "user_id"); return repository.getFriendList(userId, status); }
	public List<FriendResponse> getUserFriendList(long userId) { return getUserFriendList(null, userId); }
	public List<FriendResponse> getUserFriendList(Long viewerUserId, long userId) { validatePositive(userId, "user_id"); if (viewerUserId == null) return repository.getFriendList(userId, "friends"); validatePositive(viewerUserId, "viewer_user_id"); return repository.getUserFriendList(viewerUserId, userId); }
	@Transactional
	public Map<String, String> updateRelationship(long userId, FriendRequest request) { validatePositive(userId, "user_id"); requireBody(request); repository.updateRelationship(userId, request); return Map.of("status", "success", "message", "관계 상태가 변경되었습니다."); }
	@Transactional
	public FeedUploadResponse uploadFeed(long userId, FeedUploadRequest request) { validatePositive(userId, "user_id"); requireBody(request); long id = repository.insertFeed(userId, request); return repository.findFeed(id); }
	public FeedDetailResponse getFeedDetail(long userId, long feedId) { validatePositive(userId, "user_id"); validatePositive(feedId, "feed_id"); return repository.findFeedDetail(userId, feedId); }
	@Transactional
	public Map<String, String> toggleFeedLike(long userId, long feedId) { validatePositive(userId, "user_id"); validatePositive(feedId, "feed_id"); return interactionResult("liked", repository.toggleInteraction(userId, feedId, "LIKE")); }
	@Transactional
	public Map<String, String> toggleFeedBookmark(long userId, long feedId) { validatePositive(userId, "user_id"); validatePositive(feedId, "feed_id"); return interactionResult("bookmarked", repository.toggleInteraction(userId, feedId, "BOOKMARK")); }
	@Transactional
	public CommentResponse createFeedComment(long userId, long feedId, CommentRequest request) { validatePositive(userId, "user_id"); validatePositive(feedId, "feed_id"); requireBody(request); return repository.createComment(userId, feedId, request); }
	@Transactional
	public FeedUploadResponse updateFeed(long userId, long feedId, FeedUploadRequest request) { validatePositive(userId, "user_id"); validatePositive(feedId, "feed_id"); requireBody(request); repository.updateFeed(userId, feedId, request); return repository.findFeed(feedId); }
	@Transactional
	public void deleteFeed(long userId, long feedId) { validatePositive(userId, "user_id"); validatePositive(feedId, "feed_id"); repository.deleteContent(userId, feedId, "POST"); }
	@Transactional
	public CommentResponse updateFeedComment(long userId, long feedId, long commentId, CommentRequest request) { validatePositive(userId, "user_id"); validatePositive(feedId, "feed_id"); validatePositive(commentId, "comment_id"); requireBody(request); return repository.updateComment(userId, feedId, commentId, request); }
	@Transactional
	public void deleteFeedComment(long userId, long feedId, long commentId) { validatePositive(userId, "user_id"); validatePositive(feedId, "feed_id"); validatePositive(commentId, "comment_id"); repository.deleteComment(userId, feedId, commentId); }
	public List<FriendResponse> getTaggedUsers(long feedId) { validatePositive(feedId, "feed_id"); return repository.getTaggedUsers(feedId); }
	public List<FeedUploadResponse> getFeedList() { return getFeedList(null); }
	public List<FeedUploadResponse> getFeedList(Long viewerUserId) { return repository.listFeeds(viewerUserId); }
	public List<FeedUploadResponse> getFeedList(Long viewerUserId, String cursorCreatedAt, Long cursorId, Integer limit) {
		if (limit == null && isBlank(cursorCreatedAt) && cursorId == null) {
			return getFeedList(viewerUserId);
		}
		return getFeedPage(viewerUserId, cursorCreatedAt, cursorId, limit == null ? 20 : limit).items();
	}
	public FeedPageResponse getFeedPage(Long viewerUserId, String cursorCreatedAt, Long cursorId, int limit) {
		validateLimit(limit);
		if (viewerUserId != null) validatePositive(viewerUserId, "viewer_user_id");
		LocalDateTime parsedCursorCreatedAt = parseFeedCursor(cursorCreatedAt, cursorId);
		List<FeedUploadResponse> rows = repository.listFeedsPage(viewerUserId, parsedCursorCreatedAt, cursorId, limit + 1);
		boolean hasMore = rows.size() > limit;
		List<FeedUploadResponse> items = hasMore ? rows.subList(0, limit) : rows;
		FeedCursorResponse nextCursor = null;
		if (hasMore && !items.isEmpty()) {
			FeedUploadResponse last = items.getLast();
			nextCursor = new FeedCursorResponse(last.createdAt(), last.feedId());
		}
		return new FeedPageResponse(items, nextCursor, hasMore);
	}

	public CommentPageResponse getFeedComments(long userId, long feedId, String cursorCreatedAt, Long cursorId, int limit) {
		validatePositive(userId, "user_id");
		validatePositive(feedId, "feed_id");
		return getComments(userId, feedId, cursorCreatedAt, cursorId, limit);
	}

	public CommentPageResponse getTipComments(long userId, long tipId, String cursorCreatedAt, Long cursorId, int limit) {
		validatePositive(userId, "user_id");
		validatePositive(tipId, "tip_id");
		return getComments(userId, tipId, cursorCreatedAt, cursorId, limit);
	}

	@Transactional
	public TipUploadResponse uploadTip(long userId, TipUploadRequest request) { validatePositive(userId, "user_id"); requireBody(request); long id = repository.insertTip(userId, request); return repository.findTip(id); }
	public TipListResponse getTips(Long viewerUserId) { return new TipListResponse(viewerUserId == null ? repository.listTips() : repository.listTips(viewerUserId)); }
	public List<TipUploadResponse> getMyTips(long userId) { validatePositive(userId, "user_id"); return repository.listTipsByUser(userId); }
	public List<TipUploadResponse> getUserTips(long userId) { return getUserTips(null, userId); }
	public List<TipUploadResponse> getUserTips(Long viewerUserId, long userId) { validatePositive(userId, "user_id"); if (viewerUserId != null) validatePositive(viewerUserId, "viewer_user_id"); return repository.listTipsByUser(viewerUserId, userId); }
	public List<TipUploadResponse> getLikedTips(long userId) { validatePositive(userId, "user_id"); return repository.listTipsLikedByUser(userId); }
	public List<TipUploadResponse> getBookmarkedTips(long userId) { validatePositive(userId, "user_id"); return repository.listTipsBookmarkedByUser(userId); }
	public List<TipUploadResponse> getCommentedTips(long userId) { validatePositive(userId, "user_id"); return repository.listTipsCommentedByUser(userId); }
	public TipDetailResponse getTipDetail(long userId, long tipId) { validatePositive(userId, "user_id"); validatePositive(tipId, "tip_id"); return repository.findTipDetail(userId, tipId); }
	@Transactional
	public Map<String, String> toggleTipLike(long userId, long tipId) { validatePositive(userId, "user_id"); validatePositive(tipId, "tip_id"); return interactionResult("liked", repository.toggleInteraction(userId, tipId, "LIKE")); }
	@Transactional
	public Map<String, String> toggleTipBookmark(long userId, long tipId) { validatePositive(userId, "user_id"); validatePositive(tipId, "tip_id"); return interactionResult("bookmarked", repository.toggleInteraction(userId, tipId, "BOOKMARK")); }
	@Transactional
	public CommentResponse createTipComment(long userId, long tipId, CommentRequest request) { validatePositive(userId, "user_id"); validatePositive(tipId, "tip_id"); requireBody(request); return repository.createComment(userId, tipId, request); }
	@Transactional
	public TipUploadResponse updateTip(long userId, long tipId, TipUploadRequest request) { validatePositive(userId, "user_id"); validatePositive(tipId, "tip_id"); requireBody(request); repository.updateTip(userId, tipId, request); return repository.findTip(tipId); }
	@Transactional
	public void deleteTip(long userId, long tipId) { validatePositive(userId, "user_id"); validatePositive(tipId, "tip_id"); repository.deleteContent(userId, tipId, "TIP"); }
	@Transactional
	public CommentResponse updateTipComment(long userId, long tipId, long commentId, CommentRequest request) { validatePositive(userId, "user_id"); validatePositive(tipId, "tip_id"); validatePositive(commentId, "comment_id"); requireBody(request); return repository.updateComment(userId, tipId, commentId, request); }
	@Transactional
	public void deleteTipComment(long userId, long tipId, long commentId) { validatePositive(userId, "user_id"); validatePositive(tipId, "tip_id"); validatePositive(commentId, "comment_id"); repository.deleteComment(userId, tipId, commentId); }
	public List<FeedUploadResponse> searchFeeds(String keyword, int page, int size) { validatePage(page, size); return repository.searchFeeds(keyword, page, size); }
	public List<FeedUploadResponse> searchFeeds(Long viewerUserId, String keyword, int page, int size) { if (viewerUserId != null) validatePositive(viewerUserId, "viewer_user_id"); validatePage(page, size); return repository.searchFeeds(viewerUserId, keyword, page, size); }
	public List<TipUploadResponse> searchTips(String keyword, String category, int page, int size) { validatePage(page, size); return repository.searchTips(keyword, category, page, size); }
	public List<TipUploadResponse> searchTips(Long viewerUserId, String keyword, String category, int page, int size) { if (viewerUserId != null) validatePositive(viewerUserId, "viewer_user_id"); validatePage(page, size); return repository.searchTips(viewerUserId, keyword, category, page, size); }
	public List<SearchUserResponse> searchProfiles(String keyword, int page, int size) {
		validatePage(page, size);
		String normalizedKeyword = validateSearchKeyword(keyword);
		return normalizedKeyword == null ? repository.getTopProfiles(page, size) : repository.searchProfiles(normalizedKeyword, page, size);
	}
	public List<SearchUserResponse> searchProfiles(Long viewerUserId, String keyword, int page, int size) {
		if (viewerUserId != null) validatePositive(viewerUserId, "viewer_user_id");
		validatePage(page, size);
		String normalizedKeyword = validateSearchKeyword(keyword);
		return normalizedKeyword == null ? repository.getTopProfiles(viewerUserId, page, size) : repository.searchProfiles(viewerUserId, normalizedKeyword, page, size);
	}
	public List<SearchUserResponse> searchFriends(long userId, String keyword) { validatePositive(userId, "user_id"); return repository.searchFriends(userId, keyword); }
	public List<SearchUserResponse> getTopProfiles(int page, int size) { validatePage(page, size); return repository.getTopProfiles(page, size); }
	public List<SearchUserResponse> getTopProfiles(Long viewerUserId, int page, int size) { if (viewerUserId != null) validatePositive(viewerUserId, "viewer_user_id"); validatePage(page, size); return repository.getTopProfiles(viewerUserId, page, size); }
	public List<SearchUserResponse> getMyFriends(long userId) { validatePositive(userId, "user_id"); return repository.getMyFriends(userId); }


	private CommentPageResponse getComments(long userId, long contentId, String cursorCreatedAt, Long cursorId, int limit) {
		validateLimit(limit);
		LocalDateTime parsedCursorCreatedAt = parseFeedCursor(cursorCreatedAt, cursorId);
		List<CommentResponse> rows = repository.commentsPage(userId, contentId, parsedCursorCreatedAt, cursorId, limit + 1);
		boolean hasMore = rows.size() > limit;
		List<CommentResponse> items = hasMore ? rows.subList(0, limit) : rows;
		CommentCursorResponse nextCursor = null;
		if (hasMore && !items.isEmpty()) {
			CommentResponse last = items.getLast();
			nextCursor = new CommentCursorResponse(last.createdAt(), last.commentId());
		}
		return new CommentPageResponse(items, nextCursor, hasMore);
	}

	private Map<String, String> interactionResult(String key, boolean enabled) {
		return Map.of("status", "success", key, String.valueOf(enabled));
	}

	private void requireBody(Object body) {
		if (body == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
	}

	private String validateSearchKeyword(String keyword) {
		if (isBlank(keyword)) {
			return null;
		}
		String normalized = keyword.trim();
		if (normalized.length() > MAX_SEARCH_KEYWORD_LENGTH) {
			throw new IllegalArgumentException("검색어는 50자 이하이어야 합니다.");
		}
		String lower = normalized.toLowerCase(Locale.ROOT);
		if (SQL_INJECTION_META_PATTERN.matcher(lower).find() || SQL_INJECTION_KEYWORD_PATTERN.matcher(lower).find()) {
			throw new IllegalArgumentException("검색어에 허용되지 않는 문자가 포함되어 있습니다.");
		}
		return normalized;
	}

	private LocalDateTime parseFeedCursor(String cursorCreatedAt, Long cursorId) {
		boolean hasCreatedAt = !isBlank(cursorCreatedAt);
		boolean hasId = cursorId != null;
		if (!hasCreatedAt && !hasId) {
			return null;
		}
		if (!hasCreatedAt || !hasId) {
			throw new IllegalArgumentException("cursorCreatedAt과 cursorId는 함께 전달해야 합니다.");
		}
		validatePositive(cursorId, "cursor_id");
		try {
			return LocalDateTime.parse(cursorCreatedAt.trim());
		} catch (DateTimeParseException exception) {
			throw new IllegalArgumentException("cursorCreatedAt은 ISO_LOCAL_DATE_TIME 형식이어야 합니다.", exception);
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private void validatePositive(long value, String fieldName) {
		if (value <= 0) {
			throw new IllegalArgumentException(fieldName + "는 1 이상의 값이어야 합니다.");
		}
	}

	private void validateLimit(int limit) {
		if (limit <= 0 || limit > 100) {
			throw new IllegalArgumentException("limit은 1 이상 100 이하이어야 합니다.");
		}
	}

	private void validatePage(int page, int size) {
		if (page < 0) {
			throw new IllegalArgumentException("page는 0 이상의 값이어야 합니다.");
		}
		if (size <= 0 || size > 100) {
			throw new IllegalArgumentException("size는 1 이상 100 이하이어야 합니다.");
		}
	}
}
