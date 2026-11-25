package com.jdevsappstudio.kept.data.ask

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class AskQueryTest {

    @Test
    fun `filler words go, content words stay`() {
        assertThat(AskQuery.parse("Which thriller should I watch first?").words).containsExactly("thriller", "watch")
        assertThat(AskQuery.parse("What can I cook tonight?").words).containsExactly("cook", "tonight")
        assertThat(AskQuery.parse("what is the best one?").isBlank).isTrue()
    }

    @Test
    fun `match ORs every form, with plurals and kind synonyms`() {
        val m = AskQuery.parse("films about stories").match
        assertThat(m).contains("film*")
        assertThat(m).contains("movie*")
        assertThat(m).contains("story*")
        assertThat(m).contains("stories*")
        assertThat(m).contains(" OR ")
        assertThat(AskQuery.parse("the a of").match).isEqualTo("")
    }

    @Test
    fun `hits counts words as prefixes, accents folded`() {
        val q = AskQuery.parse("cafes in goa for brunch")
        assertThat(q.hits("Best Café in Goa · brunch spots")).isEqualTo(3)
        assertThat(q.hits("A cafeteria in Pune")).isEqualTo(1) // 'cafe' prefix
        assertThat(q.hits("")).isEqualTo(0)
    }
}

class AskPromptTest {

    private fun save(id: Long, title: String, body: String = title, createdAt: Long = id, tags: List<String> = emptyList()) =
        SourceSave(id = id, createdAt = createdAt, title = title, type = "movie_show", tags = tags, body = body)

    @Test
    fun `title and tag hits rank above body-only hits, ties go to the newest`() {
        val q = AskQuery.parse("which thriller first")
        val bodyOnly = save(1, "Weekend list", body = "Weekend list a thriller night", createdAt = 50)
        val titled = save(2, "Gone Girl thriller", createdAt = 10)
        // The real FTS body holds the tags too; ties with the titled one (4 each), and it's newer.
        val tagged = save(3, "Prisoners", body = "Prisoners thriller", createdAt = 20, tags = listOf("thriller"))
        val ranked = AskPrompt.rank(q, listOf(bodyOnly, titled, tagged))
        assertThat(ranked.map { it.id }).containsExactly(3L, 2L, 1L)
    }

    @Test
    fun `previous sources fill free slots after fresh hits, no repeats, at most 20`() {
        val q = AskQuery.parse("open tuesday")
        val fresh = save(9, "Cafe open tuesday")
        val carry = (1L..25L).map { save(it, "Place $it", body = "nothing") } + save(9, "Cafe open tuesday")
        val ranked = AskPrompt.rank(q, listOf(fresh), carry)
        assertThat(ranked.first().id).isEqualTo(9L)
        assertThat(ranked.size).isEqualTo(AskPrompt.MAX_SOURCES)
        assertThat(ranked.map { it.id }.distinct().size).isEqualTo(ranked.size)
    }

    @Test
    fun `no hits and nothing to carry gives an empty list (the repo falls back to the newest)`() {
        assertThat(AskPrompt.rank(AskQuery.parse("zebra"), listOf(save(1, "Pasta")))).isEmpty()
    }

    @Test
    fun `prompt numbers sources, carries history and the question, and stays in budget`() {
        val long = "word ".repeat(2_000)
        val sources = (1L..20L).map { SourceSave(id = it, createdAt = it, title = "Save $it", type = "place", fullText = long) }
        val p = AskPrompt.build("Where should I go?", listOf(AskHistory("Any cafes?", "Try Save 2.")), sources)
        assertThat(p.text).contains("[1] Save 1 (Place)")
        assertThat(p.text).contains("Q: Any cafes?")
        assertThat(p.text).contains("A: Try Save 2.")
        assertThat(p.text.endsWith("Question: Where should I go?")).isTrue()
        assertThat(p.text.length).isLessThan(AskPrompt.SOURCE_BUDGET + 1_000)
        assertThat(p.saveIds.first()).isEqualTo(1L)
        // Every id listed was actually written into the prompt.
        p.saveIds.forEachIndexed { i, id -> assertThat(p.text).contains("[${i + 1}] Save $id") }
    }

    @Test
    fun `no sources says so`() {
        val p = AskPrompt.build("Anything?", emptyList(), emptyList())
        assertThat(p.text).contains("Saves: none match.")
        assertThat(p.saveIds).isEmpty()
        assertThat(p.text).doesNotContain("Earlier in this chat")
    }

    /** The real answer shape from the OnePlus 8 (25 Nov). */
    @Test
    fun `inline markers are stripped from the text and can stand in for refs`() {
        val raw = "You saved Submit Apps for review [1], a Kotlin app video [3], and two astrology apps [4, 14]."
        assertThat(AskPrompt.cleanAnswer(raw)).isEqualTo("You saved Submit Apps for review, a Kotlin app video, and two astrology apps.")
        assertThat(AskPrompt.markers(raw)).containsExactly(1, 3, 4, 14)
        assertThat(AskPrompt.markers("No saves match.")).isEmpty()
    }

    @Test
    fun `ref numbers map to save ids in order, repeats and bad numbers dropped`() {
        assertThat(AskPrompt.refIds(listOf(2, 0, 2, 9, 1), listOf(10L, 20L, 30L))).containsExactly(20L, 10L)
    }
}

class AskStartersTest {

    @Test
    fun `under 3 saves there are no starters`() {
        assertThat(AskStarters.build(2, mapOf("recipe" to 2), listOf("pasta"))).isEmpty()
    }

    @Test
    fun `types the person saves most come first, tags fill up, type names skipped`() {
        val s = AskStarters.build(
            readSaves = 12,
            typeCounts = mapOf("recipe" to 6, "place" to 3, "app" to 1),
            topTags = listOf("recipe", "goa", "thriller"),
        )
        assertThat(s).containsExactly("What can I cook tonight?", "Which places should I go to first?", "What did I save about goa?")
    }

    @Test
    fun `unknown types count as other and give no type starter`() {
        assertThat(AskStarters.build(5, mapOf("weird" to 5), listOf("tea"))).containsExactly("What did I save about tea?")
    }
}
