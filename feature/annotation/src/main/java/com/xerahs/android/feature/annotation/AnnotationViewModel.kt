package com.xerahs.android.feature.annotation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import com.xerahs.android.core.common.generateId
import com.xerahs.android.core.domain.model.Annotation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class AnnotationTool {
    RECTANGLE, ARROW, TEXT, BLUR
}

data class AnnotationUiState(
    val annotations: List<Annotation> = emptyList(),
    val selectedTool: AnnotationTool = AnnotationTool.RECTANGLE,
    val strokeColor: Int = Color.Red.toArgb(),
    val strokeWidth: Float = 4f,
    val fontSize: Float = 24f,
    val blurRadius: Float = 25f,
    val undoStack: List<List<Annotation>> = emptyList(),
    val redoStack: List<List<Annotation>> = emptyList(),
    val isExporting: Boolean = false,
    val pendingTextPosition: Pair<Float, Float>? = null
)

@HiltViewModel
class AnnotationViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AnnotationUiState())
    val uiState: StateFlow<AnnotationUiState> = _uiState.asStateFlow()

    fun selectTool(tool: AnnotationTool) {
        _uiState.value = _uiState.value.copy(selectedTool = tool)
    }

    fun setStrokeColor(color: Int) {
        _uiState.value = _uiState.value.copy(strokeColor = color)
    }

    fun setStrokeWidth(width: Float) {
        _uiState.value = _uiState.value.copy(strokeWidth = width)
    }

    fun setFontSize(size: Float) {
        _uiState.value = _uiState.value.copy(fontSize = size)
    }

    fun setBlurRadius(radius: Float) {
        _uiState.value = _uiState.value.copy(blurRadius = radius)
    }

    fun addAnnotation(startX: Float, startY: Float, endX: Float, endY: Float) {
        val state = _uiState.value
        pushUndo()

        val annotation = when (state.selectedTool) {
            AnnotationTool.RECTANGLE -> Annotation.Rectangle(
                id = generateId(),
                zIndex = state.annotations.size,
                strokeColor = state.strokeColor,
                strokeWidth = state.strokeWidth,
                startX = startX, startY = startY,
                endX = endX, endY = endY
            )
            AnnotationTool.ARROW -> Annotation.Arrow(
                id = generateId(),
                zIndex = state.annotations.size,
                strokeColor = state.strokeColor,
                strokeWidth = state.strokeWidth,
                startX = startX, startY = startY,
                endX = endX, endY = endY
            )
            AnnotationTool.TEXT -> {
                // For text, we set a pending position and show dialog
                _uiState.value = state.copy(pendingTextPosition = Pair(startX, startY))
                return
            }
            AnnotationTool.BLUR -> Annotation.Blur(
                id = generateId(),
                zIndex = state.annotations.size,
                startX = startX, startY = startY,
                endX = endX, endY = endY,
                blurRadius = state.blurRadius
            )
        }

        _uiState.value = state.copy(
            annotations = state.annotations + annotation,
            redoStack = emptyList()
        )
    }

    fun addTextAnnotation(text: String) {
        val state = _uiState.value
        val position = state.pendingTextPosition ?: return
        pushUndo()

        val annotation = Annotation.Text(
            id = generateId(),
            zIndex = state.annotations.size,
            strokeColor = state.strokeColor,
            strokeWidth = state.strokeWidth,
            text = text,
            x = position.first,
            y = position.second,
            fontSize = state.fontSize
        )

        _uiState.value = state.copy(
            annotations = state.annotations + annotation,
            pendingTextPosition = null,
            redoStack = emptyList()
        )
    }

    fun dismissTextDialog() {
        _uiState.value = _uiState.value.copy(pendingTextPosition = null)
    }

    fun undo() {
        val state = _uiState.value
        if (state.undoStack.isEmpty()) return

        val previousState = state.undoStack.last()
        _uiState.value = state.copy(
            annotations = previousState,
            undoStack = state.undoStack.dropLast(1),
            redoStack = state.redoStack + listOf(state.annotations)
        )
    }

    fun redo() {
        val state = _uiState.value
        if (state.redoStack.isEmpty()) return

        val nextState = state.redoStack.last()
        _uiState.value = state.copy(
            annotations = nextState,
            undoStack = state.undoStack + listOf(state.annotations),
            redoStack = state.redoStack.dropLast(1)
        )
    }

    fun clearAnnotations() {
        pushUndo()
        _uiState.value = _uiState.value.copy(
            annotations = emptyList(),
            redoStack = emptyList()
        )
    }

    fun setExporting(exporting: Boolean) {
        _uiState.value = _uiState.value.copy(isExporting = exporting)
    }

    private fun pushUndo() {
        val state = _uiState.value
        _uiState.value = state.copy(
            undoStack = state.undoStack + listOf(state.annotations)
        )
    }
}
