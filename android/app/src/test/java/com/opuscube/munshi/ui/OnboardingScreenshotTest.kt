package com.opuscube.munshi.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.opuscube.munshi.api.Profile
import com.opuscube.munshi.brain.BrainState
import com.opuscube.munshi.onboarding.OnboardingUi
import com.opuscube.munshi.onboarding.Step
import com.opuscube.munshi.ui.onboarding.OnboardingActions
import com.opuscube.munshi.ui.onboarding.OnboardingContent
import com.opuscube.munshi.ui.onboarding.OnboardingEnv
import org.junit.Rule
import org.junit.Test

// The Figma Onboarding frames, one per step, plus the error and the nothing-heard voice check.
class OnboardingScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)
    private val env = OnboardingEnv("Gemma 4 · E4B", BrainState.Downloading(1_500_000_000, 3_659_530_240), micGranted = true, notifyGranted = false)
    private val a = OnboardingActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val shop = Profile("Ramesh Kirana Store", "Aliganj, Lucknow", "Kirana · rozmarra ka saamaan", "Ramesh Gupta", "Ramesh ji", "+919876543210")
    private fun shot(ui: OnboardingUi, e: OnboardingEnv = env) = paparazzi.snapshot { OnboardingContent(ui, e, a) }

    @Test fun o1Welcome() = shot(OnboardingUi(Step.Welcome))
    @Test fun o2Connect() = shot(OnboardingUi(Step.Connect))
    @Test fun o3Number() = shot(OnboardingUi(Step.Number, phone = "+91 98765 43210"))
    @Test fun o3NumberError() = shot(OnboardingUi(Step.Number, phone = "123", error = "10 ank ka number daaliye."))
    @Test fun o4Otp() = shot(OnboardingUi(Step.Otp, phone = "+91 98765 43210", otp = "482917", profile = shop))
    @Test fun o5Shop() = shot(OnboardingUi(Step.Shop, profile = shop))
    @Test fun o6Permissions() = shot(OnboardingUi(Step.Permissions))
    @Test fun o7Brain() = shot(OnboardingUi(Step.Brain))
    @Test fun o7BrainReady() = shot(OnboardingUi(Step.Brain), env.copy(brain = BrainState.Ready))
    @Test fun o8VoiceHeard() = shot(OnboardingUi(Step.Voice, profile = shop, heard = "haan", voiceTried = true))
    @Test fun o8VoiceNothing() = shot(OnboardingUi(Step.Voice, profile = shop, voiceTried = true))
}
