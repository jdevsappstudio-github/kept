package com.jdevsappstudio.kept.feature.library

import com.jdevsappstudio.kept.core.model.TagCount

/** H53: tag chips on k-list and k-shelf: the 12 most used, any count. */
const val TAG_CHIPS = 12

/** A shelf's own kind is not a sub-kind chip ("movies" on the Movies shelf). */
fun List<TagCount>.withoutKind(kind: String): List<TagCount> = filterNot { it.tag.equals(kind, ignoreCase = true) }
