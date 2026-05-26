package com.ssafy.mobile.feature.report.presentation

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ReportFilterSelectionStore
    @Inject
    constructor() {
        private val anchorDateState = MutableStateFlow(defaultReportFilterAnchorDate())
        private val _input = MutableStateFlow(defaultReportFilterInputState(anchorDateState.value))
        val input: StateFlow<ReportFilterInputState> = _input.asStateFlow()

        fun currentInput(): ReportFilterInputState = input.value

        fun currentAnchorDate(): LocalDate = anchorDateState.value

        fun update(input: ReportFilterInputState) {
            _input.value = input
        }

        fun updateAnchorDate(anchorDate: LocalDate) {
            anchorDateState.value = anchorDate
        }

        fun reset() {
            _input.value = defaultReportFilterInputState(anchorDateState.value)
        }
    }
