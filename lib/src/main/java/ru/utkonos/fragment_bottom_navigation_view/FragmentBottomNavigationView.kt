package ru.utkonos.fragment_bottom_navigation_view

import android.content.Context
import android.util.AttributeSet
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.core.view.forEach
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.ArrayDeque
import kotlin.math.abs

sealed class CompleteBackStackEntry {
    abstract val tabId: Int
}

data class FragmentBackStackEntry(val id: Int, override val tabId: Int) : CompleteBackStackEntry()

data class TabBackStackEntry(override val tabId: Int) : CompleteBackStackEntry()

class FragmentBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BottomNavigationView(context, attrs, defStyle) {

    private lateinit var fragmentManager: FragmentManager

    private var addSelectedTabsToBackStack = false

    private var clearTabStackOnReselect = false

    private var outerOnNavigationItemSelectedListener: OnNavigationItemSelectedListener? = null

    private var outerOnNavigationItemReselectedListener: OnNavigationItemReselectedListener? = null

    val selectedFragment: Fragment? get() = fragmentManager.primaryNavigationFragment

    private val completeBackStack = ArrayDeque<CompleteBackStackEntry>()

    private val onBackPressedCallback by lazy {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() =
                this@FragmentBottomNavigationView.handleOnBackPressed()
        }
    }

    init {
        if (id == NO_ID) id = R.id.default_id
        super.setOnNavigationItemSelectedListener(OnNavigationItemSelectedListener(::onNavigationItemSelected))
        super.setOnNavigationItemReselectedListener(OnNavigationItemReselectedListener(::onNavigationItemReselected))
    }

    private fun getTabFragmentTag(tabId: Int) = "TabFragment$tabId"

    fun initNavigation(
        fragmentManager: FragmentManager,
        containerId: Int = 0,
        addSelectedItemsToBackStack: Boolean = false,
        resetFragmentBackStackOnItemReselect: Boolean = false,
        tabFragmentFactory: (menuItemId: Int) -> Fragment?
    ) {
        this.fragmentManager = fragmentManager
        this.addSelectedTabsToBackStack = addSelectedItemsToBackStack
        this.clearTabStackOnReselect = resetFragmentBackStackOnItemReselect
        menu.forEach {
            val tabId = it.itemId
            val fragmentTag = getTabFragmentTag(tabId)
            val tabFragment =
                fragmentManager.findFragmentByTag(fragmentTag)?.also { tabFragment ->
                    if (tabId == selectedItemId) {
                        if (fragmentManager.primaryNavigationFragment !== tabFragment)
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
                        if (tabId == selectedItemId)
                            setPrimaryNavigationFragment(tabFragment)
                        else
                            detach(tabFragment)
                        commitNowAllowingStateLoss()
                    }
                }
            if (addSelectedItemsToBackStack)
                tabFragment?.initOnBackStackChangedListener(tabId)
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
                setPrimaryNavigationFragment(it)
                attach(it)
            }
            if (addSelectedTabsToBackStack)
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
        if (clearTabStackOnReselect) {
            selectedFragment?.childFragmentManager?.apply {
                repeat(backStackEntryCount) { if (!isStateSaved) popBackStack() }
            }
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

    companion object {

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