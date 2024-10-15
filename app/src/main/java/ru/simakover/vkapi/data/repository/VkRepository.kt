package ru.simakover.vkapi.data.repository

import android.app.Application
import com.vk.api.sdk.VKPreferencesKeyValueStorage
import com.vk.api.sdk.auth.VKAccessToken
import ru.simakover.vkapi.data.mapper.VkMapper
import ru.simakover.vkapi.data.network.ApiFactory
import ru.simakover.vkapi.domain.models.FeedPost
import ru.simakover.vkapi.domain.models.PostComment
import ru.simakover.vkapi.domain.models.StatisticItem
import ru.simakover.vkapi.domain.models.StatisticType

class VkRepository(application: Application) {

    private val storage = VKPreferencesKeyValueStorage(application)
    private val token = VKAccessToken.restore(storage)

    private val apiService = ApiFactory.apiService
    private val mapper = VkMapper()

    private val _feedPosts = mutableListOf<FeedPost>()
    val feedPosts: List<FeedPost>
        get() = _feedPosts.toList()

    private var nextFrom: String? = null

    private fun getAccessToken(): String {
        return token?.accessToken ?: throw IllegalStateException("Token is null")
    }

    suspend fun loadRecommendations(): List<FeedPost> {
        val startFrom = nextFrom

        if (startFrom == null && feedPosts.isNotEmpty()) return feedPosts

        val response = if (startFrom == null) {
            apiService.loadRecommendations(getAccessToken())
        } else {
            apiService.loadRecommendations(getAccessToken(), startFrom)
        }

        nextFrom = response.newsFeedContent.nextFrom

        val posts = mapper.mapResponseToPosts(response)
        _feedPosts.addAll(posts)
        return feedPosts
    }

    suspend fun changeLikeStatus(post: FeedPost) {
        val response = if (!post.isLiked) {
            apiService.addLike(
                token = getAccessToken(),
                ownerId = post.communityId,
                postId = post.id
            )
        } else {
            apiService.deleteLike(
                token = getAccessToken(),
                ownerId = post.communityId,
                postId = post.id
            )
        }

        val newLikesCount = response.likes.count

        val newStatistics = post.statistics.toMutableList().apply {
            removeIf { it.type == StatisticType.LIKES }
            add(StatisticItem(type = StatisticType.LIKES, count = newLikesCount))
        }

        val newPost = post.copy(
            statistics = newStatistics,
            isLiked = !post.isLiked
        )
        val postIndex = _feedPosts.indexOf(post)

        _feedPosts[postIndex] = newPost
    }

    suspend fun ignoreRecommendation(post: FeedPost) {
        apiService.ignoreRecommendation(
            token = getAccessToken(),
            ownerId = post.communityId,
            postId = post.id
        )
        _feedPosts.remove(post)
    }

    private val _postComments = mutableListOf<PostComment>()
    val postComments: List<PostComment>
        get() = _postComments.toList()

    suspend fun loadPostComments(ownerId: Long, postId: Long): List<PostComment> {

        val response = apiService.loadComments(
            token = getAccessToken(),
            ownerId = ownerId,
            postId = postId
        )

        val comments = mapper.mapResponseToComments(response)
        _postComments.addAll(comments)
        return postComments
    }
}