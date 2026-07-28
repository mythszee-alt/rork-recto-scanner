package com.rork.recto.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.recto.data.AuthRepository
import com.rork.recto.data.CloudSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class RectoDocument(
    val id: String,
    val title: String,
    val detail: String,
    val pages: Int,
    val quality: Int,
    val isVerified: Boolean,
    val accent: DocumentAccent,
    val pagePaths: List<String> = emptyList(),
    val isDeleted: Boolean = false
)

@Serializable
enum class DocumentAccent { CYAN, MAGENTA, NEUTRAL }

data class RectoUiState(
    val documents: List<RectoDocument> = emptyList(),
    val selectedFilter: String = "ALL",
    val searchQuery: String = "",
    val proofScore: Int = 94,
    val capturedPages: List<String> = emptyList(),
    val message: String? = null
)

sealed interface RectoAction {
    data class SetFilter(val filter: String) : RectoAction
    data class SetSearch(val query: String) : RectoAction
    data class AddPage(val path: String) : RectoAction
    data class RemovePage(val index: Int) : RectoAction
    data class MovePage(val from: Int, val to: Int) : RectoAction
    data class SaveCapture(val title: String) : RectoAction
    data class Rename(val id: String, val title: String) : RectoAction
    data class Duplicate(val id: String) : RectoAction
    data class Delete(val id: String) : RectoAction
    data class Restore(val id: String) : RectoAction
    data object ClearCapture : RectoAction
    data object Capture : RectoAction
}

class RectoViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("recto_documents", 0)
    private val json = Json { ignoreUnknownKeys = true }
    private val authRepository = AuthRepository(application)
    private val cloudSync = CloudSyncService(application)
    private val _uiState = MutableStateFlow(RectoUiState(documents = loadDocuments()))
    val uiState: StateFlow<RectoUiState> = _uiState.asStateFlow()

    fun onAction(action: RectoAction) {
        when (action) {
            is RectoAction.SetFilter -> _uiState.update { it.copy(selectedFilter = action.filter) }
            is RectoAction.SetSearch -> _uiState.update { it.copy(searchQuery = action.query) }
            is RectoAction.AddPage -> _uiState.update { it.copy(capturedPages = it.capturedPages + action.path, proofScore = 98) }
            is RectoAction.RemovePage -> _uiState.update { state -> state.copy(capturedPages = state.capturedPages.filterIndexed { index, _ -> index != action.index }) }
            is RectoAction.MovePage -> _uiState.update { state ->
                val pages = state.capturedPages.toMutableList()
                if (action.from in pages.indices && action.to in pages.indices) pages.add(action.to, pages.removeAt(action.from))
                state.copy(capturedPages = pages)
            }
            is RectoAction.SaveCapture -> saveCapture(action.title)
            is RectoAction.Rename -> updateDocuments { documents -> documents.map { if (it.id == action.id) it.copy(title = action.title) else it } }
            is RectoAction.Duplicate -> updateDocuments { documents ->
                documents.firstOrNull { it.id == action.id }?.let { documents + it.copy(id = UUID.randomUUID().toString(), title = "${it.title} copy") } ?: documents
            }
            is RectoAction.Delete -> updateDocuments { documents -> documents.map { if (it.id == action.id) it.copy(isDeleted = true) else it } }
            is RectoAction.Restore -> updateDocuments { documents -> documents.map { if (it.id == action.id) it.copy(isDeleted = false) else it } }
            RectoAction.ClearCapture -> _uiState.update { it.copy(capturedPages = emptyList()) }
            RectoAction.Capture -> Unit
        }
    }

    private fun saveCapture(title: String) {
        val pages = _uiState.value.capturedPages
        if (pages.isEmpty()) return
        val document = RectoDocument(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Untitled scan" },
            detail = "JUST NOW · ON DEVICE",
            pages = pages.size,
            quality = _uiState.value.proofScore,
            isVerified = false,
            accent = DocumentAccent.CYAN,
            pagePaths = pages
        )
        updateDocuments { listOf(document) + it }
        _uiState.update { it.copy(capturedPages = emptyList(), message = "Document saved on this device") }
        authRepository.restoredSession()?.let { session ->
            viewModelScope.launch {
                cloudSync.upload(session, document).fold(
                    onSuccess = { updateDocuments { documents -> documents.map { if (it.id == document.id) it.copy(detail = "JUST NOW · ENCRYPTED BACKUP") else it } } },
                    onFailure = { _uiState.update { state -> state.copy(message = "Saved locally. Encrypted backup will retry when online.") } }
                )
            }
        }
    }

    private fun updateDocuments(transform: (List<RectoDocument>) -> List<RectoDocument>) {
        val updated = transform(_uiState.value.documents)
        _uiState.update { it.copy(documents = updated) }
        preferences.edit().putString("items", json.encodeToString(updated)).apply()
    }

    private fun loadDocuments(): List<RectoDocument> {
        val raw = preferences.getString("items", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<RectoDocument>>(raw) }.getOrDefault(emptyList())
            .filter { document -> document.pagePaths.all { File(it).exists() } }
    }
}
