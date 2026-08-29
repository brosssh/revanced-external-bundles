package me.brosssh.bundles.workers.loaders.common

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

internal fun Any.invokeNoArg(name: String): Any? =
    javaClass.getMethod(name).invokeUnwrapped(this)

internal fun Method.invokeUnwrapped(receiver: Any?, vararg arguments: Any?): Any? =
    try {
        invoke(receiver, *arguments)
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }
