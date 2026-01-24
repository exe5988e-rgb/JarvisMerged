package com.jarvismini.core.util

import com.google.gson.Gson

object JsonUtil {
    private val gson = Gson()

    fun <T> toJson(obj: T): String = gson.toJson(obj)

    inline fun <reified T> fromJson(json: String): T =
        gson.fromJson(json, T::class.java)
}
