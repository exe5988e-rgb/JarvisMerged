package com.jarvismini.engine

import java.net.InetSocketAddress
import java.net.Socket

object NetworkUtil {
    fun hasInternet(): Boolean {
        return try {
            Socket().use {
                it.connect(InetSocketAddress("8.8.8.8", 53), 1000)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}