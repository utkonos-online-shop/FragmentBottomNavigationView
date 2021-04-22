package ru.utkonos.fragment_bottom_navigation_view

import android.os.Bundle
import android.text.Html
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_destination.*
import ru.utkonos.easy_navigation.navigate

class DestinationFragment : Fragment(R.layout.fragment_destination) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tabNameResId = arguments!!.getInt(KEY_tabNameResId)

        title.text = Html.fromHtml(
            getString(
                R.string.destination_fragment_title,
                parentFragment!!.childFragmentManager.backStackEntryCount + 1,
                getString(tabNameResId)
            )
        )

        button_forward.setOnClickListener {
            navigate().to(newInstance(tabNameResId))
        }

        button_back.setOnClickListener {
            navigate().back()
        }
    }

    companion object {

        private const val KEY_tabNameResId = "tabName"

        fun newInstance(tabNameResId: Int) =
            DestinationFragment().apply { arguments = bundleOf(KEY_tabNameResId to tabNameResId) }
    }
}