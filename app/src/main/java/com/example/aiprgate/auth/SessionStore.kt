package com.example.aiprgate.auth

interface SessionStore {
    fun readToken(): String?
    fun saveToken(token: String)
    fun clearToken()
}
