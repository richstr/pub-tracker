package com.richardstrang.pubtracker.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.richardstrang.pubtracker.ui.challenge.ChallengeScreen
import com.richardstrang.pubtracker.ui.checkin.CheckInScreen
import com.richardstrang.pubtracker.ui.editpub.EditPubScreen
import com.richardstrang.pubtracker.ui.home.HomeScreen
import com.richardstrang.pubtracker.ui.newchallenge.NewChallengeScreen
import com.richardstrang.pubtracker.ui.pub.PubDetailScreen
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object NewChallengeRoute

@Serializable
data class ChallengeRoute(val challengeId: String)

@Serializable
data class PubRoute(val pubId: String, val challengeId: String? = null)

@Serializable
data class CheckInRoute(val pubId: String, val visitId: String? = null)

@Serializable
data class EditPubRoute(val pubId: String? = null, val challengeId: String? = null)

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.navigateUp() }

    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onNewChallenge = { nav.navigate(NewChallengeRoute) },
                onOpenChallenge = { nav.navigate(ChallengeRoute(it)) },
            )
        }
        composable<NewChallengeRoute> {
            NewChallengeScreen(
                onBack = back,
                onCreated = { id ->
                    nav.navigate(ChallengeRoute(id)) {
                        popUpTo<NewChallengeRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<ChallengeRoute> { entry ->
            val route = entry.toRoute<ChallengeRoute>()
            ChallengeScreen(
                challengeId = route.challengeId,
                onBack = back,
                onOpenPub = { nav.navigate(PubRoute(it, route.challengeId)) },
                onCheckIn = { nav.navigate(CheckInRoute(it)) },
                onAddPub = { nav.navigate(EditPubRoute(challengeId = route.challengeId)) },
            )
        }
        composable<PubRoute> { entry ->
            val route = entry.toRoute<PubRoute>()
            PubDetailScreen(
                pubId = route.pubId,
                challengeId = route.challengeId,
                onBack = back,
                onCheckIn = { nav.navigate(CheckInRoute(route.pubId)) },
                onEditVisit = { nav.navigate(CheckInRoute(route.pubId, it)) },
                onEditPub = { nav.navigate(EditPubRoute(route.pubId, route.challengeId)) },
            )
        }
        composable<CheckInRoute> { entry ->
            val route = entry.toRoute<CheckInRoute>()
            CheckInScreen(pubId = route.pubId, visitId = route.visitId, onDone = back)
        }
        composable<EditPubRoute> { entry ->
            val route = entry.toRoute<EditPubRoute>()
            EditPubScreen(pubId = route.pubId, challengeId = route.challengeId, onDone = back)
        }
    }
}
