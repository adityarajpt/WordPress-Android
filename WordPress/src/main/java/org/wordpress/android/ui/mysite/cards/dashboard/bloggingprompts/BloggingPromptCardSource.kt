package org.wordpress.android.ui.mysite.cards.dashboard.bloggingprompts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.BloggingRemindersStore
import org.wordpress.android.fluxc.store.bloggingprompts.BloggingPromptsStore
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.ui.mysite.MySiteSource.MySiteRefreshSource
import org.wordpress.android.ui.mysite.MySiteUiState.PartialState.BloggingPromptUpdate
import org.wordpress.android.ui.mysite.SelectedSiteRepository
import org.wordpress.android.ui.prefs.AppPrefsWrapper
import org.wordpress.android.util.config.BloggingPromptsFeatureConfig
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Named

const val REFRESH_DELAY = 500L

class BloggingPromptCardSource @Inject constructor(
    private val selectedSiteRepository: SelectedSiteRepository,
    private val promptsStore: BloggingPromptsStore,
    private val bloggingPromptsFeatureConfig: BloggingPromptsFeatureConfig,
    private val appPrefsWrapper: AppPrefsWrapper,
    private val bloggingRemindersStore: BloggingRemindersStore,
    @param:Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher
) : MySiteRefreshSource<BloggingPromptUpdate> {
    override val refresh = MutableLiveData(false)
    val singleRefresh = MutableLiveData(false)

    companion object {
        private const val NUM_PROMPTS_TO_REQUEST = 20
    }

    override fun build(coroutineScope: CoroutineScope, siteLocalId: Int): LiveData<BloggingPromptUpdate> {
        val result = MediatorLiveData<BloggingPromptUpdate>()
        result.getData(coroutineScope, siteLocalId)
        result.addSource(refresh) { result.refreshData(coroutineScope, siteLocalId, refresh.value) }
        result.addSource(singleRefresh) { result.refreshData(coroutineScope, siteLocalId, singleRefresh.value, true) }
        refresh()
        return result
    }

    fun refreshTodayPrompt() {
        if (isRefreshing() == false) {
            singleRefresh.postValue(true)
        }
    }

    private fun MediatorLiveData<BloggingPromptUpdate>.getData(
        coroutineScope: CoroutineScope,
        siteLocalId: Int
    ) {
        val selectedSite = selectedSiteRepository.getSelectedSite()
        if (selectedSite != null && selectedSite.id == siteLocalId && bloggingPromptsFeatureConfig.isEnabled()) {
            coroutineScope.launch(bgDispatcher) {
                if (isPrompAvailable()) {
                    promptsStore.getPromptForDate(selectedSite, Date()).collect { result ->
                        postValue(BloggingPromptUpdate(result.model))
                    }
                } else {
                    postEmptyState()
                }
            }
        } else {
            postErrorState()
        }
    }

    private fun MediatorLiveData<BloggingPromptUpdate>.refreshData(
        coroutineScope: CoroutineScope,
        siteLocalId: Int,
        isRefresh: Boolean? = null,
        isSinglePromptRefresh: Boolean = false
    ) {
        when (isRefresh) {
            null, true -> refreshData(coroutineScope, siteLocalId, isSinglePromptRefresh)
            else -> Unit // Do nothing
        }
    }

    private fun MediatorLiveData<BloggingPromptUpdate>.refreshData(
        coroutineScope: CoroutineScope,
        siteLocalId: Int,
        isSinglePromptRefresh: Boolean = false
    ) {
        val selectedSite = selectedSiteRepository.getSelectedSite()
        if (selectedSite != null && selectedSite.id == siteLocalId) {
            if (bloggingPromptsFeatureConfig.isEnabled()) {
                coroutineScope.launch(bgDispatcher) {
                    if (isPrompAvailable()) {
                        fetchPromptsAndPostErrorIfAvailable(coroutineScope, selectedSite, isSinglePromptRefresh)
                    } else {
                        postEmptyState()
                    }
                }
            } else {
                onRefreshedMainThread()
            }
        } else {
            postErrorState()
        }
    }

    private fun MediatorLiveData<BloggingPromptUpdate>.fetchPromptsAndPostErrorIfAvailable(
        coroutineScope: CoroutineScope,
        selectedSite: SiteModel,
        isSinglePromptRefresh: Boolean = false
    ) {
        coroutineScope.launch(bgDispatcher) {
            delay(REFRESH_DELAY)
            val numOfPromptsToFetch = if (isSinglePromptRefresh) 1 else NUM_PROMPTS_TO_REQUEST
            val result = promptsStore.fetchPrompts(selectedSite, numOfPromptsToFetch, Date())
            val error = result.error
            when {
                error != null -> {
                    postErrorState()
                }
                else -> onRefreshedBackgroundThread()
            }
        }
    }

    // we don't have any special error handling at this point - just show the last available prompt
    private fun MediatorLiveData<BloggingPromptUpdate>.postErrorState() {
        val lastPrompt = this.value?.promptModel
        postState(BloggingPromptUpdate(lastPrompt))
    }

    private fun MediatorLiveData<BloggingPromptUpdate>.postEmptyState() {
        postState(BloggingPromptUpdate(null))
    }

    private suspend fun isPrompAvailable(): Boolean {
        val selectedSite = selectedSiteRepository.getSelectedSite() ?: return false
        val isPotentialBloggingSite = selectedSite.isPotentialBloggingSite
        val isPromptReminderOptedIn = bloggingRemindersStore.bloggingRemindersModel(selectedSite.localId().value)
                .firstOrNull()?.isPromptIncluded == true
        val promptSkippedDate = appPrefsWrapper.getSkippedPromptDay()

        val isPromptSkippedForToday = promptSkippedDate != null && isSameDay(promptSkippedDate, Date())

        return !isPromptSkippedForToday &&
                (isPromptReminderOptedIn || (!isPromptReminderOptedIn && isPotentialBloggingSite))
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val localDate1: LocalDate = date1.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        val localDate2: LocalDate = date2.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        return localDate1.isEqual(localDate2)
    }
}
