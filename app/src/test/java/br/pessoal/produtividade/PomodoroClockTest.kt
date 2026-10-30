package br.pessoal.produtividade

import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroClockTest {
    @Test fun usesMonotonicDeadlineOnSameBoot() {
        assertEquals(9_000L, PomodoroClock.restante(20_000, 99_000, 4, 4, 11_000, 50_000))
    }

    @Test fun fallsBackToWallClockAfterReboot() {
        assertEquals(30_000L, PomodoroClock.restante(20_000, 80_000, 4, 5, 1_000, 50_000))
    }

    @Test fun expiredSessionNeverHasNegativeRemainingTime() {
        assertEquals(0L, PomodoroClock.restante(20_000, 40_000, 4, 5, 1_000, 50_000))
    }

    @Test fun clockMovedBackDuringRebootCannotExtendSessionBeyondSavedDuration() {
        assertEquals(12_000L, PomodoroClock.restante(20_000, 900_000, 4, 5, 1_000, 50_000, 12_000))
    }

    @Test fun focusCyclesAlternateShortAndLongBreaks() {
        assertEquals(Modo.CURTA, proximoModo(Modo.FOCO, 1))
        assertEquals(Modo.LONGA, proximoModo(Modo.FOCO, 4))
        assertEquals(Modo.FOCO, proximoModo(Modo.CURTA, 1))
        assertEquals(Modo.FOCO, proximoModo(Modo.LONGA, 4))
    }
}
