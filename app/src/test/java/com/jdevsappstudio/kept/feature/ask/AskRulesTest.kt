package com.jdevsappstudio.kept.feature.ask

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.core.model.AskRef
import org.junit.jupiter.api.Test

class AskRulesTest {

    @Test
    fun `cloud chip counts the saves sent`() {
        assertThat(AskCopy.cloudChip(0)).isEqualTo("Cloud")
        assertThat(AskCopy.cloudChip(1)).isEqualTo("Cloud · sent text of 1 save")
        assertThat(AskCopy.cloudChip(9)).isEqualTo("Cloud · sent text of 9 saves")
    }

    @Test
    fun `counter and daily lines`() {
        assertThat(AskCopy.counterLine(2, 5)).isEqualTo("2 of 5 left today")
        assertThat(AskCopy.dailyLine(5)).isEqualTo("5 free cloud answers a day.")
    }

    @Test
    fun `report needs a reason and shows a note only for Other`() {
        assertThat(AskCopy.canSendReport(null)).isFalse()
        assertThat(AskCopy.canSendReport(ReportReason.WRONG)).isTrue()
        assertThat(AskCopy.showsNote(ReportReason.WRONG)).isFalse()
        assertThat(AskCopy.showsNote(ReportReason.OTHER)).isTrue()
    }

    @Test
    fun `ref line is the first summary line, else the place`() {
        assertThat(AskRef.line("\n  Assagao · banana pancakes \nMore", "Goa")).isEqualTo("Assagao · banana pancakes")
        assertThat(AskRef.line("", " Morjim ")).isEqualTo("Morjim")
        assertThat(AskRef.line("  ", "")).isEqualTo("")
    }
}
