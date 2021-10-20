package jna

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform

interface Kernel32 : Library
{
    companion object {
        val Ins: Kernel32 = Native.load(if(Platform.isWindows()) "kernel32" else "c", Kernel32::class.java)
    }

    fun SetConsoleTitleA(title: String): Boolean
}