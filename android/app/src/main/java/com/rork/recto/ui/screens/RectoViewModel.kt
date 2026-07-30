package com.rork.recto.ui.screens

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rork.recto.data.AuthRepository
import com.rork.recto.data.CloudSyncService
import com.rork.recto.data.EncryptionKeyRepository
import com.rork.recto.data.RectoSession
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
data class PageQuality(val sharpness: Int, val glare: Int)

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
    val pageQualities: List<PageQuality> = emptyList(),
    val cloudPath: String? = null,
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
    val capturedQualities: List<PageQuality> = emptyList(),
    val message: String? = null,
    val isSyncing: Boolean = false,
    val isSyncError: Boolean = false
)

sealed interface RectoAction {
    data class SetFilter(val filter: String) : RectoAction
    data class SetSearch(val query: String) : RectoAction
    data class AddPage(val path: String, val sharpness: Int = 0, val glare: Int = 0) : RectoAction
    data class RemovePage(val index: Int) : RectoAction
    data class MovePage(val from: Int, val to: Int) : RectoAction
    data class SaveCapture(val title: String) : RectoAction
    data class Rename(val id: String, val title: String) : RectoAction
    data class Duplicate(val id: String) : RectoAction
    data class Delete(val id: String) : RectoAction
    data class Restore(val id: String) : RectoAction
    data class DeleteForever(val id: String) : RectoAction
    data object ClearCapture : RectoAction
    data object Capture : RectoAction
    data object RestoreFromCloud : RectoAction
}

class RectoViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val cloudSync: CloudSyncService,
    private val encryptionKeys: EncryptionKeyRepository
) : ViewModel() {
    private val preferences: SharedPreferences = application.getSharedPreferences("recto_documents", 0)
    private val json = Json { ignoreUnknownKeys = true }
    private val _uiState = MutableStateFlow(RectoUiState(documents = loadDocuments()))
    val uiState: StateFlow<RectoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.validSession()?.let { session ->
                restoreFromCloud(session)
            }
        }
    }

    fun onAction(action: RectoAction) {
        when (action) {
            is RectoAction.SetFilter -> _uiState.update { it.copy(selectedFilter = action.filter) }
            is RectoAction.SetSearch -> _uiState.update { it.copy(searchQuery = action.query) }
            is RectoAction.AddPage -> _uiState.update {
                val newPages = it.capturedPages + action.path
                val newQualities = it.capturedQualities + PageQuality(action.sharpness, action.glare)
                val avgQuality = if (newQualities.isNotEmpty()) newQualities.map { q -> (q.sharpness + (100 - q.glare)) / 2 }.average().toInt() else 94
                it.copy(capturedPages = newPages, capturedQualities = newQualities, proofScore = avgQuality)
            }
            is RectoAction.RemovePage -> _uiState.update { state ->
                val newPages = state.capturedPages.filterIndexed { index, _ -> index != action.index }
                val newQualities = state.capturedQualities.filterIndexed { index, _ -> index != action.index }
                val avgQuality = if (newQualities.isNotEmpty()) newQualities.map { q -> (q.sharpness + (100 - q.glare)) / 2 }.average().toInt() else 94
                state.copy(capturedPages = newPages, capturedQualities = newQualities, proofScore = avgQuality)
            }
            is RectoAction.MovePage -> _uiState.update { state ->
                val pages = state.capturedPages.toMutableList()
                val qualities = state.capturedQualities.toMutableList()
                if (action.from in pages.indices && action.to in pages.indices) {
                    pages.add(action.to, pages.removeAt(action.from))
                    qualities.add(action.to, qualities.removeAt(action.from))
                }
                state.copy(capturedPages = pages, capturedQualities = qualities)
            }
            is RectoAction.SaveCapture -> saveCapture(action.title)
            is RectoAction.Rename -> updateDocuments { documents -> documents.map { if (it.id == action.id) it.copy(title = action.title) else it } }
            is RectoAction.Duplicate -> updateDocuments { documents ->
                documents.firstOrNull { it.id == action.id }?.let { documents + it.copy(id = UUID.randomUUID().toString(), title = "${it.title} copy") } ?: documents
            }
            is RectoAction.Delete -> {
                updateDocuments { documents -> documents.map { if (it.id == action.id) it.copy(isDeleted = true) else it } }
                syncDeletionState(action.id, deleted = true)
            }
            is RectoAction.Restore -> {
                updateDocuments { documents -> documents.map { if (it.id == action.id) it.copy(isDeleted = false) else it } }
                syncDeletionState(action.id, deleted = false)
            }
            is RectoAction.DeleteForever -> {
                val document = _uiState.value.documents.firstOrNull { it.id == action.id }
                updateDocuments { documents -> documents.filter { it.id != action.id } }
                document?.let { doc ->
                    viewModelScope.launch {
                        doc.pagePaths.forEach { File(it).delete() }
                        val session = authRepository.validSession() ?: return@launch
                        cloudSync.deleteForever(session, doc.id, doc.cloudPath ?: "")
                    }
                }
            }
            RectoAction.ClearCapture -> _uiState.update { it.copy(capturedPages = emptyList(), capturedQualities = emptyList(), proofScore = 94) }
            RectoAction.Capture -> Unit
            RectoAction.RestoreFromCloud -> viewModelScope.launch {
                authRepository.validSession()?.let { session ->
                    restoreFromCloud(session)
                }
            }
        }
    }

    private suspend fun restoreFromCloud(session: RectoSession) {
        _uiState.update { it.copy(isSyncing = true) }
        val key = encryptionKeys.resolve(session).getOrNull()
        if (key == null) {
            _uiState.update { it.copy(isSyncing = false, isSyncError = true, message = "Couldn't unlock encrypted backups. Try again shortly.") }
            return
        }
        cloudSync.listCloudDocuments(session).fold(
            onSuccess = { remoteDocs ->
                val localIds = _uiState.value.documents.map { it.id }.toSet()
                val missing = remoteDocs.filter { it.id !in localIds }
                var restoredCount = 0
                for (remote in missing) {
                    cloudSync.download(session, remote, key).onSuccess { document ->
                        updateDocuments { it + document }
                        restoredCount += 1
                    }
                }
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        isSyncError = false,
                        message = if (restoredCount > 0) "Restored $restoredCount document${if (restoredCount == 1) "" else "s"} from your account" else null,
                    )
                }
            },
            onFailure = { _uiState.update { state -> state.copy(isSyncing = false, isSyncError = true, message = "Cloud sync is unavailable right now") } },
        )
    }

    private fun syncDeletionState(documentId: String, deleted: Boolean) {
        viewModelScope.launch {
            val session = authRepository.validSession() ?: return@launch
            cloudSync.syncDeletion(session, documentId, deleted)
        }
    }

    private fun saveCapture(title: String) {
        val state = _uiState.value
        val pages = state.capturedPages
        if (pages.isEmpty()) return
        val document = RectoDocument(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Untitled scan" },
            detail = "JUST NOW · ON DEVICE",
            pages = pages.size,
            quality = state.proofScore,
            isVerified = state.proofScore >= 85,
            accent = if (state.proofScore >= 85) DocumentAccent.CYAN else DocumentAccent.NEUTRAL,
            pagePaths = pages,
            pageQualities = state.capturedQualities
        )
        updateDocuments { listOf(document) + it }
        _uiState.update { it.copy(capturedPages = emptyList(), capturedQualities = emptyList(), message = "Document saved on this device", isSyncError = false) }
        viewModelScope.launch {
            val session = authRepository.validSession() ?: return@launch
            val key = encryptionKeys.resolve(session).getOrNull()
            if (key == null) {
                _uiState.update { state -> state.copy(isSyncError = true, message = "Saved locally. Encrypted backup will retry when online.") }
                return@launch
            }
            cloudSync.upload(session, document, key).fold(
                onSuccess = {
                    val objectPath = "${session.user.id}/${document.id}.recto"
                    updateDocuments { documents -> documents.map { if (it.id == document.id) it.copy(detail = "JUST NOW · ENCRYPTED BACKUP", cloudPath = objectPath) else it } }
                },
                onFailure = { _uiState.update { state -> state.copy(isSyncError = true, message = "Saved locally. Encrypted backup will retry when online.") } }
            )
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
