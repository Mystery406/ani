package me.him188.ani.app.data.repositories

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.subject.EpisodeCollection
import me.him188.ani.app.data.subject.RatingCounts
import me.him188.ani.app.data.subject.RatingInfo
import me.him188.ani.app.data.subject.SelfRatingInfo
import me.him188.ani.app.data.subject.SubjectCollection
import me.him188.ani.app.data.subject.SubjectCollectionStats
import me.him188.ani.app.data.subject.SubjectInfo
import me.him188.ani.app.data.subject.SubjectManager
import me.him188.ani.app.data.subject.Tag
import me.him188.ani.app.data.subject.toInfoboxItem
import me.him188.ani.app.session.SessionManager
import me.him188.ani.datasources.api.paging.PageBasedPagedSource
import me.him188.ani.datasources.api.paging.Paged
import me.him188.ani.datasources.api.paging.PagedSource
import me.him188.ani.datasources.api.paging.processPagedResponse
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.datasources.bangumi.models.BangumiCount
import me.him188.ani.datasources.bangumi.models.BangumiRating
import me.him188.ani.datasources.bangumi.models.BangumiSubject
import me.him188.ani.datasources.bangumi.models.BangumiSubjectCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.models.BangumiUserEpisodeCollection
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollection
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollectionModifyPayload
import me.him188.ani.datasources.bangumi.processing.toCollectionType
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Performs network requests.
 * Use [SubjectManager] instead.
 */
interface BangumiSubjectRepository : Repository {
    suspend fun getSubject(id: Int): BangumiSubject?

    fun getSubjectCollections(
        username: String,
        subjectType: BangumiSubjectType? = null,
        subjectCollectionType: BangumiSubjectCollectionType? = null,
    ): PagedSource<BangumiUserSubjectCollection>

    /**
     * 获取用户对这个条目的收藏状态. flow 一定会 emit 至少一个值或抛出异常. 当用户没有收藏这个条目时 emit `null`.
     */
    fun subjectCollectionById(subjectId: Int): Flow<BangumiUserSubjectCollection?>

    fun subjectCollectionTypeById(subjectId: Int): Flow<UnifiedCollectionType>

    suspend fun patchSubjectCollection(subjectId: Int, payload: BangumiUserSubjectCollectionModifyPayload)
    suspend fun deleteSubjectCollection(subjectId: Int)
}

suspend inline fun BangumiSubjectRepository.setSubjectCollectionTypeOrDelete(
    subjectId: Int,
    type: BangumiSubjectCollectionType?
) {
    return if (type == null) {
        deleteSubjectCollection(subjectId)
    } else {
        patchSubjectCollection(subjectId, BangumiUserSubjectCollectionModifyPayload(type))
    }
}

class RemoteBangumiSubjectRepository : BangumiSubjectRepository, KoinComponent {
    private val client: BangumiClient by inject()
    private val sessionManager: SessionManager by inject()
    private val logger = logger(this::class)

    override suspend fun getSubject(id: Int): BangumiSubject? {
        return runCatching {
            client.api.getSubjectById(id).body()
        }.getOrNull()
    }

    override suspend fun patchSubjectCollection(subjectId: Int, payload: BangumiUserSubjectCollectionModifyPayload) {
        kotlin.runCatching {
            client.api.postUserCollection(subjectId, payload)
        }
    }

    override suspend fun deleteSubjectCollection(subjectId: Int) {
        kotlin.runCatching {
            // TODO:  deleteSubjectCollection
        }
    }

    override fun getSubjectCollections(
        username: String,
        subjectType: BangumiSubjectType?,
        subjectCollectionType: BangumiSubjectCollectionType?,
    ): PagedSource<BangumiUserSubjectCollection> {
        return PageBasedPagedSource { page ->
            try {
                val pageSize = 10
                withContext(Dispatchers.IO) {
                    client.api.getUserCollectionsByUsername(
                        username,
                        offset = page * pageSize, limit = pageSize,
                        subjectType = subjectType,
                        type = subjectCollectionType,
                    ).body().run {
                        total?.let { setTotalSize(it) }
                        Paged.processPagedResponse(total, pageSize, data)
                    }
                }
            } catch (e: ResponseException) {
                logger.warn("Exception in getCollections, page=$page", e)
                null
            }
        }
    }

    override fun subjectCollectionById(subjectId: Int): Flow<BangumiUserSubjectCollection?> {
        return flow {
            emit(
                try {
                    client.api.getUserCollection(sessionManager.username.first() ?: "-", subjectId).body()
                } catch (e: ResponseException) {
                    if (e.response.status == HttpStatusCode.NotFound) {
                        null
                    } else {
                        throw e
                    }
                },
            )
        }
    }

    override fun subjectCollectionTypeById(subjectId: Int): Flow<UnifiedCollectionType> {
        return flow {
            emit(
                try {
                    val username = sessionManager.username.first() ?: "-"
                    client.api.getUserCollection(username, subjectId).body().type.toCollectionType()
                } catch (e: ResponseException) {
                    if (e.response.status == HttpStatusCode.NotFound) {
                        UnifiedCollectionType.NOT_COLLECTED
                    } else {
                        throw e
                    }
                },
            )
        }
    }
}

fun BangumiUserSubjectCollection.toSubjectCollectionItem(
    subject: BangumiSubject,
    episodes: List<BangumiUserEpisodeCollection>,
): SubjectCollection = SubjectCollection(
    info = subject.toSubjectInfo(),
    episodes = episodes.map { it.toEpisodeCollection() },
    collectionType = type.toCollectionType(),
    selfRatingInfo = toSelfRatingInfo(),
)

fun BangumiUserSubjectCollection.toSelfRatingInfo(): SelfRatingInfo {
    return SelfRatingInfo(
        score = rate,
        comment = comment.takeUnless { it.isNullOrBlank() },
        tags = tags,
        isPrivate = private,
    )
}

fun BangumiSubject.toSubjectInfo(): SubjectInfo {
    return SubjectInfo(
        id = id,
        name = name,
        nameCn = nameCn,
        summary = this.summary,
        nsfw = this.nsfw,
        locked = this.locked,
        platform = this.platform,
        volumes = this.volumes,
        eps = this.eps,
        totalEpisodes = this.totalEpisodes,
        date = this.date,
        tags = this.tags.map { Tag(it.name, it.count) },
        infobox = this.infobox?.map { it.toInfoboxItem() }.orEmpty(),
        imageCommon = this.images.common,
        imageLarge = this.images.large,
        collection = this.collection.run {
            SubjectCollectionStats(
                wish = wish,
                doing = doing,
                done = collect,
                onHold = onHold,
                dropped = dropped,
            )
        },
        ratingInfo = this.rating.toRatingInfo(),
    )
}


private fun BangumiRating.toRatingInfo(): RatingInfo = RatingInfo(
    rank = rank,
    total = total,
    count = count.toRatingCounts(),
    score = score.toString(),
)

private fun BangumiCount.toRatingCounts() = RatingCounts(
    intArrayOf(
        _1 ?: 0,
        _2 ?: 0,
        _3 ?: 0,
        _4 ?: 0,
        _5 ?: 0,
        _6 ?: 0,
        _7 ?: 0,
        _8 ?: 0,
        _9 ?: 0,
        _10 ?: 0,
    ),
)

fun BangumiUserEpisodeCollection.toEpisodeCollection(): EpisodeCollection {
    return EpisodeCollection(
        episodeInfo = episode.toEpisodeInfo(),
        collectionType = type.toCollectionType(),
    )
}


private class LruCache<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(maxSize + 1, 1f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean {
        return size > maxSize
    }
}
