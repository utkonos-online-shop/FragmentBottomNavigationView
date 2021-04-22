package ru.utkonos.fragment_bottom_navigation_view

import android.content.ContextWrapper
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

internal val View.activity: FragmentActivity
    get() {
        var contextWrapper = (context as ContextWrapper)
        while (contextWrapper !is FragmentActivity) {
            contextWrapper =
                contextWrapper.baseContext as ContextWrapper? ?: throw NoSuchFieldException()
        }
        return contextWrapper
    }

internal val FragmentManager.backStackIds
    get() = IntArray(backStackEntryCount) { getBackStackEntryAt(it).id }