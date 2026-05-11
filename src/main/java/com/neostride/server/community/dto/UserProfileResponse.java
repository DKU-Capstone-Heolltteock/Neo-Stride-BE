package com.neostride.server.community.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserProfileResponse(
		@JsonProperty("community_profile_name") String nickname,
		@JsonProperty("profile_photo") String profilePhoto,
		@JsonProperty("status_message") String statusMessage,
		@JsonProperty("friend_count") Integer friendCount,
		@JsonProperty("post_count") Integer postCount,
		@JsonProperty("tagged_count") Integer taggedCount,
		@JsonProperty("commented_feed_count") Integer commentedFeedCount,
		@JsonProperty("liked_feed_count") Integer likedFeedCount,
		@JsonProperty("bookmarked_feed_count") Integer bookmarkedFeedCount
) {}
