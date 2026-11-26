package com.jdevsappstudio.kept.navigation

import kotlinx.serialization.Serializable

// Type-safe routes. Property names are the SavedStateHandle keys the ViewModels read (their ARG_* constants),
// so keep them in sync: SaveListViewModel.ARG_TYPE = "type", ShelfViewModel.ARG_KIND = "kind",
// SaveDetailViewModel.ARG_ID = "id", CollectionViewModel.ARG_ID = "collectionId", SearchViewModel ARG_QUERY = "q",
// ARG_TYPE = "type", ARG_PICK_INTO = "pickInto", AskViewModel.ARG_QUESTION = "question",
// CollectionNameViewModel.ARG_RENAME_ID = "renameCollectionId", EditSaveViewModel.ARG_IDS = "ids".

// Bottom tabs
@Serializable data object HomeRoute
@Serializable data class SearchRoute(val q: String? = null, val type: String? = null, val pickInto: Long? = null)
@Serializable data class AskRoute(val question: String? = null)

// Library
@Serializable data class SaveListRoute(val type: String? = null)
@Serializable data class ShelfRoute(val kind: String)
@Serializable data object ShelvesRoute
@Serializable data class SaveDetailRoute(val id: Long)
@Serializable data class EditSaveRoute(val ids: LongArray)

// Collections
@Serializable data class CollectionRoute(val collectionId: Long)
@Serializable data class CollectionNameRoute(val renameCollectionId: Long? = null)

// Capture, settings, states
/** k-addmenu; [pick] = open the photo picker straight away (k-empty), no sheet. */
@Serializable data class AddRoute(val pick: Boolean = false)
/** k-reading: ReadingViewModel.ARG_SINCE = "since" (H63). */
@Serializable data class ReadingRoute(val since: Long)
@Serializable data object HowToRoute
@Serializable data object SettingsRoute
@Serializable data object AppearanceRoute
@Serializable data object PrivacyRoute
/** k-unsupported: no Nano on this phone (H69). */
@Serializable data object UnsupportedRoute
@Serializable data object AboutRoute
@Serializable data object LicencesRoute
@Serializable data object NeedLookRoute
/** k-cloud-out: CloudOutViewModel.ARG_METER = "meter" (a CloudMeter name; reads come from Needs a look). */
@Serializable data class CloudOutRoute(val meter: String = "ANSWERS")
/** k-sent, opened at one request from an answer's Cloud chip (null = the whole log, from Privacy). */
@Serializable data class SentRoute(val sentId: Long? = null)
