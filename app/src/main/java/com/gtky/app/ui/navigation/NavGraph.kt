package com.gtky.app.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.gtky.app.GTKYApplication
import com.gtky.app.ui.screens.*
import com.gtky.app.viewmodel.*
import com.gtky.app.viewmodel.GroupsViewModel
import com.gtky.app.viewmodel.ProfileViewModel

object Routes {
    const val HOME = "home"
    const val PICK_USER = "pick_user"
    const val SURVEY = "survey/{userId}"
    const val QUIZ = "quiz/{userId}/{groupIds}?subjectIds={subjectIds}"
    const val CONNECTIONS = "connections"
    const val ACTIVE_USERS = "active_users"
    const val GROUPS = "groups"
    const val ADMIN = "admin"
    const val PROFILE = "profile/{userId}"

    fun survey(userId: Long) = "survey/$userId"
    fun quiz(userId: Long, groupIds: String, subjectIds: String = "") =
        if (subjectIds.isBlank()) "quiz/$userId/$groupIds" else "quiz/$userId/$groupIds?subjectIds=$subjectIds"
    fun profile(userId: Long) = "profile/$userId"
}

@Composable
fun GTKYNavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val repo = (context.applicationContext as GTKYApplication).repository

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(repo))
            HomeScreen(
                viewModel = vm,
                onStartSurvey = { userId -> navController.navigate(Routes.survey(userId)) },
                onGoToQuiz = { userId, groupIds, subjectIds ->
                    navController.navigate(Routes.quiz(userId, groupIds, subjectIds))
                },
                onGoToConnections = { navController.navigate(Routes.CONNECTIONS) },
                onGoToActiveUsers = { navController.navigate(Routes.ACTIVE_USERS) },
                onGoToGroups = { navController.navigate(Routes.GROUPS) },
                onGoToAdmin = { navController.navigate(Routes.ADMIN) },
                onPickUser = { navController.navigate(Routes.PICK_USER) }
            )
        }

        composable(Routes.PICK_USER) {
            val vm: HomeViewModel = viewModel(
                viewModelStoreOwner = navController.getBackStackEntry(Routes.HOME),
                factory = HomeViewModel.Factory(repo)
            )
            val allUsers by vm.allUsers.collectAsState()
            PickUserScreen(
                users = allUsers,
                onUserSelected = { user ->
                    vm.selectExistingUser(user)
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.SURVEY,
            arguments = listOf(navArgument("userId") { type = NavType.LongType })
        ) { backStack ->
            val userId = backStack.arguments!!.getLong("userId")
            val homeVm: HomeViewModel = viewModel(
                viewModelStoreOwner = navController.getBackStackEntry(Routes.HOME),
                factory = HomeViewModel.Factory(repo)
            )
            val vm: SurveyViewModel = viewModel(factory = SurveyViewModel.Factory(repo, userId))
            SurveyScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onGoToQuiz = {
                    homeVm.requestOpenQuizDialog()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

        composable(
            Routes.QUIZ,
            arguments = listOf(
                navArgument("userId") { type = NavType.LongType },
                navArgument("groupIds") { type = NavType.StringType },
                navArgument("subjectIds") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStack ->
            val userId = backStack.arguments!!.getLong("userId")
            val groupIds = backStack.arguments!!.getString("groupIds") ?: "0"
            val groupIdList = groupIds.split(",").mapNotNull { it.toLongOrNull() }
            val subjectIdsStr = backStack.arguments!!.getString("subjectIds") ?: ""
            val subjectIdList = if (subjectIdsStr.isBlank()) emptyList()
                else subjectIdsStr.split(",").mapNotNull { it.toLongOrNull() }
            val homeVm: HomeViewModel = viewModel(
                viewModelStoreOwner = navController.getBackStackEntry(Routes.HOME),
                factory = HomeViewModel.Factory(repo)
            )
            val vm: QuizViewModel = viewModel(factory = QuizViewModel.Factory(repo, userId, groupIdList, subjectIdList))
            QuizScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onGoToSurvey = {
                    navController.navigate(Routes.survey(userId)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onQuizAboutSubject = { subjectId ->
                    homeVm.requestQuizWithSubject(subjectId)
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onGoToProfile = { userId -> navController.navigate(Routes.profile(userId)) }
            )
        }

        composable(Routes.CONNECTIONS) {
            val homeVm: HomeViewModel = viewModel(
                viewModelStoreOwner = navController.getBackStackEntry(Routes.HOME),
                factory = HomeViewModel.Factory(repo)
            )
            val vm: ConnectionsViewModel = viewModel(factory = ConnectionsViewModel.Factory(repo))
            ConnectionsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onGoToQuiz = {
                    homeVm.requestOpenQuizDialog()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onGoToProfile = { userId -> navController.navigate(Routes.profile(userId)) }
            )
        }

        composable(
            Routes.PROFILE,
            arguments = listOf(navArgument("userId") { type = NavType.LongType })
        ) { backStack ->
            val userId = backStack.arguments!!.getLong("userId")
            val app = context.applicationContext as GTKYApplication
            val language by app.language.collectAsState()
            val vm: ProfileViewModel = viewModel(
                key = "profile-$userId-$language",
                factory = ProfileViewModel.Factory(repo, userId, language)
            )
            ProfileScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.ACTIVE_USERS) {
            val homeVm: HomeViewModel = viewModel(
                viewModelStoreOwner = navController.getBackStackEntry(Routes.HOME),
                factory = HomeViewModel.Factory(repo)
            )
            val vm: ActiveUsersViewModel = viewModel(factory = ActiveUsersViewModel.Factory(repo))
            ActiveUsersScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onGoToGroups = { navController.navigate(Routes.GROUPS) },
                onStartSubjectQuiz = { subjectUserId ->
                    homeVm.requestQuizWithSubject(subjectUserId)
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

        composable(Routes.GROUPS) {
            val vm: GroupsViewModel = viewModel(factory = GroupsViewModel.Factory(repo))
            GroupsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.ADMIN) {
            val vm: AdminViewModel = viewModel(factory = AdminViewModel.Factory(repo))
            AdminScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
