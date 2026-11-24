package com.meteomontana.android.ui.screens.submissions

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.ui.navigation.Navigator

/** Mis propuestas y «enviar escuela» dentro del overlay. */
fun NavGraphBuilder.submissionRoutes(nav: Navigator) {
    composable(Routes.MY_SUBMISSIONS) { MySubmissionsScreen(onBack = nav::back) }
    composable(Routes.SUBMIT_SCHOOL) { SubmitSchoolScreen(onBack = nav::back) }
}
