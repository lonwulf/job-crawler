package org.lonwulf.labs.jobcrawler.data.response

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchDTO(val items: List<Item>?) {

    @JsonClass(generateAdapter = true)
    data class Item(val title: String, val link: String, val snippet: String?, val pageMap: PageMap?) {
        @JsonClass(generateAdapter = true)
        data class PageMap(val metaTags: List<Map<String, String>>)
    }
}
