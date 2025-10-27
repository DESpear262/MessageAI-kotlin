package com.messageai.tactical.modules.reporting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportService: ReportService
) : ViewModel() {

    private val _markdown = MutableStateFlow<String?>(null)
    val markdown: StateFlow<String?> = _markdown

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun loadSitrep(chatId: String, window: String = "6h") {
        _loading.value = true
        viewModelScope.launch {
            reportService.generateSITREP(chatId, window)
                .onSuccess { _markdown.value = it }
                .onFailure { _markdown.value = "# SITREP\n\n_Generation failed: ${it.message}_" }
            _loading.value = false
        }
    }

    fun loadTemplate(kind: String, chatId: String? = null) {
        android.util.Log.i("ReportViewModel", "loadTemplate kind=${kind} chatId=$chatId")
        _loading.value = true
        viewModelScope.launch {
            val result = when (kind.lowercase()) {
                "warnord" -> reportService.generateWarnord(chatId)
                "opord" -> reportService.generateOpord(chatId)
                "frago" -> reportService.generateFrago(chatId)
                "medevac" -> reportService.generateMedevac(chatId)
                else -> Result.failure(IllegalArgumentException("unknown template"))
            }
            result
                .onSuccess { _markdown.value = it }
                .onFailure { _markdown.value = "# Template\n\n_Generation failed: ${it.message}_" }
            _loading.value = false
        }
    }
}


