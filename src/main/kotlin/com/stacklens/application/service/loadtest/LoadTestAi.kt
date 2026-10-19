package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.loadtest.LoadTestAnalysisAiPort
import com.stacklens.application.port.out.loadtest.LoadTestEnvironmentPort

interface LoadTestAi :
    LoadTestAnalysisAiPort,
    LoadTestEnvironmentPort
