package com.papsign.ktor.openapigen.modules.openapi

import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.modules.ModuleProvider
import com.papsign.ktor.openapigen.modules.OpenAPIModule
import com.papsign.ktor.openapigen.openapi.Operation

interface OperationModule: OpenAPIModule {
    fun configure(apiGen: OpenAPIGen, provider: ModuleProvider<*>, operation: Operation)
}