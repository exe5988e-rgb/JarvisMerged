package com.jarvismini.core

fun String.trunc(n: Int): String =
    if (this.length > n) this.substring(0, n) else this
