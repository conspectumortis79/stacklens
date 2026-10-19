package com.stacklens.adapter.out.analysis

import com.stacklens.application.port.out.analysis.GithubRepoContentPort
import com.stacklens.application.port.out.analysis.GitlabConfigLookupPort
import com.stacklens.application.port.out.analysis.GitlabRepoContentPort
import com.stacklens.application.port.out.analysis.LocalRepoContentPort

interface CodeAnalysisRepoApi :
    LocalRepoContentPort,
    GitlabRepoContentPort,
    GithubRepoContentPort,
    GitlabConfigLookupPort
