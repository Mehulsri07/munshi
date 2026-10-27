package com.opuscube.munshi.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class Step { Welcome, Connect, Number, Otp, Shop, Permissions, Brain, Voice }

// Where the merchant is in onboarding. editOnly: opened from settings to change the shop's details.
data class Progress(val step: Step = Step.Welcome, val done: Boolean = false, val editOnly: Boolean = false)

interface ProgressStore {
    val progress: Flow<Progress>
    suspend fun save(p: Progress)
}

private val Context.onboardingStore by preferencesDataStore("onboarding")
private val STEP = stringPreferencesKey("step")
private val DONE = booleanPreferencesKey("done")
private val EDIT = booleanPreferencesKey("editOnly")

class DataStoreProgress(private val context: Context) : ProgressStore {
    override val progress: Flow<Progress> = context.onboardingStore.data.map { p ->
        Progress(p[STEP]?.let { s -> Step.entries.firstOrNull { it.name == s } } ?: Step.Welcome, p[DONE] ?: false, p[EDIT] ?: false)
    }
    override suspend fun save(p: Progress) { context.onboardingStore.edit { it[STEP] = p.step.name; it[DONE] = p.done; it[EDIT] = p.editOnly } }
}
