package ru.utkonos.fragment_bottom_navigation_view

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import ru.utkonos.easy_navigation.NavigationFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bottom_navigation_view.initNavigation(
            fragmentManager = supportFragmentManager,
            containerId = R.id.container,
            onBackPressedBehaviour = FragmentBottomNavigationView.POP_CHILD_BACK_STACK or FragmentBottomNavigationView.SELECT_PREVIOUS_ITEM,
            onItemReselectBehaviour = FragmentBottomNavigationView.CLEAR_CHILD_BACK_STACK
        ) {
            when (it) {
                R.id.menu_item_home -> NavigationFragment { DestinationFragment.newInstance(R.string.tab_home) }
                R.id.menu_item_search -> NavigationFragment { DestinationFragment.newInstance(R.string.tab_search) }
                R.id.menu_item_settings -> NavigationFragment { DestinationFragment.newInstance(R.string.tab_settings) }
                else -> null
            }
        }
    }
}