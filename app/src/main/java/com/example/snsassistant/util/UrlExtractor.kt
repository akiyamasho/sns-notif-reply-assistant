package com.example.snsassistant.util

object UrlExtractor {
    private val regex = Regex("(https?://[\\w./?=&%#-]+)")
    fun firstUrlFrom(text: String?): String? = text?.let { regex.find(it)?.value }
}

