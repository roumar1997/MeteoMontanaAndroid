package com.meteomontana.android.ui.screens.compare

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.ui.navigation.Destination
import com.meteomontana.android.ui.navigation.Navigator

/** Comparar escuelas dentro del overlay. */
fun NavGraphBuilder.compareRoutes(nav: Navigator) {
    composable(Routes.COMPARE) {
        CompareScreen(
            onBack = nav::back,
            onSchoolDetail = { id -> nav.navigate(Destination.SchoolDetail(id)) }
        )
    }
}
