package uk.akane.cupertino.widget.navigation

enum class FragmentSwitcherTransitionStyle {
    DEFAULT,
    CUPERTINO
}

interface FragmentSwitcherTransitionProvider {
    val fragmentSwitcherTransitionStyle: FragmentSwitcherTransitionStyle
        get() = FragmentSwitcherTransitionStyle.CUPERTINO
}
