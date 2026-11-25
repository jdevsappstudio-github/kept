package com.jdevsappstudio.kept.core.presentation

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/** A string that may come from resources, so ViewModels never hold a Context. */
sealed interface UiText {
    data class Dynamic(val value: String) : UiText
    class Res(@StringRes val id: Int, vararg val args: Any) : UiText
    class Plural(@PluralsRes val id: Int, val count: Int, vararg val args: Any) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Dynamic -> value
        is Res -> stringResource(id, *args)
        is Plural -> pluralStringResource(id, count, *args)
    }
}
