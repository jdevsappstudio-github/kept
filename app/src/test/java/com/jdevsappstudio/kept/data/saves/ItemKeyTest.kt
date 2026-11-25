package com.jdevsappstudio.kept.data.saves

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class ItemKeyTest {
    @Test fun `case and punctuation do not matter`() {
        assertThat(ItemKey.of("Gone Girl")).isEqualTo(ItemKey.of("  gone girl! "))
        assertThat(ItemKey.of("Se7en")).isEqualTo("se7en")
    }

    @Test fun `bracketed or separated year is dropped`() {
        assertThat(ItemKey.of("Gone Girl (2014)")).isEqualTo("gone girl")
        assertThat(ItemKey.of("Gone Girl [2014]")).isEqualTo("gone girl")
        assertThat(ItemKey.of("Prisoners - 2013")).isEqualTo("prisoners")
        assertThat(ItemKey.of("Zodiac · 2007")).isEqualTo("zodiac")
    }

    @Test fun `a number that is part of the title stays`() {
        assertThat(ItemKey.of("Blade Runner 2049")).isEqualTo("blade runner 2049")
        assertThat(ItemKey.of("1917")).isEqualTo("1917")
        assertThat(ItemKey.of("(2019)")).isEqualTo("2019")
    }

    @Test fun `non latin titles keep their letters`() {
        assertThat(ItemKey.of("अंधाधुन (2018)")).isEqualTo("अंधाधुन")
    }

    @Test fun `only punctuation gives an empty key`() {
        assertThat(ItemKey.of(" -- ")).isEqualTo("")
    }
}
