package com.example.models

sealed class ApiKeyValidationState {
    data object Idle : ApiKeyValidationState()
    data object Validating : ApiKeyValidationState()
    data class Success(val message: String) : ApiKeyValidationState()
    data class Error(val message: String) : ApiKeyValidationState()
}
