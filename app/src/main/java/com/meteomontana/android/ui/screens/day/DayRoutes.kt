package com.meteomontana.android.ui.screens.day

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.ui.navigation.Navigator

/** Detalle de un día de previsión (de una escuela o de la ubicación) dentro del overlay. */
fun NavGraphBuilder.dayRoutes(nav: Navigator) {
    composable(
        route = Routes.DAY_DETAIL,
        arguments = listOf(
            navArgument("schoolId") { type = NavType.StringType },
            navArgument("dayIndex") { type = NavType.StringType }
        )
    ) { DayDetailScreen(onBack = nav::back) }
    composable(
        route = Routes.DAY_DETAIL_BY_LOCATION,
        arguments = listOf(
            navArgument("lat") { type = NavType.StringType },
            navArgument("lon") { type = NavType.StringType },
            navArgument("dayIndex") { type = NavType.StringType }
        )
    ) { DayDetailScreen(onBack = nav::back) }
}
