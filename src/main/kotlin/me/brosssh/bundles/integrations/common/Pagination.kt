package me.brosssh.bundles.integrations.common

/** Returns the URL whose RFC 8288 link relation is `next`, if present. */
internal fun nextPageUrl(linkHeader: String?): String? = linkHeader
    ?.split(',')
    ?.asSequence()
    ?.map { it.trim() }
    ?.firstOrNull { it.contains("""rel="next"""") }
    ?.substringAfter('<')
    ?.substringBefore('>')
