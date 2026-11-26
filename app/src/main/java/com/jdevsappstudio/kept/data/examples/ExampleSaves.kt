package com.jdevsappstudio.kept.data.examples

import android.content.Context
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.data.ai.CardDraft
import com.jdevsappstudio.kept.data.db.SaveDao
import com.jdevsappstudio.kept.data.db.SaveEntity
import com.jdevsappstudio.kept.data.files.SaveFiles
import com.jdevsappstudio.kept.data.saves.SaveLocalDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * H71 'Try 3 examples' (k-ob-ready, k-empty): three marked saves, already read, each showing a different part of Kept.
 * Clear goes through the normal soft delete (snackbar Undo); all of them go for good at the 5th real save
 * ([com.jdevsappstudio.kept.data.saves.RoomSaveDataSource.add]).
 */
interface ExampleSaves {
    /** Live example saves, oldest first. */
    fun observeIds(): Flow<List<Long>>

    /** Adds the three (once: live examples are returned as they are). */
    suspend fun add(): List<Long>

    companion object {
        /** Tests and previews: no examples. */
        val NONE: ExampleSaves = object : ExampleSaves {
            override fun observeIds(): Flow<List<Long>> = flowOf(emptyList())
            override suspend fun add(): List<Long> = emptyList()
        }
    }
}

/** One example: its drawn screenshot in `assets/examples/` and the card a read would have made. */
data class ExampleSave(val asset: String, val card: CardDraft)

/** The three, oldest first, so the recipe ends up on top of Home. Drawn by `tools/docbuild/examples_art.py`. */
object ExampleContent {
    /** H71: they leave on their own once the library has this many real saves. */
    const val GONE_AT_REAL_SAVES = 5

    val ALL: List<ExampleSave> = listOf(
        ExampleSave(
            asset = "movies.webp",
            card = CardDraft(
                title = "5 thrillers to watch",
                type = "movie_show",
                kind = "movies",
                tags = listOf("thriller", "watchlist", "hindi"),
                items = listOf("Andhadhun", "Drishyam", "Kahaani", "Talvar", "Raat Akeli Hai"),
                place = "", date = "", link = "",
                summary = "A list of Hindi thrillers with big twists, for a weekend in.",
            ),
        ),
        ExampleSave(
            asset = "place.webp",
            card = CardDraft(
                title = "Fort Aguada",
                type = "place",
                kind = "places",
                tags = listOf("goa", "fort", "sunset", "trip"),
                items = emptyList(),
                place = "Fort Aguada, Candolim, Goa",
                date = "", link = "",
                summary = "17th-century Portuguese fort and lighthouse above Sinquerim beach. Go before sunset.",
            ),
        ),
        ExampleSave(
            asset = "recipe.webp",
            card = CardDraft(
                title = "Shakshuka",
                type = "recipe",
                kind = "recipes",
                tags = listOf("breakfast", "eggs", "one-pan", "vegetarian"),
                items = listOf(
                    "4 eggs", "2 tbsp olive oil", "1 onion, chopped", "1 red pepper, sliced", "3 garlic cloves",
                    "1 tsp cumin", "1 tsp paprika", "400 g chopped tomatoes", "Salt and pepper", "Fresh coriander",
                ),
                place = "", date = "", link = "",
                summary = "Eggs cooked in a spiced tomato and pepper sauce. One pan, about 25 minutes.",
            ),
        ),
    )
}

class RoomExampleSaves(
    private val context: Context,
    private val dao: SaveDao,
    private val saves: SaveLocalDataSource,
    private val fileDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : ExampleSaves {

    override fun observeIds(): Flow<List<Long>> = dao.observeExampleIds().distinctUntilChanged()

    override suspend fun add(): List<Long> {
        dao.observeExampleIds().first().takeIf { it.isNotEmpty() }?.let { return it }
        val now = clock()
        val images = ExampleContent.ALL.map { copyAsset(it.asset) }
        // Inserted as READ so the read queue never claims them; the card lands right after. The drawn images are
        // small, so each is its own thumb.
        val ids = dao.insert(
            ExampleContent.ALL.indices.map { i ->
                SaveEntity(
                    createdAt = now + i, source = SaveSource.PICKER, imagePath = images[i], thumbPath = images[i],
                    status = SaveStatus.READ, example = true,
                )
            },
        )
        ids.forEachIndexed { i, id -> saves.saveCard(id, ExampleContent.ALL[i].card, ReadBy.HAND, MODEL, now) }
        return ids
    }

    private suspend fun copyAsset(name: String): String? = withContext(fileDispatcher) {
        runCatching {
            val dir = File(context.filesDir, SaveFiles.DIR).apply { mkdirs() }
            val out = File(dir, "example-${UUID.randomUUID()}.${name.substringAfterLast('.')}")
            context.assets.open("$ASSET_DIR/$name").use { input -> out.outputStream().use { input.copyTo(it) } }
            out.path
        }.getOrNull()
    }

    private companion object {
        const val ASSET_DIR = "examples"
        const val MODEL = "example"
    }
}
