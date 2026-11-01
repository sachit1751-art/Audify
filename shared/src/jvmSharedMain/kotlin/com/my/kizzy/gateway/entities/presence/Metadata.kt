// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.my.kizzy.gateway.entities.presence

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Metadata(
    @SerialName("button_urls")
    val buttonUrls: List<String?>?,
)
