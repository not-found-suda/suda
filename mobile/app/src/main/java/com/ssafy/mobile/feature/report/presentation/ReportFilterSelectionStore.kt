package com.ssafy.mobile.feature.report.presentation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ReportFilterSelectionStore
    @Inject
    constructor() {
        private val _input = MutableStateFlow(defaultReportFilterInputState())
        val input: StateFlow<ReportFilterInputState> = _input.asStateFlow()

        fun currentInput(): ReportFilterInputState = input.value

        fun update(input: ReportFilterInputState) {
            _input.value = input
        }

        fun reset() {
            _input.value = defaultReportFilterInputState()
        }
    }
