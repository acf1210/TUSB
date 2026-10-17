package com.opentonex.controller.ui.editor

import com.opentonex.controller.domain.TonexParam
import com.opentonex.controller.ui.modelParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectModelsTest {
    @Test
    fun everyModelParamHasOneNamePerValueOfItsRange() {
        // O indice escrito no pedal e' a posicao do nome na lista: os tamanhos precisam bater.
        EffectSlotType.entries.forEach { effect ->
            val param = effect.modelParam() ?: return@forEach
            assertEquals(
                "modelos de $effect",
                param.max.toInt() - param.min.toInt() + 1,
                EffectModels.namesFor(effect).size
            )
        }
    }

    @Test
    fun onlyReverbModulationAndDelayHaveModels() {
        val withModels = EffectSlotType.entries.filter { EffectModels.namesFor(it).isNotEmpty() }
        assertEquals(listOf(EffectSlotType.MOD, EffectSlotType.DLY, EffectSlotType.REV), withModels)
        assertNull(EffectSlotType.CAB.modelParam())
        assertEquals(TonexParam.REVERB_MODEL, EffectSlotType.REV.modelParam())
    }

    @Test
    fun modulationLabelsFollowTheKnobMapping() {
        assertEquals(Triple("RATE", "DEPTH", "LEVEL"), EffectModels.modulationKnobLabels(0))
        assertEquals(Triple("RATE", "SHAPE", "LEVEL"), EffectModels.modulationKnobLabels(1))
        assertEquals(Triple("SPEED", "RADIUS", "LEVEL"), EffectModels.modulationKnobLabels(4))
        assertTrue(EffectModels.modulationKnobLabels(99).first == "RATE")
    }
}
