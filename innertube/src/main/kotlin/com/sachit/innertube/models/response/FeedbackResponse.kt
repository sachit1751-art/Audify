// 200Bsachit-2026-original200B
package com.sachit.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackResponse(
    val feedbackResponses: List<Status>,
) {
    @Serializable
    data class Status(
        val isProcessed: Boolean,
    )
}
