package ru.utkonos.fragment_bottom_navigation_view

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.core.view.forEach
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomnavigation.BottomNavigationView.OnNavigationItemReselectedListener
import com.google.android.material.bottomnavigation.BottomNavigationView.OnNavigationItemSelectedListener
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.RawValue
import java.util.*
import kotlin.math.abs

sealed class CompleteBackStackEntry {
    abstract val tabId: Int
}

@Parcelize
data class FragmentBackStackEntry(val id: Int, override val tabId: Int) : CompleteBackStackEntry(),
    Parcelable

@Parcelize
data class TabBackStackEntry(override val tabId: Int) : CompleteBackStackEntry(), Parcelable

class FragmentBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BottomNavigationView(context, attrs, defStyle) {

    private lateinit var fragmentManager: FragmentManager

    private var outerOnNavigationItemSelectedListener: OnNavigationItemSelectedListener? = null

    private var outerOnNavigationItemReselectedListener: OnNavigationItemReselectedListener? = null

    val selectedFragment: Fragment? get() = fragmentManager.primaryNavigationFragment

    private val completeBackStack by lazy { ArrayDeque<CompleteBackStackEntry>() }

    private var completeBackStackEnabled = false

    private var addFragmentDestinationsToCompleteBackStack = false

    private val onBackPressedCallback by lazy {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() =
                this@FragmentBottomNavigationView.handleOnBackPressed()
        }
    }

    private var canSetPrimaryNavigationFragment = true

    private var clearChildBackStack = false

    init {
        if (id == NO_ID) id = R.id.default_id
        super.setOnNavigationItemSelectedListener(OnNavigationItemSelectedListener(::onNavigationItemSelected))
        super.setOnNavigationItemReselectedListener(OnNavigationItemReselectedListener(::onNavigationItemReselected))
    }

    private fun getTabFragmentTag(tabId: Int) = "TabFragment$tabId"

    fun initNavigation(
        fragmentManager: FragmentManager,
        containerId: Int = 0,
        onBackPressedBehaviour: Int = POP_CHILD_BACK_STACK,
        onItemReselectBehaviour: Int = NONE,
        tabFragmentFactory: (menuItemId: Int) -> Fragment?
    ) {
        this.fragmentManager = fragmentManager
        canSetPrimaryNavigationFragment =
            onBackPressedBehaviour and POP_CHILD_BACK_STACK == POP_CHILD_BACK_STACK
        completeBackStackEnabled =
            onBackPressedBehaviour and SELECT_PREVIOUS_ITEM == SELECT_PREVIOUS_ITEM
        addFragmentDestinationsToCompleteBackStack =
            onBackPressedBehaviour == POP_CHILD_BACK_STACK or SELECT_PREVIOUS_ITEM
        clearChildBackStack =
            onItemReselectBehaviour and CLEAR_CHILD_BACK_STACK == CLEAR_CHILD_BACK_STACK

        menu.forEach {
            val tabId = it.itemId
            val fragmentTag = getTabFragmentTag(tabId)
            val tabFragment =
                fragmentManager.findFragmentByTag(fragmentTag)?.also { tabFragment ->
                    if (tabId == selectedItemId) {
                        if (canSetPrimaryNavigationFragment && fragmentManager.primaryNavigationFragment !== tabFragment)
                            fragmentManager.beginTransaction()
                                .setPrimaryNavigationFragment(tabFragment)
                                .commitNowAllowingStateLoss()
                    } else if (tabFragment.isAdded) {
                        fragmentManager.beginTransaction()
                            .detach(tabFragment)
                            .commitNowAllowingStateLoss()
                    }
                } ?: tabFragmentFactory(tabId)?.also { tabFragment ->
                    with(fragmentManager.beginTransaction()) {
                        add(containerId, tabFragment, fragmentTag)
                        if (tabId == selectedItemId) {
                            if (canSetPrimaryNavigationFragment)
                                setPrimaryNavigationFragment(tabFragment)
                        } else {
                            detach(tabFragment)
                        }
                        commitNowAllowingStateLoss()
                    }
                }
            if (completeBackStackEnabled) {
                completeBackStack.clear()
                if (addFragmentDestinationsToCompleteBackStack)
                    tabFragment?.initOnBackStackChangedListener(tabId)
            }
        }
    }

    private fun Fragment.initOnBackStackChangedListener(tabId: Int) {
        var lastTabBackStackIds = childFragmentManager.backStackIds
        childFragmentManager.addOnBackStackChangedListener {
            val newTabBackStackIds = childFragmentManager.backStackIds
            val sizeDelta = newTabBackStackIds.size - lastTabBackStackIds.size
            if (sizeDelta > 0)
                newTabBackStackIds.takeLast(sizeDelta).forEach {
                    completeBackStack.add(FragmentBackStackEntry(it, tabId))
                }
            else if (sizeDelta < 0)
                lastTabBackStackIds.takeLast(abs(sizeDelta)).forEach {
                    completeBackStack.removeLastOccurrence(FragmentBackStackEntry(it, tabId))
                }
            lastTabBackStackIds = newTabBackStackIds
            updateOnBackPressedCallback()
        }
    }

    override fun setOnNavigationItemSelectedListener(listener: OnNavigationItemSelectedListener?) {
        outerOnNavigationItemSelectedListener = listener
    }

    override fun setOnNavigationItemReselectedListener(listener: OnNavigationItemReselectedListener?) {
        outerOnNavigationItemReselectedListener = listener
    }

    private fun onNavigationItemSelected(item: MenuItem): Boolean {
        val oldTabId = selectedItemId
        val newTabId = item.itemId
        if (outerOnNavigationItemSelectedListener?.onNavigationItemSelected(item) == false) return false
        with(fragmentManager.beginTransaction()) {
            selectedFragment?.let { detach(it) }
            fragmentManager.findFragmentByTag(getTabFragmentTag(newTabId))?.let {
                if (canSetPrimaryNavigationFragment)
                    setPrimaryNavigationFragment(it)
                attach(it)
            }
            if (completeBackStackEnabled)
                runOnCommit {
                    if ((completeBackStack.peekLast() as? TabBackStackEntry)?.tabId == newTabId)
                        completeBackStack.pollLast()
                    else
                        completeBackStack.add(TabBackStackEntry(oldTabId))
                    updateOnBackPressedCallback()
                }
            commitAllowingStateLoss()
        }
        return true
    }

    private fun onNavigationItemReselected(item: MenuItem) {
        if (clearChildBackStack)
            selectedFragment?.childFragmentManager?.apply {
                repeat(backStackEntryCount) { if (!isStateSaved) popBackStack() }
            }
        outerOnNavigationItemReselectedListener?.onNavigationItemReselected(item)
    }

    private fun updateOnBackPressedCallback() {
        onBackPressedCallback.remove()
        if (completeBackStack.peekLast() is TabBackStackEntry)
            activity.onBackPressedDispatcher.addCallback(onBackPressedCallback)
    }

    private fun handleOnBackPressed() {
        val lastBackStackEntry = completeBackStack.peekLast()
        if (lastBackStackEntry is TabBackStackEntry) {
            selectedItemId = lastBackStackEntry.tabId
        } else {
            updateOnBackPressedCallback()
            activity.onBackPressed()
        }
    }

    override fun onSaveInstanceState(): Parcelable = SavedState(
        super.onSaveInstanceState(),
        if (completeBackStackEnabled) completeBackStack.toTypedArray() else null
    )

    override fun onRestoreInstanceState(state: Parcelable?) {
        (state as? SavedState)?.let { savedState ->
            super.onRestoreInstanceState(savedState.superState)
            if (completeBackStackEnabled) {
                completeBackStack.clear()
                completeBackStack.addAll(savedState.completeBackStack ?: emptyArray())
            }
        } ?: super.onRestoreInstanceState(null)
    }

    @Parcelize
    private class SavedState(
        val superState: Parcelable?,
        val completeBackStack: @RawValue Array<CompleteBackStackEntry>?
    ) : Parcelable

    companion object {

        const val NONE = 1
        const val POP_CHILD_BACK_STACK = 2
        const val SELECT_PREVIOUS_ITEM = 4

        const val CLEAR_CHILD_BACK_STACK = 2

        @JvmStatic
        @BindingAdapter("onNavigationItemSelectedListener")
        fun setOnNavigationItemSelectedListener(
            view: FragmentBottomNavigationView,
            value: OnNavigationItemSelectedListener?
        ) {
            view.setOnNavigationItemSelectedListener(value)
        }

        @JvmStatic
        @BindingAdapter("onNavigationItemReselectedListener")
        fun setOnNavigationItemReelectedListener(
            view: FragmentBottomNavigationView,
            value: OnNavigationItemReselectedListener?
        ) {
            view.setOnNavigationItemReselectedListener(value)
        }
    }
}