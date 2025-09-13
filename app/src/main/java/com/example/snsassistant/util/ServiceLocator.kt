package com.example.snsassistant.util

import com.example.snsassistant.data.repo.SnsRepository

object ServiceLocator {
    lateinit var repository: SnsRepository
        private set

    fun init(repo: SnsRepository) {
        repository = repo
    }
}

