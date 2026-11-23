package com.abgmie.vpn.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abgmie.vpn.updates.ReleaseInfo
import com.abgmie.vpn.updates.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class UpdateUi(
    val checking: Boolean = false,
    val release: ReleaseInfo? = null,
    val error: String? = null,
    val progress: Float? = null,
    val verifiedFile: File? = null,
    val checked: Boolean = false,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(private val repo: UpdateRepository) : ViewModel() {
    val ui = MutableStateFlow(UpdateUi())
    private val _launch = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val launches: SharedFlow<Intent> = _launch

    fun check() = viewModelScope.launch {
        ui.value = UpdateUi(checking = true)
        ui.value = runCatching { repo.latest() }.fold(
            { UpdateUi(release = it, checked = true) },
            { UpdateUi(error = it.message, checked = true) },
        )
    }

    fun download() = viewModelScope.launch {
        val r = ui.value.release ?: return@launch
        ui.value = ui.value.copy(progress = 0f, error = null)
        runCatching { repo.download(r) { p -> ui.value = ui.value.copy(progress = p) } }
            .onSuccess { ui.value = ui.value.copy(progress = null, verifiedFile = it); install() }
            .onFailure { ui.value = ui.value.copy(progress = null, error = it.message) }
    }

    fun install() {
        val f = ui.value.verifiedFile ?: return
        if (!repo.canInstall()) {
            ui.value = ui.value.copy(error = "Allow \"Install unknown apps\" for AbgMieVPN, then tap Install again.")
            _launch.tryEmit(repo.unknownSourcesIntent())
        } else {
            _launch.tryEmit(repo.installIntent(f))
        }
    }
}
