package ru.utkonos.fragment_bottom_navigation_view

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IntDef
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

private const val TAB_FRAGMENT_TAG_PREFIX = "TabFragment"
private const val TAB_FRAGMENT_TAG_PATTERN = "$TAB_FRAGMENT_TAG_PREFIX.*"

class FragmentBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BottomNavigationView(context, attrs, defStyle) {

    private lateinit var fragmentManager: FragmentManager

    private var outerOnNavigationItemSelectedListener: OnNavigationItemSelectedListener? = null

    private var outerOnNavigationItemReselectedListener: OnNavigationItemReselectedListener? = null

    val selectedFragment: Fragment?
        get() = fragmentManager.findFragmentByTag(getTabFragmentTag(selectedItemId))

    private val completeBackStack by lazy { ArrayDeque<CompleteBackStackEntry>() }

    private var completeBackStackEnabled = false

    private var addFragmentDestinationsToCompleteBackStack = false

    private val onBackPressedCallback by lazy {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() =
                this@FragmentBottomNavigationView.handleOnBackPressed()
        }
    }

    private var onBackStackChangedListeners: MutableList<FragmentManager.OnBackStackChangedListener> =
        mutableListOf()

    val canPerformOnBackPressed
        get() = (canSetPrimaryNavigationFragment && selectedFragment?.childFragmentManager?.backStackEntryCount?.let { it > 0 } == true)
            || (completeBackStackEnabled && completeBackStack.isNotEmpty())

    private var canSetPrimaryNavigationFragment = true

    private var clearChildBackStack = false

    init {
        if (id == NO_ID) id = R.id.default_id
        super.setOnNavigationItemSelectedListener(OnNavigationItemSelectedListener(::onNavigationItemSelected))
        super.setOnNavigationItemReselectedListener(OnNavigationItemReselectedListener(::onNavigationItemReselected))
    }

    private fun getTabFragmentTag(tabId: Int) =
        TAB_FRAGMENT_TAG_PATTERN.replace(".*", tabId.toString())

    private fun getTabIdFromTabFragment(fragment: Fragment) =
        fragment.tag?.substringAfter(TAB_FRAGMENT_TAG_PREFIX)?.toInt()

    fun initNavigation(
        fragmentManager: FragmentManager,
        containerId: Int = 0,
        @OnBackPressedBehaviour
        onBackPressedBehaviour: Int = POP_BACK_STACK_OF_SELECTED_FRAGMENT,
        @OnItemReselectBehaviour
        onItemReselectBehaviour: Int = NONE,
        tabFragmentFactory: (menuItemId: Int) -> Fragment?
    ) {
        this.fragmentManager = fragmentManager
        canSetPrimaryNavigationFragment =
            onBackPressedBehaviour and POP_BACK_STACK_OF_SELECTED_FRAGMENT == POP_BACK_STACK_OF_SELECTED_FRAGMENT
        completeBackStackEnabled =
            onBackPressedBehaviour and SELECT_PREVIOUS_ITEM == SELECT_PREVIOUS_ITEM
        addFragmentDestinationsToCompleteBackStack =
            onBackPressedBehaviour == POP_BACK_STACK_OF_SELECTED_FRAGMENT or SELECT_PREVIOUS_ITEM
        clearChildBackStack =
            onItemReselectBehaviour and CLEAR_BACK_STACK_OF_SELECTED_FRAGMENT == CLEAR_BACK_STACK_OF_SELECTED_FRAGMENT

        val restoredSelectedItemId = fragmentManager.fragments
            .lastOrNull { it.tag?.matches(TAB_FRAGMENT_TAG_PATTERN.toRegex()) == true }
            ?.let(::getTabIdFromTabFragment)
            ?: selectedItemId

        clearFragmentBackStackListeners()
        menu.forEach {
            val tabId = it.itemId
            val fragmentTag = getTabFragmentTag(tabId)
            val tabFragment =
                fragmentManager.findFragmentByTag(fragmentTag)?.also { tabFragment ->
                    if (tabId == restoredSelectedItemId) {
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
                        if (tabId == restoredSelectedItemId) {
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
                if (addFragmentDestinationsToCompleteBackStack) {
                    tabFragment?.initOnBackStackChangedListener(tabId)
                }
            }
        }
        fragmentManager.executePendingTransactions()
        updatePrimaryNavigationFragment(true)
    }

    private fun Fragment.initOnBackStackChangedListener(tabId: Int) {
        var lastTabBackStackIds = childFragmentManager.backStackIds

        val onBackStackChangedListener = FragmentManager.OnBackStackChangedListener {
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

        childFragmentManager.addOnBackStackChangedListener(onBackStackChangedListener)

        onBackStackChangedListeners.add(onBackStackChangedListener)
    }

    private fun getMenuFragmentsListWithTabId(): List<Pair<Fragment, Int>> {
        val fragmentList = mutableListOf<Pair<Fragment, Int>>()
        menu.forEach { menuItem ->
            val tabId = menuItem.itemId
            val fragmentTag = getTabFragmentTag(tabId)
            fragmentManager.findFragmentByTag(fragmentTag)?.let {
                fragmentList.add(it to tabId)
            }
        }

        return fragmentList
    }

    private fun restoreFragmentBackStackListeners() {
        clearFragmentBackStackListeners()
        getMenuFragmentsListWithTabId().forEach {
            val fragment = it.first
            val tabId = it.second
            if (completeBackStackEnabled) {
                if (addFragmentDestinationsToCompleteBackStack) {
                    fragment.initOnBackStackChangedListener(tabId)
                }
            }
        }
    }

    private fun clearFragmentBackStackListeners() {
        getMenuFragmentsListWithTabId().map { it.first }
            .forEach { fragment ->
                onBackStackChangedListeners.forEach {
                    fragment.childFragmentManager.removeOnBackStackChangedListener(it)
                }
            }

        onBackStackChangedListeners = mutableListOf()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restoreFragmentBackStackListeners()
        updateOnBackPressedCallback()
    }

    override fun onDetachedFromWindow() {
        clearFragmentBackStackListeners()
        clearOnBackPressedCallback()
        super.onDetachedFromWindow()
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
                if (canSetPrimaryNavigationFragment) setPrimaryNavigationFragment(it)
                attach(it)
            }
            runOnCommit {
                updatePrimaryNavigationFragment(false)
                if (completeBackStackEnabled) {
                    if ((completeBackStack.peekLast() as? TabBackStackEntry)?.tabId == newTabId)
                        completeBackStack.pollLast()
                    else
                        completeBackStack.add(TabBackStackEntry(oldTabId))
                    updateOnBackPressedCallback()
                }
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

    private fun updatePrimaryNavigationFragment(now: Boolean) {
        if (!canSetPrimaryNavigationFragment)
            with(fragmentManager.beginTransaction()) {
                setPrimaryNavigationFragment(null)
                if (now) commitNowAllowingStateLoss() else commitAllowingStateLoss()
            }
    }

    private fun updateOnBackPressedCallback() {
        clearOnBackPressedCallback()
        if (completeBackStack.peekLast() is TabBackStackEntry) {
            activity.onBackPressedDispatcher.addCallback(onBackPressedCallback)
        }
    }

    private fun clearOnBackPressedCallback() {
        onBackPressedCallback.remove()
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

    @IntDef(NONE, POP_BACK_STACK_OF_SELECTED_FRAGMENT, SELECT_PREVIOUS_ITEM, flag = true)
    annotation class OnBackPressedBehaviour

    @IntDef(NONE, CLEAR_BACK_STACK_OF_SELECTED_FRAGMENT)
    annotation class OnItemReselectBehaviour

    @Parcelize
    private class SavedState(
        val superState: Parcelable?,
        val completeBackStack: @RawValue Array<CompleteBackStackEntry>?
    ) : Parcelable

    companion object {

        const val NONE = 1
        const val POP_BACK_STACK_OF_SELECTED_FRAGMENT = 1 shl 1
        const val SELECT_PREVIOUS_ITEM = 1 shl 2

        const val CLEAR_BACK_STACK_OF_SELECTED_FRAGMENT = 2

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
        fun setOnNavigationItemReselectedListener(
            view: FragmentBottomNavigationView,
            value: OnNavigationItemReselectedListener?
        ) {
            view.setOnNavigationItemReselectedListener(value)
        }
    }
}