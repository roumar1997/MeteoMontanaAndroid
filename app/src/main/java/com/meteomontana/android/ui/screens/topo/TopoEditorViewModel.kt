package com.meteomontana.android.ui.screens.topo
import com.meteomontana.android.util.toUserMessage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.dto.CreateBlockLineRequest
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.usecase.blocks.GetBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.UpdateBlockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditableLine(
    val tempId: String,
    val name: String,
    val grade: String?,
    val startType: String?,
    val stroke: LineStroke
)

data class TopoEditorUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val block: Block? = null,
    val lines: List<EditableLine> = emptyList(),
    val selectedLineId: String? = null,
    val drawing: Boolean = false,
    val saving: Boolean = false,
    val savedOk: Boolean = false
)

@HiltViewModel
class TopoEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getBlock: GetBlockUseCase,
    private val updateBlock: UpdateBlockUseCase
) : ViewModel() {
    private val blockId: String = checkNotNull(savedStateHandle["blockId"])

    private val _state = MutableStateFlow(TopoEditorUiState())
    val state: StateFlow<TopoEditorUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val block = getBlock(blockId)
                val lines = block.lines.map { l ->
                    EditableLine(
                        tempId = l.id, name = l.name, grade = l.grade,
                        startType = l.startType, stroke = parseLineStroke(l.linePath)
                    )
                }
                _state.value = TopoEditorUiState(
                    loading = false, block = block, lines = lines
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = t.toUserMessage())
            }
        }
    }

    fun startNewLine(name: String, grade: String?, startType: String?) {
        val newLine = EditableLine(
            tempId = java.util.UUID.randomUUID().toString(),
            name = name, grade = grade, startType = startType,
            stroke = LineStroke(emptyList())
        )
        _state.value = _state.value.copy(
            lines = _state.value.lines + newLine,
            selectedLineId = newLine.tempId,
            drawing = true
        )
    }

    fun selectLine(id: String?) {
        _state.value = _state.value.copy(selectedLineId = id)
    }

    fun toggleDrawing() {
        _state.value = _state.value.copy(drawing = !_state.value.drawing)
    }

    fun addPointToSelected(point: androidx.compose.ui.geometry.Offset) {
        val sel = _state.value.selectedLineId ?: return
        val updated = _state.value.lines.map {
            if (it.tempId == sel) it.copy(stroke = LineStroke(it.stroke.points + point))
            else it
        }
        _state.value = _state.value.copy(lines = updated)
    }

    fun undoLastPoint() {
        val sel = _state.value.selectedLineId ?: return
        val updated = _state.value.lines.map {
            if (it.tempId == sel) {
                val pts = it.stroke.points
                it.copy(stroke = LineStroke(if (pts.isEmpty()) pts else pts.dropLast(1)))
            } else it
        }
        _state.value = _state.value.copy(lines = updated)
    }

    fun deleteLine(id: String) {
        _state.value = _state.value.copy(
            lines = _state.value.lines.filter { it.tempId != id },
            selectedLineId = if (_state.value.selectedLineId == id) null else _state.value.selectedLineId
        )
    }

    fun save() {
        val block = _state.value.block ?: return
        _state.value = _state.value.copy(saving = true, savedOk = false)
        viewModelScope.launch {
            try {
                val req = CreateBlockRequest(
                    type = block.type,
                    name = block.name,
                    lat = block.lat,
                    lon = block.lon,
                    photoPath = block.photoPath,
                    description = block.description,
                    lines = _state.value.lines.map { l ->
                        CreateBlockLineRequest(
                            name = l.name, grade = l.grade, startType = l.startType,
                            linePath = l.stroke.toJson()
                        )
                    }
                )
                val updated = updateBlock(block.id, req)
                _state.value = _state.value.copy(saving = false, block = updated, savedOk = true)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(saving = false, error = t.toUserMessage())
            }
        }
    }
}
