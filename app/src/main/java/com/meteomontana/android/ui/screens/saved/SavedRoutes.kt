package com.meteomontana.android.ui.screens.saved

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.ui.navigation.Destination
import com.meteomontana.android.ui.navigation.Navigator

/** Escuelas guardadas dentro del overlay. */
fun NavGraphBuilder.savedRoutes(nav: Navigator) {
    composable(Routes.SAVED_SCHOOLS) {
        SavedSchoolsScreen(
            onBack = nav::back,
            onOpen = { id -> nav.navigate(Destination.SchoolDetail(id)) }
        )
    }
}
