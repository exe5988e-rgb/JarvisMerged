package com.jarvismini.core.util

import com.google.gson.Gson

object JsonUtil {
    val gson = Gson() // Made public to fix inline reified access error

    fun <T> toJson(obj: T): String = gson.toJson(obj)

    inline fun <reified T> fromJson(json: String): T =
        gson.fromJson(json, T::class.java)
}
