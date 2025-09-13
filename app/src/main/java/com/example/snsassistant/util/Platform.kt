package com.example.snsassistant.util

enum class Platform(val display: String, val pkg: String) {
    LinkedIn("LinkedIn", "com.linkedin.android"),
    X("X", "com.twitter.android"),
    Instagram("Instagram", "com.instagram.android");

    companion object {
        fun fromPackageName(pkg: String): Platform? = values().firstOrNull { it.pkg == pkg }
    }
}

